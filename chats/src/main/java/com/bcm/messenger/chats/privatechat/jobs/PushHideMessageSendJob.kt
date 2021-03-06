package com.bcm.messenger.chats.privatechat.jobs

import android.content.Context
import com.bcm.messenger.chats.privatechat.core.BcmChatCore
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.exception.InsecureFallbackApprovalException
import com.bcm.messenger.common.exception.RetryLaterException
import com.bcm.messenger.common.grouprepository.room.entity.ChatHideMessage
import com.bcm.messenger.common.jobs.RetrieveProfileJob
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.utility.logger.ALog
import org.greenrobot.eventbus.EventBus
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException
import java.io.IOException

/**
 * Created by Kin on 2019/5/8
 */
class PushHideMessageSendJob(
        context: Context,
        accountContext: AccountContext,
        private val messageId: Long,
        private val messageType:Long,
        destination: Address)
    : PushSendJob(
        context,
        accountContext,
        constructParameters(context, accountContext, destination, "control")) {
    private val TAG = "PushHideMessageSendJob"

    override fun onShouldRetryThrowable(exception: Exception?): Boolean {
        return exception is RetryLaterException
    }

    override fun onAdded() {
        ALog.i(TAG, "Add $messageId")
    }

    override fun onPushSend(masterSecret: MasterSecret?) {
        val message = repository.chatHideMessageRepo.queryHideMessage(messageId)

        var succeed = false
        try {
            ALog.i(TAG, "Push send $messageId, time is ${message.sendTime}")
            deliver(message)
            succeed = true
            ALog.i(TAG, "Push send completed")
        } catch (e: InsecureFallbackApprovalException) {
            ALog.e(TAG, "Push send fail", e)
        } catch (e: UntrustedIdentityException) {
            AmeModuleCenter.accountJobMgr(accountContext)?.add(RetrieveProfileJob(context, accountContext, Recipient.from(accountContext, e.e164Number, false)))
        } finally {
            val address = Address.from(accountContext, message.destinationAddress)
            EventBus.getDefault().post(HideMessageSendResult(messageType, address, succeed))
        }
    }

    override fun onCanceled() {
        ALog.i(TAG, "Canceled $messageId")
    }

    @Throws(UntrustedIdentityException::class, InsecureFallbackApprovalException::class, RetryLaterException::class)
    private fun deliver(message: ChatHideMessage) {
        try {
            val individualRecipient = Recipient.from(accountContext, message.destinationAddress, false)
            val address = getPushAddress(individualRecipient.address)
//            val profileKey = getProfileKey(individualRecipient)
            val textSecureMessage = SignalServiceDataMessage.newBuilder()
                    .withTimestamp(message.sendTime)
                    .withBody(message.content)
                    .withExpiration(0)
//                    .withProfileKey(profileKey.orNull())
                    .asEndSessionMessage(false)
                    .asLocation(true)
                    .build()

            BcmChatCore.sendSilentMessage(accountContext, address, textSecureMessage)
        } catch (e: UnregisteredUserException) {
            ALog.e(TAG, e)
            throw InsecureFallbackApprovalException(e)
        } catch (e: IOException) {
            ALog.e(TAG, e)
            throw RetryLaterException(e)
        }
    }
}