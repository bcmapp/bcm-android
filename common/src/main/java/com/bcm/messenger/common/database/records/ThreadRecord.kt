package com.bcm.messenger.common.database.records

import androidx.room.Ignore
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.database.model.ThreadDbModel
import com.bcm.messenger.common.database.repositories.*
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.utils.GroupUtil
import com.bcm.messenger.utility.logger.ALog

/**
 * Created by Kin on 2019/9/17
 */
class ThreadRecord : ThreadDbModel() {
    @Ignore private val TAG = "ThreadRecord"

    @Ignore private lateinit var recipient: Recipient
    @Ignore private var groupMessage: AmeGroupMessage<*>? = null

    fun setRecipient(recipient: Recipient) {
        this.recipient = recipient
    }

    fun getRecipient(accountContext: AccountContext): Recipient {
        if (!::recipient.isInitialized) {
            ALog.d(TAG, "getRecipient init")
            recipient = Recipient.from(accountContext, uid, true)
        }
        return recipient
    }


    fun getGroupMessage(): AmeGroupMessage<*>? {
        if (groupMessage == null && (isGroup() || isLocation())) {
            groupMessage = AmeGroupMessage.messageFromJson(snippetContent)
        }
        return groupMessage
    }

    fun isFailed(): Boolean {
        if (isGroup()) {
            val msg = AmeGroupMessage.messageFromJson(snippetContent)
            return msg.type != AmeGroupMessage.SYSTEM_INFO && snippetType == AmeGroupMessageDetail.SendState.SEND_FAILED.value
        }
        return isFailedMessageType(snippetType) ||
                isFailedCannotDecryptType(snippetType) ||
                isPendingInsecureSmsFallbackType(snippetType)
    }

    fun isPending(): Boolean {
        if (isGroup()) return snippetType == AmeGroupMessageDetail.SendState.SENDING.value
        return isPendingMessageType(snippetType) ||
                isIdentityVerifiedType(snippetType) ||
                isIdentityDefaultType(snippetType)
    }

    fun isGroup(): Boolean {
        return distributionType == ThreadRepo.DistributionTypes.NEW_GROUP || GroupUtil.isTTGroup(uid)
    }

    fun isRead(): Boolean {
        return this.unreadCount <= 0
    }

    fun isPendingInsecureFallback() = isPendingInsecureSmsFallbackType(snippetType)

    fun isJoinRequestMessage() = isJoinRequestType(snippetType)

    fun isAtMeMessage() = isAtMeType(snippetType)

    fun isDraftMessage() = isDraftMessageType(snippetType)

    fun isExpirationTimerUpdate() = isExpirationTimerUpdateType(snippetType)

    fun isOutgoing() = isOutgoingMessageType(snippetType)

    fun isDecrypting() = isDecryptInProgressType(snippetType)

    fun isLocation() = isLocationType(snippetType)

    fun isKeyExchange() = isKeyExchangeType(snippetType)

    fun isDecryptFail() = isFailedDecryptType(snippetType)

    fun isNoSession() = isNoRemoteSessionType(snippetType)

    fun isPlaintext() = true

    fun isEndSession() = isEndSessionType(snippetType)

    fun isLegacy() = isLegacyType(snippetType)

    fun isMissedCall() = isIncomingMissedCallType(snippetType) || isOutgoingMissedCallType(snippetType)

    fun isCallLog() = isCallLogType(snippetType)

    fun isIdentityUpdate() = isIdentityUpdateType(snippetType)

    fun isIdentityVerify() = isIdentityVerifiedType(snippetType)

    fun isIdentityDefault() = isIdentityDefaultType(snippetType)

    fun isJoin() = isJoinedType(snippetType)

    fun isOutgoingMissedCall() = isOutgoingMissedCallType(snippetType)

}