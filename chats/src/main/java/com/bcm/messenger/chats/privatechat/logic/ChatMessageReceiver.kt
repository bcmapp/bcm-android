package com.bcm.messenger.chats.privatechat.logic

import com.bcm.messenger.chats.privatechat.core.ChatHttp
import com.bcm.messenger.chats.privatechat.jobs.PushDecryptJob
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.core.AddressUtil
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.crypto.storage.SignalProtocolStoreImpl
import com.bcm.messenger.common.deprecated.MessagingDatabase
import com.bcm.messenger.common.database.model.DecryptFailData
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.server.IServerDataListener
import com.bcm.messenger.common.utils.format
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.logger.ALog
import com.google.protobuf.AbstractMessage
import org.json.JSONObject
import org.whispersystems.libsignal.SessionBuilder
import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.libsignal.util.guava.Optional
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.push.SignalServiceProtos
import org.whispersystems.signalservice.internal.util.Base64
import java.nio.charset.StandardCharsets
import java.util.*

class ChatMessageReceiver(private val accountContext: AccountContext) : IServerDataListener {
    private val TAG = "ChatMessageReceiver"

    override fun onReceiveData(accountContext: AccountContext, proto: AbstractMessage): Boolean {
        if (accountContext != this.accountContext) {
            return false
        }

        when (proto) {
            is SignalServiceProtos.Mailbox -> {
                val mismatchDevices = mutableSetOf<SignalServiceAddress>()

                proto.envelopesList.forEach {
                    val uid = handleEnvelope(accountContext, it)
                    if (uid.isNotEmpty() && isDecryptedFailedRecipient(it)) {
                        val address = addressFromEnvelope(uid, it)
                        mismatchDevices.add(address)
                    }
                }

                updateMismatchSessions(accountContext, mismatchDevices.toList())
            }
            is SignalServiceProtos.Envelope -> {
                val uid = handleEnvelope(accountContext, proto)

                if (isDecryptedFailedRecipient(proto)) {
                    val address = addressFromEnvelope(uid, proto)
                    updateMismatchSessions(accountContext, listOf(address))
                }
            }
            else -> {
                return false
            }
        }

        return true
    }

    private fun addressFromEnvelope(uid: String, envelope: SignalServiceProtos.Envelope): SignalServiceAddress {
        return SignalServiceAddress(uid,
                if (envelope.hasRelay())
                    Optional.fromNullable(envelope.relay)
                else
                    Optional.absent())
    }


    private fun handleEnvelope(accountContext: AccountContext, envelope: SignalServiceProtos.Envelope): String {
        var fromUid = envelope.source
        if (envelope.sourceExtra != null && !envelope.sourceExtra.isEmpty) {
            fromUid = getRealUid(accountContext, envelope.sourceExtra.toByteArray())
        }

        if (fromUid.isNullOrEmpty()) {
            ALog.w(TAG, "who are u?")
            return ""
        }

        val local = AmeTimeUtil.serverTimeMillis()
        if (envelope.timestamp > local) {
            ALog.e(TAG, "handleEnvelope remote:${envelope.timestamp} local:$local")
        }

        val context = AppContextHolder.APP_CONTEXT
        val source = Address.from(accountContext, fromUid)
        val recipient = Recipient.from(source, false)

        when (envelope.type) {
            SignalServiceProtos.Envelope.Type.RECEIPT -> {
                ALog.i(TAG, "recv recipient:" + System.currentTimeMillis())

                val messageId = MessagingDatabase.SyncMessageId(source, envelope.timestamp)

                if (isDecryptedFailedRecipient(envelope)) {
                    saveDecryptFailedState(accountContext, messageId)
                }
                val chatRepo = Repository.getChatRepo(accountContext)
                chatRepo?.incrementDeliveryReceiptCount(messageId.address.serialize(), messageId.timetamp)
            }
            SignalServiceProtos.Envelope.Type.CIPHERTEXT, SignalServiceProtos.Envelope.Type.PREKEY_BUNDLE -> {
                ALog.i(TAG, "recv message:" + System.currentTimeMillis())
                if (!recipient.isBlocked) {
                    val messageId = Repository.getPushRepo(accountContext)?.insert(envelope) ?: -1L
                    val jobManager = AmeModuleCenter.accountJobMgr(accountContext)
                    jobManager?.add(PushDecryptJob(context, accountContext, messageId))
                } else {
                    ALog.w(TAG, "*** Received blocked push message, ignoring...")
                }
            }
            else -> {
                ALog.w(TAG, "Received envelope of unknown type:${envelope.type}")
            }
        }

        return fromUid
    }

    private fun saveDecryptFailedState(accountContext: AccountContext, messageId: MessagingDatabase.SyncMessageId) {
        val threadRepo = Repository.getThreadRepo(accountContext)
        val chatRepo = Repository.getChatRepo(accountContext)
        val success = chatRepo?.setMessageCannotDecrypt(messageId.address.serialize(), messageId.timetamp) ?: false
        if (success) {
            val dataJson = threadRepo?.getDecryptFailData(messageId.address.toString())
            val data: DecryptFailData
            if (!dataJson.isNullOrEmpty()) {
                data = GsonUtils.fromJson(dataJson, DecryptFailData::class.java)
            } else {
                data = DecryptFailData()
            }
            data.increaseFailCount()
            if (data.firstNotFoundMsgTime == 0L || data.firstNotFoundMsgTime > messageId.timetamp) {
                data.firstNotFoundMsgTime = messageId.timetamp
            }
            threadRepo?.setDecryptFailData(messageId.address.toString(), data.toJson())
        }
    }

    private fun isDecryptedFailedRecipient(envelope: SignalServiceProtos.Envelope): Boolean {
        return envelope.type == SignalServiceProtos.Envelope.Type.RECEIPT
                && envelope.content.toByteArray().format().toUpperCase(Locale.getDefault()) == "STALE"
    }

    private fun updateMismatchSessions(accountContext: AccountContext, deviceList: List<SignalServiceAddress>) {
        if (deviceList.isEmpty()) {
            return
        }

        val context = AppContextHolder.APP_CONTEXT
        val store = SignalProtocolStoreImpl(context, accountContext)
        try {
            for (device in deviceList) {
                store.deleteSession(SignalProtocolAddress(device.number, SignalServiceAddress.DEFAULT_DEVICE_ID))

                val preKeyBundles = ChatHttp.get(accountContext).getPreKeys(device, SignalServiceAddress.DEFAULT_DEVICE_ID)
                for (preKey in preKeyBundles) {
                    try {
                        val identityKeyString = String(EncryptUtils.base64Encode(preKey.identityKey.serialize()))
                        if (!AddressUtil.isValid(device.number, identityKeyString)) {
                            ALog.e(TAG, "getPreKeys error identity key got")
                            continue
                        }

                        val sessionBuilder = SessionBuilder(store, SignalProtocolAddress(device.number, SignalServiceAddress.DEFAULT_DEVICE_ID))
                        sessionBuilder.process(preKey)
                    } catch (e: Exception) {
                        ALog.w(TAG, "Untrusted identity key from handleMismatchedDevices")
                    }
                }
            }
        } catch (e: Exception) {
            ALog.logForSecret(TAG, "updateMismatchSessions error", e)
        }
    }

    private fun getRealUid(accountContext: AccountContext, encryptSource: ByteArray): String? {
        return try {
            val decodeString = String(Base64.decode(encryptSource), StandardCharsets.UTF_8)
            val json = JSONObject(decodeString)
            val iv = Base64.decode(json.getString("iv"))
            val ephemeralPubKey = ByteArray(32)
            System.arraycopy(Base64.decode(json.getString("ephemeralPubkey")), 1, ephemeralPubKey, 0, 32)
            val source = Base64.decode(json.getString("source"))

            val psw = BCMEncryptUtils.calculateAgreementKeyWithMe(accountContext, ephemeralPubKey)
            val shaPsw = EncryptUtils.computeSHA256(psw)
            String(EncryptUtils.decryptAES(source, shaPsw, "AES/CBC/PKCS7Padding", iv))
        } catch (e: Throwable) {
            null
        }
    }
}