package com.bcm.messenger.chats.group.viewholder

import android.text.Layout
import android.text.StaticLayout
import android.view.View
import androidx.fragment.app.FragmentActivity
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.components.BigContentRecycleFragment
import com.bcm.messenger.chats.group.logic.GroupMessageLogic
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.grouprepository.model.AmeHistoryMessageDetail
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.common.ui.emoji.EmojiTextView
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.common.utils.getScreenWidth
import com.bcm.messenger.common.utils.hideKeyboard
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.MultiClickObserver

/**
 *
 * Created by wjh on 2018/10/23
 */
open class ChatMessageHolderAction(accountContext: AccountContext) : BaseChatHolderAction<EmojiTextView>(accountContext) {

    companion object {

        private var BODY_MAX_WIDTH = 0

        fun getBodyMaxWidth(): Int {
            if (BODY_MAX_WIDTH == 0) {
                BODY_MAX_WIDTH = AppContextHolder.APP_CONTEXT.getScreenWidth() - 130.dp2Px()
            }
            return BODY_MAX_WIDTH
        }
    }

    private var mMultiClickObserver = MultiClickObserver(2, object : MultiClickObserver.MultiClickListener {
        override fun onMultiClick(view: View?, count: Int) {
            val detail = mMessageDetail
            if (detail != null && detail !is AmeHistoryMessageDetail) {
                view?.hideKeyboard()
                BigContentRecycleFragment.showBigContent(view?.context as? FragmentActivity
                        ?: return, detail.gid, detail.indexId)
            }
        }
    })

    override fun bindData(message: AmeGroupMessageDetail, body: EmojiTextView, glideRequests: GlideRequests, batchSelected: Set<AmeGroupMessageDetail>?) {
        val text = textFromMessage(message)
        if (!message.isSendByMe) {
            if (message.message.type == AmeGroupMessage.NONSUPPORT){
                body.setTextColor(body.context.getColorCompat(R.color.common_color_A8A8A8))
            } else {
                body.setTextColor(body.context.getColorCompat(R.color.common_color_black))
            }
        } else {
            body.setTextColor(body.context.getColorCompat(R.color.common_color_white))
        }

        ChatViewHolder.interceptMessageText(body, message, text)

        body.setOnClickListener(mMultiClickObserver)

        val staticLayout = StaticLayout(body.text, body.paint, getBodyMaxWidth(), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
        val w = ChatViewHolder.requestFitWidth(staticLayout)
        if (w > 0) {
            val lp = body.layoutParams
            if (lp != null) {
                lp.width = w + body.paddingStart + body.paddingEnd
                body.layoutParams = lp
            }
        }
    }

    private fun textFromMessage(record:AmeGroupMessageDetail): String{
        val data = record.message?.content as? AmeGroupMessage.TextContent
        return if (null != data) {
            data.text
        } else {
            (record.message?.content as? AmeGroupMessage.LinkContent)?.url?:""
        }
    }

    override fun unBind() {
    }

    override fun resend(messageRecord: AmeGroupMessageDetail) {
        GroupMessageLogic.get(accountContext).messageSender.resendTextMessage(messageRecord)
    }

}