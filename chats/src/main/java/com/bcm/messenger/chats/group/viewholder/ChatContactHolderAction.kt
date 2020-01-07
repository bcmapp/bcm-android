package com.bcm.messenger.chats.group.viewholder

import com.bcm.messenger.chats.components.ContactCardView
import com.bcm.messenger.chats.group.logic.GroupMessageLogic
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.mms.GlideRequests

/**
 * bcm.social.01 2018/10/23.
 */
class ChatContactHolderAction(accountContext: AccountContext) : BaseChatHolderAction<ContactCardView>(accountContext) {

    override fun bindData(message: AmeGroupMessageDetail, body: ContactCardView, glideRequests: GlideRequests, batchSelected: Set<AmeGroupMessageDetail>?) {
        body.setContact(message.gid, message.message.content as AmeGroupMessage.ContactContent, message.isSendByMe)
    }

    override fun resend(messageRecord: AmeGroupMessageDetail) {
        GroupMessageLogic.get(accountContext).messageSender.resendContactMessage(messageRecord)
    }
}