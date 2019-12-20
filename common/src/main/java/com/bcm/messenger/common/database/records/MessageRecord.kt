package com.bcm.messenger.common.database.records

import androidx.room.Ignore
import com.bcm.messenger.common.R
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.database.model.PrivateChatDbModel
import com.bcm.messenger.common.database.repositories.*
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.utils.ExpirationUtil
import com.bcm.messenger.common.utils.getString
import com.bcm.messenger.utility.AppContextHolder

/**
 * Created by Kin on 2019/9/17
 */
class MessageRecord() : ChatMessageModel() {
    constructor(model: PrivateChatDbModel) : this() {
        this.id = model.id
        this.threadId = model.threadId
        this.uid = model.uid
        this.addressDevice = model.addressDevice
        this.dateReceive = model.dateReceive
        this.dateSent = model.dateSent
        this.read = model.read
        this.type = model.type
        this.messageType = model.messageType
        this.body = model.body
        this.attachmentCount = model.attachmentCount
        this.expiresTime = model.expiresTime
        this.expiresStartTime = model.expiresStartTime
        this.readRecipientCount = model.readRecipientCount
        this.callType = model.callType
        this.callDuration = model.callDuration
    }

    @Ignore private val MAX_DISPLAY_LENGTH = 2000
    @Ignore private lateinit var recipient: Recipient

    override fun equals(other: Any?): Boolean {
        if (other !is MessageRecord) return false
        return other.id == id &&
                other.threadId == threadId &&
                other.dateReceive == dateReceive &&
                other.dateSent == dateSent &&
                other.type == type &&
                other.messageType == messageType &&
                other.attachmentCount == attachmentCount &&
                other.body == body &&
                other.expiresTime == expiresTime &&
                other.expiresStartTime == expiresStartTime
    }

    fun getRecipient(): Recipient {
        if (!::recipient.isInitialized) {
            recipient = Recipient.from(AppContextHolder.APP_CONTEXT, Address.fromSerialized(uid), true)
        }
        return recipient
    }

    fun getDisplayBody(): CharSequence {
        return when {
            isDecryptFail() -> getString(R.string.MessageDisplayHelper_bad_encrypted_message)
            isCorruptedKeyExchange() -> getString(R.string.common_record_received_corrupted_key_exchange_message)
            isInvalidVersionKeyExchange() -> getString(R.string.common_record_received_key_exchange_message_for_invalid_protocol_version)
            isLegacy() -> getString(R.string.MessageRecord_message_encrypted_with_a_legacy_protocol_version_that_is_no_longer_supported)
            isBundleKeyExchange() -> getString(R.string.common_record_received_message_with_new_safety_number_tap_to_process)
            isKeyExchange() && isOutgoing() -> ""
            isKeyExchange() && !isOutgoing() -> getString(R.string.common_conversation_received_key_exchange_message_tap_to_process)
            isDuplicateMessage() -> getString(R.string.common_record_duplicate_message)
            isDecrypting() -> getString(R.string.MessageDisplayHelper_decrypting_please_wait)
            isNoSession() -> getString(R.string.MessageDisplayHelper_message_encrypted_for_non_existing_session)
            isEndSession() && isOutgoing() -> getString(R.string.common_record_secure_session_reset)
            isIncomingCall() -> getString(R.string.MessageRecord_s_called_you, recipient.toShortString())
            isOutgoingCall() -> getString(R.string.MessageRecord_called_s, recipient.toShortString())
            isMissedCall() -> getString(R.string.MessageRecord_missed_call_from, recipient.toShortString())
            isJoin() -> getString(R.string.MessageRecord_s_joined_signal, recipient.toShortString())
            isExpirationTimerUpdate() -> {
                val time = ExpirationUtil.getExpirationDisplayValue(AppContextHolder.APP_CONTEXT, expiresTime.toInt() / 1000)
                if (isOutgoing()) {
                    getString(R.string.MessageRecord_you_set_disappearing_message_time_to_s, time)
                } else {
                    getString(R.string.MessageRecord_s_set_disappearing_message_time_to_s, recipient.toShortString(), time)
                }
            }
            isLocation() -> getString(R.string.common_location_message_description)
            body.length > MAX_DISPLAY_LENGTH -> body.substring(0, MAX_DISPLAY_LENGTH)
            else -> body
        }
    }

    fun isFailed() = isFailedMessageType(type) || isFailedCannotDecryptType(type) || isPendingInsecureSmsFallbackType(type)

    fun isPending() = isPendingMessageType(type) || isIdentityVerifiedType(type) || isIdentityDefaultType(type)

    fun isPendingInsecureFallback() = isPendingInsecureSmsFallbackType(type)

    fun isDraftMessage() = isDraftMessageType(type)

    fun isExpirationTimerUpdate() = isExpirationTimerUpdateType(type)

    fun isOutgoing() = isOutgoingMessageType(type)

    fun isDecrypting() = isDecryptInProgressType(type)

    fun isLocation() = isLocationType(type)

    fun isKeyExchange() = isKeyExchangeType(type)

    fun isDecryptFail() = isFailedDecryptType(type)

    fun isNoSession() = isNoRemoteSessionType(type)

    fun isPlaintext() = true

    fun isEndSession() = isEndSessionType(type)

    fun isLegacy() = isLegacyType(type)

    fun isMissedCall() = isIncomingMissedCallType(type) || isOutgoingMissedCallType(type)

    fun isCallLog() = isCallLogType(type)

    fun isIdentityUpdate() = isIdentityUpdateType(type)

    fun isIdentityVerify() = isIdentityVerifiedType(type)

    fun isIdentityDefault() = isIdentityDefaultType(type)

    fun isJoin() = isJoinedType(type)

    fun isCorruptedKeyExchange() = isCorruptedKeyExchangeType(type)

    fun isInvalidVersionKeyExchange() = isInvalidVersionKeyExchangeType(type)

    fun isBundleKeyExchange() = isBundleKeyExchangeType(type)

    fun isDuplicateMessage() = isDuplicateMessageType(type)

    fun isIncomingCall() = isIncomingCallType(type)

    fun isOutgoingCall() = isOutgoingCallType(type)

    fun isOutgoingMissedCall() = isOutgoingMissedCallType(type)

    fun isMediaPending(): Boolean {
        if (!isMediaMessage()) return false
        attachments.forEach {
            if (it.isInProgress() || it.isPendingDownload()) {
                return true
            }
        }
        return false
    }

    fun isAudioCall() = messageType == MessageType.CALL.type && callType == CallType.AUDIO.type

    fun isVideoCall() = messageType == MessageType.CALL.type && callType == CallType.VIDEO.type

    fun isMediaMessage() = messageType != MessageType.TEXT.type && messageType != MessageType.LOCATION.type && messageType != MessageType.CALL.type
}