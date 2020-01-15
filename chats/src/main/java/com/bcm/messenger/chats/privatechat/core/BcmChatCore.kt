/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package com.bcm.messenger.chats.privatechat.core

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.bcmhttp.exception.VersionTooLowException
import com.bcm.messenger.common.core.AddressUtil
import com.bcm.messenger.common.crypto.SecurityEvent
import com.bcm.messenger.common.crypto.storage.SignalProtocolStoreImpl
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import com.orhanobut.logger.Logger
import org.whispersystems.libsignal.InvalidKeyException
import org.whispersystems.libsignal.SessionBuilder
import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.libsignal.logging.Log
import org.whispersystems.libsignal.util.guava.Optional
import org.whispersystems.signalservice.api.crypto.AttachmentCipherOutputStream
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException
import org.whispersystems.signalservice.api.messages.*
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage
import org.whispersystems.signalservice.api.messages.multidevice.*
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions
import org.whispersystems.signalservice.api.push.exceptions.NetworkFailureException
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream
import org.whispersystems.signalservice.internal.push.*
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.*
import org.whispersystems.signalservice.internal.push.exceptions.MismatchedDevicesException
import org.whispersystems.signalservice.internal.push.exceptions.StaleDevicesException
import org.whispersystems.signalservice.internal.push.http.AttachmentCipherOutputStreamFactory
import org.whispersystems.signalservice.internal.util.Base64
import org.whispersystems.signalservice.internal.util.Util
import java.io.IOException
import java.security.SecureRandom
import java.util.*

/**
 * The main interface for sending Signal Service messages.
 *
 * @author Moxie Marlinspike
 */
object BcmChatCore {
    private val TAG = "AmeChatCore"
    private val storeMap = mutableMapOf<AccountContext, SignalProtocolStoreImpl>()

    private fun getStore(accountContext: AccountContext): SignalProtocolStoreImpl {
        var store = storeMap[accountContext]
        if (store == null) {
            store = SignalProtocolStoreImpl(AppContextHolder.APP_CONTEXT, accountContext)
            storeMap[accountContext] = store
        }
        return store
    }

    /**
     * Send a read receipt for a received message.
     *
     * @param recipient The sender of the received message you're acknowledging.
     * @param message   The read receipt to deliver.
     * @throws IOException
     * @throws UntrustedIdentityException
     */

    @Throws(IOException::class, UntrustedIdentityException::class)
    fun sendReceipt(accountContext: AccountContext, recipient: SignalServiceAddress, message: SignalServiceReceiptMessage) {
        val content = createReceiptContent(message)
        sendMessage(accountContext, recipient, message.getWhen(), content, PushPurpose.SILENT)
    }

    /**
     * Send a call setup message to a single recipient.
     *
     * @param recipient The message's destination.
     * @param message   The call message.
     * @throws IOException
     */

    @Throws(IOException::class, UntrustedIdentityException::class)
    fun sendCallMessage(accountContext: AccountContext, recipient: SignalServiceAddress, message: SignalServiceCallMessage) {
        val content = createCallContent(message)
        sendMessage(accountContext, recipient, AmeTimeUtil.getMessageSendTime(), content, PushPurpose.CALLING)
    }

    /**
     * Send a message to a single recipient.
     *
     * @param recipient The message's destination.
     * @param message   The message.
     * @throws UntrustedIdentityException
     * @throws IOException
     */

    @Throws(UntrustedIdentityException::class, IOException::class, VersionTooLowException::class)
    fun sendMessage(accountContext: AccountContext,
                    recipient: SignalServiceAddress,
                    message: SignalServiceDataMessage,
                    isSupportAws: Boolean) {
        val content = createMessageContent(accountContext, message, isSupportAws)
        val timestamp = message.timestamp
        val silent = message.groupInfo.isPresent && message.groupInfo.get().type == SignalServiceGroup.Type.REQUEST_INFO
        val response = sendMessage(accountContext, recipient, timestamp,
                content, if (silent) PushPurpose.SILENT else PushPurpose.NORMAL)

        if (response != null && response.needsSync) {
            val syncMessage = createMultiDeviceSentTranscriptContent(content, Optional.of(recipient), timestamp)
            sendMessage(accountContext, mySignalAddress(accountContext), timestamp, syncMessage, PushPurpose.NORMAL)
        }

        if (message.isEndSession) {
            getStore(accountContext).deleteAllSessions(recipient.number)
            SecurityEvent.broadcastSecurityUpdateEvent(AppContextHolder.APP_CONTEXT)
        }
    }

    /**
     * Send a silent message to a single recipient.
     *
     * @param recipient The message's destination.
     * @param message   The message.
     * @throws UntrustedIdentityException
     * @throws IOException
     */

    @Throws(UntrustedIdentityException::class, IOException::class, VersionTooLowException::class)
    fun sendSilentMessage(accountContext: AccountContext,
                          recipient: SignalServiceAddress,
                          message: SignalServiceDataMessage) {
        val content = createMessageContent(accountContext, message)
        val timestamp = message.timestamp
        val response = sendMessage(accountContext, recipient, timestamp, content, PushPurpose.SILENT)

        if (response != null && response.needsSync) {
            val syncMessage = createMultiDeviceSentTranscriptContent(content, Optional.of(recipient), timestamp)
            sendMessage(accountContext, mySignalAddress(accountContext), timestamp, syncMessage, PushPurpose.SILENT)
        }

        if (message.isEndSession) {
            getStore(accountContext).deleteAllSessions(recipient.number)
            SecurityEvent.broadcastSecurityUpdateEvent(AppContextHolder.APP_CONTEXT)
        }
    }

    /**
     * Send a message to a group.
     *
     * @param recipients The group members.
     * @param message    The group message.
     * @throws IOException
     * @throws EncapsulatedExceptions
     */

    @Throws(IOException::class, EncapsulatedExceptions::class)
    fun sendMessage(accountContext: AccountContext, recipients: List<SignalServiceAddress>, message: SignalServiceDataMessage) {
        val content = createMessageContent(accountContext, message)
        val timestamp = message.timestamp
        val silent = message.isGroupUpdate
        val response = sendMessage(accountContext, recipients, timestamp, content, if (silent) PushPurpose.SILENT else PushPurpose.NORMAL)

        try {
            if (response.needsSync) {
                val syncMessage = createMultiDeviceSentTranscriptContent(content, Optional.absent(), timestamp)
                sendMessage(accountContext, mySignalAddress(accountContext), timestamp, syncMessage, PushPurpose.NORMAL)
            }
        } catch (e: UntrustedIdentityException) {
            response.addException(e)
        }

        if (response.hasExceptions()) {
            throw EncapsulatedExceptions(response.untrustedIdentities, response.unregisteredUsers, response.networkExceptions)
        }
    }


    @Throws(IOException::class, UntrustedIdentityException::class)
    fun sendMessage(accountContext: AccountContext, message: SignalServiceSyncMessage) {
        val content: ByteArray

        if (message.contacts.isPresent) {
            content = createMultiDeviceContactsContent(accountContext, message.contacts.get().contactsStream.asStream(),
                    message.contacts.get().isComplete)
        } else if (message.read.isPresent) {
            content = createMultiDeviceReadContent(message.read.get())
        } else if (message.blockedList.isPresent) {
            content = createMultiDeviceBlockedContent(message.blockedList.get())
        } else if (message.configuration.isPresent) {
            content = createMultiDeviceConfigurationContent(message.configuration.get())
        } else if (message.verified.isPresent) {
            sendMessage(accountContext, message.verified.get())
            return
        } else {
            throw IOException("Unsupported sync message!")
        }

        sendMessage(accountContext, mySignalAddress(accountContext), AmeTimeUtil.getMessageSendTime(), content, PushPurpose.NORMAL)
    }

    @Throws(IOException::class, UntrustedIdentityException::class)
    private fun sendMessage(accountContext: AccountContext, message: VerifiedMessage) {
        val nullMessageBody = DataMessage.newBuilder()
                .setBody(Base64.encodeBytes(Util.getRandomLengthBytes(140)))
                .build()
                .toByteArray()

        val nullMessage = NullMessage.newBuilder()
                .setPadding(ByteString.copyFrom(nullMessageBody))
                .build()

        val content = Content.newBuilder()
                .setNullMessage(nullMessage)
                .build()
                .toByteArray()

        val response = sendMessage(accountContext, SignalServiceAddress(message.destination), message.timestamp, content, PushPurpose.NORMAL)

        if (response != null && response.needsSync) {
            val syncMessage = createMultiDeviceVerifiedContent(message, nullMessage.toByteArray())
            sendMessage(accountContext, mySignalAddress(accountContext), message.timestamp, syncMessage, PushPurpose.NORMAL)
        }
    }

    @Throws(IOException::class)
    private fun createReceiptContent(message: SignalServiceReceiptMessage): ByteArray {
        val container = Content.newBuilder()
        val builder = ReceiptMessage.newBuilder()

        for (timestamp in message.timestamps) {
            builder.addTimestamp(timestamp)
        }

        if (message.isDeliveryReceipt)
            builder.type = ReceiptMessage.Type.DELIVERY
        else if (message.isReadReceipt) builder.type = ReceiptMessage.Type.READ

        return container.setReceiptMessage(builder).build().toByteArray()
    }

    @Throws(IOException::class)
    private fun createMessageContent(accountContext: AccountContext, message: SignalServiceDataMessage, isSupportAws: Boolean): ByteArray {
        val container = Content.newBuilder()
        val builder = DataMessage.newBuilder()
        val pointers = createAttachmentPointers(accountContext, message.attachments, isSupportAws)

        if (pointers.isNotEmpty()) {
            builder.addAllAttachments(pointers)
        }

        if (message.body.isPresent) {
            builder.body = message.body.get()
        }

        if (message.isEndSession) {
            builder.flags = DataMessage.Flags.END_SESSION_VALUE
        }

        if (message.isLocation) {
            builder.flags = 8
        }

        if (message.isExpirationUpdate) {
            builder.flags = DataMessage.Flags.EXPIRATION_TIMER_UPDATE_VALUE
        }

        if (message.isProfileKeyUpdate) {
            builder.flags = DataMessage.Flags.PROFILE_KEY_UPDATE_VALUE
        }

        if (message.expiresInSeconds > 0) {
            builder.expireTimer = message.expiresInSeconds
        }

        if (message.profileKey.isPresent) {
            builder.profileKey = ByteString.copyFrom(message.profileKey.get())
        }

        builder.timestamp = message.timestamp

        return container.setDataMessage(builder).build().toByteArray()
    }

    @Throws(IOException::class)
    private fun createMessageContent(accountContext: AccountContext,
                                     message: SignalServiceDataMessage): ByteArray {
        val container = Content.newBuilder()
        val builder = DataMessage.newBuilder()
        val pointers = createAttachmentPointers(accountContext, message.attachments, true)

        if (!pointers.isEmpty()) {
            builder.addAllAttachments(pointers)
        }

        if (message.body.isPresent) {
            builder.body = message.body.get()
        }

        if (message.isEndSession) {
            builder.flags = DataMessage.Flags.END_SESSION_VALUE
        }

        if (message.isLocation) {
            builder.flags = 8
        }

        if (message.isExpirationUpdate) {
            builder.flags = DataMessage.Flags.EXPIRATION_TIMER_UPDATE_VALUE
        }

        if (message.isProfileKeyUpdate) {
            builder.flags = DataMessage.Flags.PROFILE_KEY_UPDATE_VALUE
        }

        if (message.expiresInSeconds > 0) {
            builder.expireTimer = message.expiresInSeconds
        }

        if (message.profileKey.isPresent) {
            builder.profileKey = ByteString.copyFrom(message.profileKey.get())
        }

        builder.timestamp = message.timestamp

        return container.setDataMessage(builder).build().toByteArray()
    }


    private fun createCallContent(callMessage: SignalServiceCallMessage): ByteArray {
        val container = Content.newBuilder()
        val builder = CallMessage.newBuilder()

        if (callMessage.offerMessage.isPresent) {
            val offer = callMessage.offerMessage.get()
            builder.setOffer(CallMessage.Offer.newBuilder()
                    .setId(offer.id)
                    .setDescription(offer.description))
        } else if (callMessage.answerMessage.isPresent) {
            val answer = callMessage.answerMessage.get()
            builder.setAnswer(CallMessage.Answer.newBuilder()
                    .setId(answer.id)
                    .setDescription(answer.description))
        } else if (callMessage.iceUpdateMessages.isPresent) {
            val updates = callMessage.iceUpdateMessages.get()

            for (update in updates) {
                builder.addIceUpdate(CallMessage.IceUpdate.newBuilder()
                        .setId(update.id)
                        .setSdp(update.sdp)
                        .setSdpMid(update.sdpMid)
                        .setSdpMLineIndex(update.sdpMLineIndex))
            }
        } else if (callMessage.hangupMessage.isPresent) {
            builder.setHangup(CallMessage.Hangup.newBuilder().setId(callMessage.hangupMessage.get().id))
        } else if (callMessage.busyMessage.isPresent) {
            builder.setBusy(CallMessage.Busy.newBuilder().setId(callMessage.busyMessage.get().id))
        }

        container.setCallMessage(builder)
        return container.build().toByteArray()
    }

    @Throws(IOException::class)
    private fun createMultiDeviceContactsContent(accountContext: AccountContext,
                                                 contacts: SignalServiceAttachmentStream,
                                                 complete: Boolean): ByteArray {
        val container = Content.newBuilder()
        val builder = createSyncMessageBuilder()
        builder.setContacts(SyncMessage.Contacts.newBuilder()
                .setBlob(createAttachmentPointer(accountContext, contacts))
                .setComplete(complete))

        return container.setSyncMessage(builder).build().toByteArray()
    }

    private fun createMultiDeviceSentTranscriptContent(content: ByteArray, recipient: Optional<SignalServiceAddress>, timestamp: Long): ByteArray {
        try {
            val container = Content.newBuilder()
            val syncMessage = createSyncMessageBuilder()
            val sentMessage = SyncMessage.Sent.newBuilder()
            val dataMessage = Content.parseFrom(content).dataMessage

            sentMessage.timestamp = timestamp
            sentMessage.message = dataMessage


            if (recipient.isPresent) {
                sentMessage.destination = recipient.get().number
            }

            if (dataMessage.expireTimer > 0) {
                sentMessage.expirationStartTimestamp = System.currentTimeMillis()
            }

            return container.setSyncMessage(syncMessage.setSent(sentMessage)).build().toByteArray()
        } catch (e: InvalidProtocolBufferException) {
            throw AssertionError(e)
        }

    }

    private fun createMultiDeviceReadContent(readMessages: List<ReadMessage>): ByteArray {
        val container = Content.newBuilder()
        val builder = createSyncMessageBuilder()

        for (readMessage in readMessages) {
            builder.addRead(SyncMessage.Read.newBuilder()
                    .setTimestamp(readMessage.timestamp)
                    .setSender(readMessage.sender))
        }

        return container.setSyncMessage(builder).build().toByteArray()
    }

    private fun createMultiDeviceBlockedContent(blocked: BlockedListMessage): ByteArray {
        val container = Content.newBuilder()
        val syncMessage = createSyncMessageBuilder()
        val blockedMessage = SyncMessage.Blocked.newBuilder()

        blockedMessage.addAllNumbers(blocked.numbers)

        return container.setSyncMessage(syncMessage.setBlocked(blockedMessage)).build().toByteArray()
    }

    private fun createMultiDeviceConfigurationContent(configuration: ConfigurationMessage): ByteArray {
        val container = Content.newBuilder()
        val syncMessage = createSyncMessageBuilder()
        val configurationMessage = SyncMessage.Configuration.newBuilder()

        if (configuration.readReceipts.isPresent) {
            configurationMessage.readReceipts = configuration.readReceipts.get()
        }

        return container.setSyncMessage(syncMessage.setConfiguration(configurationMessage)).build().toByteArray()
    }

    private fun createMultiDeviceVerifiedContent(verifiedMessage: VerifiedMessage, nullMessage: ByteArray): ByteArray {
        val container = Content.newBuilder()
        val syncMessage = createSyncMessageBuilder()
        val verifiedMessageBuilder = Verified.newBuilder()

        verifiedMessageBuilder.nullMessage = ByteString.copyFrom(nullMessage)
        verifiedMessageBuilder.destination = verifiedMessage.destination
        verifiedMessageBuilder.identityKey = ByteString.copyFrom(verifiedMessage.identityKey.serialize())

        when (verifiedMessage.verified) {
            VerifiedMessage.VerifiedState.DEFAULT -> verifiedMessageBuilder.state = Verified.State.DEFAULT
            VerifiedMessage.VerifiedState.VERIFIED -> verifiedMessageBuilder.state = Verified.State.VERIFIED
            VerifiedMessage.VerifiedState.UNVERIFIED -> verifiedMessageBuilder.state = Verified.State.UNVERIFIED
            else -> throw AssertionError("Unknown: " + verifiedMessage.verified)
        }

        syncMessage.setVerified(verifiedMessageBuilder)
        return container.setSyncMessage(syncMessage).build().toByteArray()
    }

    private fun createSyncMessageBuilder(): SyncMessage.Builder {
        val random = SecureRandom()
        val padding = Util.getRandomLengthBytes(512)
        random.nextBytes(padding)

        val builder = SyncMessage.newBuilder()
        builder.padding = ByteString.copyFrom(padding)

        return builder
    }

    @Throws(IOException::class)
    private fun sendMessage(accountContext: AccountContext,
                            recipients: List<SignalServiceAddress>,
                            timestamp: Long,
                            content: ByteArray,
                            pushPurpose: PushPurpose): SendMessageResponseList {
        val responseList = SendMessageResponseList()

        for (recipient in recipients) {
            try {
                val response = sendMessage(accountContext, recipient, timestamp, content, pushPurpose)
                responseList.addResponse(response)
            } catch (e: UntrustedIdentityException) {
                Log.w(TAG, e)
                responseList.addException(e)
            } catch (e: UnregisteredUserException) {
                Log.w(TAG, e)
                responseList.addException(e)
            } catch (e: PushNetworkException) {
                Log.w(TAG, e)
                responseList.addException(NetworkFailureException(recipient.number, e))
            }

        }

        return responseList
    }

    @Throws(UntrustedIdentityException::class, IOException::class)
    private fun sendMessage(accountContext: AccountContext,
                            recipient: SignalServiceAddress,
                            timestamp: Long,
                            content: ByteArray,
                            pushPurpose: PushPurpose): SendMessageResponse? {
        for (i in 0..2) {
            try {
                val messages = getEncryptedMessages(accountContext, recipient, timestamp, content, pushPurpose)

                try {
                    ALog.w(TAG, "Transmitting over pipe...")
                    return AmeModuleCenter.serverDaemon(accountContext).sendMessage(messages)
                } catch (e: IOException) {
                    ALog.e(TAG, "sendMessage", e)
                }


                ALog.w(TAG, "Not transmitting over pipe...")
                return ChatHttp.get(accountContext).sendMessage(messages)
            } catch (mde: MismatchedDevicesException) {
                Log.w(TAG, mde)
                handleMismatchedDevices(accountContext, recipient, mde.mismatchedDevices)
            } catch (ste: StaleDevicesException) {
                Log.w(TAG, ste)
                handleStaleDevices(accountContext, recipient, ste.staleDevices)
            }

        }

        throw IOException("Failed to resolve conflicts after 3 attempts!")
    }

    @Throws(IOException::class)
    private fun createAttachmentPointers(accountContext: AccountContext,
                                         attachments: Optional<List<SignalServiceAttachment>>,
                                         isSupportAws: Boolean): List<AttachmentPointer> {
        val pointers = LinkedList<AttachmentPointer>()

        if (!attachments.isPresent || attachments.get().isEmpty()) {
            Log.w(TAG, "No attachments present...")
            return pointers
        }

        for (attachment in attachments.get()) {
            if (attachment.isStream) {
                Log.w(TAG, "Found attachment, creating pointer...")
                if (isSupportAws) {
                    pointers.add(createAwsAttachmentPointer(accountContext, attachment.asStream()))
                } else {
                    pointers.add(createAttachmentPointer(accountContext, attachment.asStream()))
                }
            }
        }

        return pointers
    }

    @Throws(IOException::class)
    private fun createAwsAttachmentPointer(accountContext: AccountContext,
                                           attachment: SignalServiceAttachmentStream): AttachmentPointer {
        val attachmentKey = attachment.attachmentKey
        val paddedLength = PaddingInputStream.getPaddedSize(attachment.length)
        val ciphertextLength = AttachmentCipherOutputStream.getCiphertextLength(paddedLength)
        val attachmentData = PushAttachmentData(attachment.contentType,
                PaddingInputStream(attachment.inputStream, attachment.length),
                ciphertextLength,
                AttachmentCipherOutputStreamFactory(attachmentKey),
                attachment.listener)

        val attachmentUrlAndDigest = ChatHttp.get(accountContext).uploadAttachmentToAws(attachmentData, attachment.fileName.orNull())
        attachment.setUploadResult(0L, attachmentUrlAndDigest.first(), attachmentUrlAndDigest.second())

        val builder = AttachmentPointer.newBuilder()
                .setContentType(attachment.contentType)
                .setKey(ByteString.copyFrom(attachmentKey))
                .setDigest(ByteString.copyFrom(attachmentUrlAndDigest.second()))
                .setSize(attachment.length.toInt())
                .setUrl(attachmentUrlAndDigest.first())
        if (attachment.fileName.isPresent) {
            builder.fileName = attachment.fileName.get()
        }

        if (attachment.preview.isPresent) {
            builder.thumbnail = ByteString.copyFrom(attachment.preview.get())
        }

        if (attachment.voiceNote) {
            builder.flags = AttachmentPointer.Flags.VOICE_MESSAGE_VALUE
        }

        return builder.build()
    }

    @Throws(IOException::class)
    private fun createAttachmentPointer(accountContext: AccountContext,
                                        attachment: SignalServiceAttachmentStream): AttachmentPointer {
        val attachmentKey = attachment.attachmentKey
        val paddedLength = PaddingInputStream.getPaddedSize(attachment.length)
        val ciphertextLength = AttachmentCipherOutputStream.getCiphertextLength(paddedLength)
        val attachmentData = PushAttachmentData(attachment.contentType,
                PaddingInputStream(attachment.inputStream, attachment.length),
                ciphertextLength,
                AttachmentCipherOutputStreamFactory(attachmentKey),
                attachment.listener)

        val attachmentUrlAndDigest = ChatHttp.get(accountContext).uploadAttachmentToAws(attachmentData, attachment.fileName.orNull())
        attachment.setUploadResult(0L, attachmentUrlAndDigest.first(), attachmentUrlAndDigest.second())

        val builder = AttachmentPointer.newBuilder()
                .setContentType(attachment.contentType)
                .setUrl(attachmentUrlAndDigest.first())
                .setKey(ByteString.copyFrom(attachmentKey))
                .setDigest(ByteString.copyFrom(attachmentUrlAndDigest.second()))
                .setSize(attachment.length.toInt())

        if (attachment.fileName.isPresent) {
            builder.fileName = attachment.fileName.get()
        }

        if (attachment.preview.isPresent) {
            builder.thumbnail = ByteString.copyFrom(attachment.preview.get())
        }

        if (attachment.voiceNote) {
            builder.flags = AttachmentPointer.Flags.VOICE_MESSAGE_VALUE
        }

        return builder.build()
    }


    @Throws(IOException::class, UntrustedIdentityException::class)
    private fun getEncryptedMessages(accountContext: AccountContext,
                                     recipient: SignalServiceAddress,
                                     timestamp: Long,
                                     plaintext: ByteArray,
                                     pushPurpose: PushPurpose): OutgoingPushMessageList {
        val messages = LinkedList<OutgoingPushMessage>()

        if (recipient.number != mySignalAddress(accountContext).number) {
            messages.add(getEncryptedMessage(accountContext, recipient, SignalServiceAddress.DEFAULT_DEVICE_ID, plaintext, pushPurpose))
        }

        val store = getStore(accountContext)
        for (deviceId in store.getSubDeviceSessions(recipient.number)) {
            if (store.containsSession(SignalProtocolAddress(recipient.number, deviceId))) {
                messages.add(getEncryptedMessage(accountContext, recipient, deviceId, plaintext, pushPurpose))
            }
        }

        return OutgoingPushMessageList(recipient.number, timestamp, recipient.relay.orNull(), messages)
    }

    //
    @Throws(IOException::class, UntrustedIdentityException::class)
    private fun getEncryptedMessage(accountContext: AccountContext,
                                    recipient: SignalServiceAddress,
                                    deviceId: Int,
                                    plaintext: ByteArray,
                                    pushPurpose: PushPurpose): OutgoingPushMessage {
        val store = getStore(accountContext)

        val signalProtocolAddress = SignalProtocolAddress(recipient.number, deviceId)
        val cipher = SignalServiceCipher(mySignalAddress(accountContext), store)

        var containsSession = store.containsSession(signalProtocolAddress)
        if (containsSession) {
            val sessionRecord = store.loadSession(signalProtocolAddress)

            if (sessionRecord.sessionState.remoteRegistrationId == 0) {
                store.deleteSession(signalProtocolAddress)
                containsSession = false
            }
        }

        if (!containsSession) {
            try {
                val preKeys = ChatHttp.get(accountContext).getPreKeys(recipient, deviceId)

                for (preKey in preKeys) {
                    try {
                        val identityKeyString = Base64.encodeBytes(preKey.identityKey.serialize())
                        if (!AddressUtil.isValid(recipient.number, identityKeyString)) {
                            Logger.e("getEncryptedMessage getPreKeys error identity key got")
                            throw UntrustedIdentityException("error identity key got", recipient.number, preKey.identityKey)
                        }

                        val preKeyAddress = SignalProtocolAddress(recipient.number, preKey.deviceId)
                        val sessionBuilder = SessionBuilder(store, preKeyAddress)
                        sessionBuilder.process(preKey)
                    } catch (e: org.whispersystems.libsignal.UntrustedIdentityException) {
                        throw UntrustedIdentityException("Untrusted identity key!", recipient.number, preKey.identityKey)
                    }

                }

                SecurityEvent.broadcastSecurityUpdateEvent(AppContextHolder.APP_CONTEXT)
            } catch (e: InvalidKeyException) {
                throw IOException(e)
            }
        }

        try {
            return cipher.encrypt(signalProtocolAddress, plaintext, pushPurpose)
        } catch (e: org.whispersystems.libsignal.UntrustedIdentityException) {
            throw UntrustedIdentityException("Untrusted on send", recipient.number, e.untrustedIdentity)
        }
    }

    @Throws(IOException::class, UntrustedIdentityException::class)
    private fun handleMismatchedDevices(accountContext: AccountContext,
                                        recipient: SignalServiceAddress,
                                        mismatchedDevices: MismatchedDevices) {
        try {
            val store = getStore(accountContext)

            for (extraDeviceId in mismatchedDevices.extraDevices) {
                store.deleteSession(SignalProtocolAddress(recipient.number, extraDeviceId))
            }

            for (missingDeviceId in mismatchedDevices.missingDevices) {
                val preKey = ChatHttp.get(accountContext).getPreKey(recipient, missingDeviceId)

                try {
                    val sessionBuilder = SessionBuilder(store, SignalProtocolAddress(recipient.number, missingDeviceId))
                    sessionBuilder.process(preKey)
                } catch (e: org.whispersystems.libsignal.UntrustedIdentityException) {
                    Log.e(TAG, "Untrusted identity key from handleMismatchedDevices ")
                    throw UntrustedIdentityException("Untrusted identity key!", recipient.number, preKey.identityKey)
                }
            }
        } catch (e: InvalidKeyException) {
            throw IOException(e)
        }
    }

    private fun handleStaleDevices(accountContext: AccountContext, recipient: SignalServiceAddress, staleDevices: StaleDevices) {
        val store = getStore(accountContext)
        for (staleDeviceId in staleDevices.staleDevices) {
            store.deleteSession(SignalProtocolAddress(recipient.number, staleDeviceId))
        }
    }

    private fun mySignalAddress(accountContext: AccountContext): SignalServiceAddress {
        return SignalServiceAddress(accountContext.uid)
    }
}
