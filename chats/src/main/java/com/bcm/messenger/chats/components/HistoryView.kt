package com.bcm.messenger.chats.components

import android.content.Context
import android.util.AttributeSet
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.core.corebean.HistoryMessageDetail
import com.bcm.messenger.common.utils.AppUtil
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
            chats_history_title.setTextColor(AppUtil.getColor(resources, R.color.common_color_white))
            chats_history_view_more.setTextColor(AppUtil.getColor(resources, R.color.chats_mine_tips_text_color))
            chats_history_icon.setImageDrawable(AppUtil.getDrawable(resources, com.bcm.messenger.common.R.drawable.common_right_arrow_white_icon))
        } else {
            chats_history_title.setTextColor(AppUtil.getColor(resources, R.color.common_color_black))
            chats_history_view_more.setTextColor(AppUtil.getColor(resources, R.color.common_content_second_color))
            chats_history_icon.setImageDrawable(AppUtil.getDrawable(resources, com.bcm.messenger.common.R.drawable.common_right_arrow_icon))
        }
    }

    fun bindData(messageList: List<HistoryMessageDetail>) {
        if (inView == 2) {
            chats_history_inner_view.setViewStyle(IN_VIEW_CHAT_SEND)
        } else {
            chats_history_inner_view.setViewStyle(IN_VIEW_CHAT_RECEIVE)
        }
        chats_history_inner_view.setHistoryData(messageList, true)
    }

}