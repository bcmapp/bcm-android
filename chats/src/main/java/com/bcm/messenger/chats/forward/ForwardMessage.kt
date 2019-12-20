package com.bcm.messenger.chats.forward

import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.recipients.Recipient

/**
 * Forward structures
 *
 * Created by zjl on 2018/8/22.
 */
object ForwardType {
    const val HAS_PRIVATE = 11
    const val HAS_PUBLIC = 12
    const val NO_MESSAGE = 10

    fun getType(message: ForwardOnceMessage): Int {
        if (message.privateMessage != null)
            return HAS_PRIVATE
        if (message.groupMessage != null)
            return HAS_PUBLIC
        return NO_MESSAGE
    }

    fun getType(message: ForwardMessage): Int {
        if (message.privateMessage != null)
            return HAS_PRIVATE
        if (message.groupMessage != null)
            return HAS_PUBLIC
        return NO_MESSAGE
    }
}


data class ForwardMessage(val privateMessage: MessageRecord?, val groupMessage: AmeGroupMessageDetail?, val list: List<Recipient>, val masterSecret: MasterSecret? = null)

data class ForwardOnceMessage(val privateMessage: MessageRecord?, val groupMessage: AmeGroupMessageDetail?, val recipient: Recipient, val masterSecret: MasterSecret? = null, val commentText: String? = null)