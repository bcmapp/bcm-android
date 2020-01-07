package com.bcm.messenger.chats.group.viewholder

import com.bcm.messenger.chats.components.GroupShareCardView
import com.bcm.messenger.chats.group.logic.GroupMessageLogic
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.mms.GlideRequests

/**
 * ViewHolder for group share
 *
 * Created by wjh on 2019/6/5
 */
class GroupShareHolderAction : BaseChatHolderAction<GroupShareCardView>() {

    override fun bindData(accountContext: AccountContext, message: AmeGroupMessageDetail, body: GroupShareCardView, glideRequests: GlideRequests, batchSelected: Set<AmeGroupMessageDetail>?) {
        val content = message.message.content as AmeGroupMessage.GroupShareContent
        body.bind(content, message.isSendByMe)
    }

    override fun resend(accountContext: AccountContext, messageRecord: AmeGroupMessageDetail) {
        GroupMessageLogic.get(accountContext).messageSender.resendTextMessage(messageRecord)
    }

}