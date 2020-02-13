package com.bcm.messenger.chats.group.viewholder

import android.view.View
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.components.BigContentRecycleFragment
import com.bcm.messenger.chats.group.logic.GroupMessageLogic
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.grouprepository.model.AmeHistoryMessageDetail
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.common.ui.emoji.EmojiTextView
import com.bcm.messenger.common.utils.getAttrColor
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.common.utils.hideKeyboard
import com.bcm.messenger.utility.MultiClickObserver

/**
 *
 * Created by wjh on 2018/10/23
 */
open class ChatMessageHolderAction(accountContext: AccountContext) : BaseChatHolderAction<EmojiTextView>(accountContext) {
    private var mMultiClickObserver = MultiClickObserver(2, object : MultiClickObserver.MultiClickListener {
        override fun onMultiClick(view: View?, count: Int) {
            val detail = mMessageDetail
            if (detail != null && detail !is AmeHistoryMessageDetail) {
                view?.hideKeyboard()
                BigContentRecycleFragment.showBigContent(view?.context as? AccountSwipeBaseActivity
                        ?: return, detail.gid, detail.indexId)
            }
        }
    })

    override fun bindData(message: AmeGroupMessageDetail, body: EmojiTextView, glideRequests: GlideRequests, batchSelected: Set<AmeGroupMessageDetail>?) {
        val text = textFromMessage(message)
        if (!message.isSendByMe) {
            if (message.message.type == AmeGroupMessage.NONSUPPORT){
                body.setTextColor(body.context.getAttrColor(R.attr.common_text_third_color))
            } else {
                body.setTextColor(body.context.getAttrColor(R.attr.chats_conversation_income_text_color))
            }
        } else {
            body.setTextColor(body.context.getAttrColor(R.attr.common_text_white_color))
        }

        ChatViewHolder.interceptMessageText(body, message, text)

        body.setOnClickListener(mMultiClickObserver)
    }

    private fun textFromMessage(record:AmeGroupMessageDetail): String{
        val data = record.message?.content as? AmeGroupMessage.TextContent
        return data?.text ?: ((record.message?.content as? AmeGroupMessage.LinkContent)?.url?:"")
    }

    override fun unBind() {
    }

    override fun resend(messageRecord: AmeGroupMessageDetail) {
        GroupMessageLogic.get(accountContext).messageSender.resendTextMessage(messageRecord)
    }

}