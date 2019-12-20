package com.bcm.messenger.chats.privatechat.jobs

import android.content.Context
import com.bcm.messenger.chats.privatechat.core.BcmChatCore
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.database.db.UserDatabase
import com.bcm.messenger.common.exception.InsecureFallbackApprovalException
import com.bcm.messenger.common.exception.RetryLaterException
import com.bcm.messenger.common.grouprepository.room.entity.ChatHideMessage
import com.bcm.messenger.common.jobs.RetrieveProfileJob
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException
import java.io.IOException

/**
 * Created by Kin on 2019/5/8
 */
class PushHideMessageSendJob(context: Context, private val messageId: Long, destination: Address) : PushSendJob(context, constructParameters(context, destination, "control")) {
    private val TAG = "PushHideMessageSendJob"

    override fun onShouldRetryThrowable(exception: Exception?): Boolean {
        return exception is RetryLaterException
    }

    override fun onAdded() {
        ALog.i(TAG, "Add $messageId")
    }

    override fun onPushSend(masterSecret: MasterSecret?) {
        val dao = UserDatabase.getDatabase().chatControlMessageDao()
        val message = dao.queryHideMessage(messageId)

        try {
            ALog.i(TAG, "Push send $messageId, time is ${message.sendTime}")
            deliver(message)
            ALog.i(TAG, "Push send completed")
        } catch (e: InsecureFallbackApprovalException) {
            ALog.e(TAG, "Push send fail", e)
        } catch (e: UntrustedIdentityException) {
            AmeModuleCenter.accountJobMgr()?.add(RetrieveProfileJob(context, Recipient.from(context, Address.fromSerialized(e.e164Number), false)))
        }
    }

    override fun onCanceled() {
        ALog.i(TAG, "Canceled $messageId")
    }

    @Throws(UntrustedIdentityException::class, InsecureFallbackApprovalException::class, RetryLaterException::class)
    private fun deliver(message: ChatHideMessage) {
        try {
            val individualRecipient = Recipient.from(AppContextHolder.APP_CONTEXT, Address.fromSerialized(message.destinationAddress), false)
            val address = getPushAddress(individualRecipient.address)
            val profileKey = getProfileKey(individualRecipient)
            val textSecureMessage = SignalServiceDataMessage.newBuilder()
                    .withTimestamp(message.sendTime)
                    .withBody(message.content)
                    .withExpiration(0)
                    .withProfileKey(profileKey.orNull())
                    .asEndSessionMessage(false)
                    .asLocation(true)
                    .build()

            BcmChatCore.sendSilentMessage(address, textSecureMessage)
        } catch (e: UnregisteredUserException) {
            ALog.e(TAG, e)
            throw InsecureFallbackApprovalException(e)
        } catch (e: IOException) {
            ALog.e(TAG, e)
            throw RetryLaterException(e)
        }

    }
}