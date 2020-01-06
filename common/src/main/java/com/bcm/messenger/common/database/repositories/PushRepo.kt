package com.bcm.messenger.common.database.repositories

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.database.NoSuchMessageException
import com.bcm.messenger.common.database.model.PushDbModel
import com.bcm.messenger.utility.Base64
import com.google.protobuf.ByteString
import org.whispersystems.signalservice.internal.push.SignalServiceProtos

/**
 * Created by Kin on 2019/9/26
 */
class PushRepo(
        private val accountContext: AccountContext,
        private val repository: Repository) {

    private val pushDao = repository.userDatabase.getPushDao()

    fun insert(envelope: SignalServiceProtos.Envelope): Long {
        val messageId = find(envelope)
        if (messageId > 0) return messageId

        val model = PushDbModel()
        model.type = envelope.type.number
        model.sourceUid = envelope.source
        model.deviceId = envelope.sourceDevice
        model.legacyMessage = if (envelope.hasLegacyMessage()) Base64.encodeBytes(envelope.legacyMessage.toByteArray()) else ""
        model.content = if (envelope.hasContent()) Base64.encodeBytes(envelope.content.toByteArray()) else ""
        model.timestamp = envelope.timestamp
        model.sourceRegistrationId = envelope.sourceRegistration

        return pushDao.insertPushMessage(model)
    }

    fun insert(model: PushDbModel): Long {
        return pushDao.insertPushMessage(model)
    }

    private fun find(envelope: SignalServiceProtos.Envelope): Long {
        val model = pushDao.queryPushMessage(envelope.type.number, envelope.source, envelope.sourceDevice,
                if (envelope.hasContent()) Base64.encodeBytes(envelope.content.toByteArray()) else "",
                if (envelope.hasLegacyMessage()) Base64.encodeBytes(envelope.legacyMessage.toByteArray()) else "",
                envelope.timestamp)
        return model?.id ?: -1L
    }

    fun get(id: Long): SignalServiceProtos.Envelope {
        try {
            val model = pushDao.queryPushMessage(id)
            if (model != null) {
                val builder = SignalServiceProtos.Envelope.newBuilder()
                        .setType(SignalServiceProtos.Envelope.Type.valueOf(model.type))
                        .setSource(model.sourceUid)
                        .setSourceDevice(model.deviceId)
                        .setTimestamp(model.timestamp)
                        .setSourceRegistration(model.sourceRegistrationId)
                        .setRelay("")
                if (model.legacyMessage.isNotEmpty()) {
                    builder.legacyMessage = ByteString.copyFrom(Base64.decode(model.legacyMessage))
                }

                if (model.content.isNotEmpty()) {
                    builder.content = ByteString.copyFrom(Base64.decode(model.content))
                }

                return builder.build()
            }
        } catch (e: Exception) {

        }
        throw NoSuchMessageException("Not found")
    }

    fun delete(id: Long) {
        pushDao.deletePushMessage(id)
    }
}