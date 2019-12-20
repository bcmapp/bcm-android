package com.bcm.messenger.chats.group.viewholder

import android.view.View
import com.bcm.messenger.chats.bean.ReplyMessageEvent
import com.bcm.messenger.chats.components.ChatReplyView
import com.bcm.messenger.chats.group.logic.GroupMessageLogic
import com.bcm.messenger.chats.util.ChatComponentListener
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.mms.GlideRequests
import org.greenrobot.eventbus.EventBus

/**
 *
 * Created by wjh on 2018/11/27
 */
class ChatReplyHolderAction() : BaseChatHolderAction<ChatReplyView>(), ChatComponentListener {

    override fun onClick(v: View, data: Any) {
        if(data is AmeGroupMessageDetail) {
            EventBus.getDefault().post(ReplyMessageEvent(data, ReplyMessageEvent.ACTION_LOCATE))
        }
    }

    override fun bindData(message: AmeGroupMessageDetail, body: ChatReplyView, glideRequests: GlideRequests, batchSelected: Set<AmeGroupMessageDetail>?) {
        body.setReplyClickListener(this)
        body.setReply(message)
    }

    override fun resend(messageRecord: AmeGroupMessageDetail) {
        GroupMessageLogic.messageSender.resendTextMessage(messageRecord)
    }

}