package com.bcm.messenger.chats.group.viewholder

import android.content.Intent
import android.view.View
import com.bcm.messenger.chats.components.HistoryView
import com.bcm.messenger.chats.components.IN_VIEW_CHAT_RECEIVE
import com.bcm.messenger.chats.components.IN_VIEW_CHAT_SEND
import com.bcm.messenger.chats.group.logic.GroupMessageLogic
import com.bcm.messenger.chats.history.ChatHistoryActivity
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.utility.logger.ALog

/**
 * Created by Kin on 2018/10/24
 */
class ChatHistoryHolderAction(accountContext: AccountContext) : BaseChatHolderAction<HistoryView>(accountContext), View.OnClickListener {

    private val TAG = "ChatHistoryHolderAction"

    override fun onClick(v: View) {
        val messageRecord = mMessageDetail ?: return
        ALog.i(TAG, "onClick message index: ${messageRecord.indexId}, gid: ${messageRecord.gid}")
        val intent = Intent(v.context, ChatHistoryActivity::class.java).apply {
            putExtra(ChatHistoryActivity.CHAT_HISTORY_GID, messageRecord.gid)
            putExtra(ChatHistoryActivity.MESSAGE_INDEX_ID, messageRecord.indexId)
        }
        v.context.startActivity(intent)
    }

    override fun bindData(message: AmeGroupMessageDetail, body: HistoryView, glideRequests: GlideRequests, batchSelected: Set<AmeGroupMessageDetail>?) {
        body.setOnClickListener(this)
        if (message.isSendByMe) {
            body.setStyle(IN_VIEW_CHAT_SEND)
        } else {
            body.setStyle(IN_VIEW_CHAT_RECEIVE)
        }
        val content = message.message.content as AmeGroupMessage.HistoryContent
        ALog.i(TAG, "bindData index: ${message.indexId}, gid: ${message.gid}, messageList: ${content.messageList.size}")
        body.bindData(accountContext, content.messageList)
    }

    override fun resend(messageRecord: AmeGroupMessageDetail) {
        GroupMessageLogic.get(accountContext).messageSender.resendHistoryMessage(messageRecord)
    }

}