package com.bcm.messenger.chats.components

import android.content.Context
import android.util.AttributeSet
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.corebean.HistoryMessageDetail
import com.bcm.messenger.common.utils.getAttrColor
import kotlinx.android.synthetic.main.chats_history_view.view.*

/**
 * Created by Kin on 2018/10/24
 */
class HistoryView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : androidx.constraintlayout.widget.ConstraintLayout(context, attrs, defStyleAttr) {
    private var inView = 1

    init {
        inflate(context, R.layout.chats_history_view, this)
    }

    fun setStyle(inView: Int) {
        this.inView = inView
        if (inView == 2) {
            chats_history_title.setTextColor(context.getAttrColor(R.attr.common_text_white_color))
            chats_history_view_more.setTextColor(context.getAttrColor(R.attr.chats_conversation_outgo_text_secondary_color))
            chats_history_icon.drawable.setTint(context.getAttrColor(R.attr.common_white_color))
        } else {
            chats_history_title.setTextColor(context.getAttrColor(R.attr.common_text_main_color))
            chats_history_view_more.setTextColor(context.getAttrColor(R.attr.common_text_secondary_color))
            chats_history_icon.drawable.setTint(context.getAttrColor(R.attr.common_icon_color_grey))
        }
    }

    fun bindData(accountContext: AccountContext, messageList: List<HistoryMessageDetail>) {
        if (inView == 2) {
            chats_history_inner_view.setViewStyle(IN_VIEW_CHAT_SEND)
        } else {
            chats_history_inner_view.setViewStyle(IN_VIEW_CHAT_RECEIVE)
        }
        chats_history_inner_view.setHistoryData(accountContext, messageList, true)
    }

}