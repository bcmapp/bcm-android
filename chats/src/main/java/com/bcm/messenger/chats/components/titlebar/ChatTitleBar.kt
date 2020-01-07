package com.bcm.messenger.chats.components.titlebar

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.utils.getStatusBarHeight
import com.bcm.messenger.utility.setDrawableLeft
import com.bcm.messenger.utility.setDrawableRight
import kotlinx.android.synthetic.main.chats_titlebar_layout.view.*
import com.bcm.messenger.common.recipients.Recipient

/**
 * Created by bcm.social.01 on 2019/1/27.
 */
class ChatTitleBar : androidx.constraintlayout.widget.ConstraintLayout {

    interface OnChatTitleCallback {
        fun onLeft(multiSelect: Boolean)
        fun onRight(multiSelect: Boolean)
        fun onTitle(multiSelect: Boolean)
    }

    private var mShowDot: Boolean = false
    private var mMultiSelect: Boolean = false
    private var mInPrivateChat: Boolean = false
    private var mCallback: OnChatTitleCallback? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        View.inflate(context, R.layout.chats_titlebar_layout, this)
        chat_status_fill.layoutParams = chat_status_fill.layoutParams.apply {
            height = context.getStatusBarHeight()
        }

        custom_view.setOnClickListener {}

        bar_back.setOnClickListener {
            mCallback?.onLeft(mMultiSelect)
        }
        bar_recipient_photo.setOnClickListener {
            mCallback?.onRight(mMultiSelect)
        }
        bar_title_main.setOnClickListener {
            mCallback?.onTitle(mMultiSelect)
        }
        bar_title_whole.setOnClickListener {
            mCallback?.onTitle(mMultiSelect)
        }

        setBackgroundResource(R.color.common_color_white)
    }

    fun setOnChatTitleCallback(callback: OnChatTitleCallback?) {
        mCallback = callback
    }

    fun setPrivateChat(recipient: Recipient?) {
        mInPrivateChat = true
        if (recipient == null) {
            bar_title_layout.visibility = View.GONE
            bar_title_whole.visibility = View.GONE
            return
        } else {
            if (recipient.isFriend) {
                bar_title_layout.visibility = View.GONE
                bar_title_whole.visibility = View.VISIBLE
                bar_title_whole.text = recipient.name
            } else {
                bar_title_layout.visibility = View.VISIBLE
                bar_title_whole.visibility = View.GONE
                bar_title_main.text = recipient.name
                bar_title_main.setDrawableRight(R.drawable.chats_add_friend_icon)
                bar_title_sub.text = context.getString(R.string.chats_recipient_stranger_role)
            }
            bar_recipient_photo.showPrivateAvatar(recipient)
        }
        setMultiSelectionMode(mMultiSelect)
    }

    fun setGroupChat(accountContext: AccountContext, gid: Long, name: String, memberCount: Int) {
        mInPrivateChat = false
        bar_title_layout.visibility = View.GONE
        bar_title_whole.visibility = View.VISIBLE
        bar_title_whole.text = "$name($memberCount)"
        bar_recipient_photo.showGroupAvatar(accountContext, gid)
        setMultiSelectionMode(mMultiSelect)
    }

    fun updateGroupName(name: String, memberCount: Int) {
        bar_title_whole.text = "$name($memberCount)"
    }

    fun updateGroupAvatar(accountContext: AccountContext, gid: Long, path: String) {
        bar_recipient_photo.showGroupAvatar(accountContext, gid, path = path)
    }

    fun setMultiSelectionMode(multiSelect: Boolean) {
        mMultiSelect = multiSelect
        showRight(!multiSelect)
        if (multiSelect) {
            bar_back.setDrawableLeft(0)
            bar_back.text = context.getString(R.string.chats_select_mode_cancel_btn)
        } else {
            bar_back.text = ""
            bar_back.setDrawableLeft(R.drawable.common_back_arrow_black_icon)
        }
    }

    fun showRight(show: Boolean) {
        bar_recipient_photo.visibility = if (show && !mMultiSelect) View.VISIBLE else View.GONE
        if (show && !mMultiSelect && mShowDot) {
            bar_right_layout.showDot()
        } else {
            bar_right_layout.hideBadge()
        }
    }

    fun showDot(show: Boolean) {
        mShowDot = show
        if (show && bar_recipient_photo.visibility == View.VISIBLE) {
            bar_right_layout.showDot()
        } else {
            bar_right_layout.hideBadge()
        }
    }

    fun addCustomView(view: View, marginLeft: Int = 0, marginTop: Int = 0, marginRight: Int = 0, marginBottom: Int = 0) {
        if (view.parent != null) {
            val p = view.parent as ViewGroup
            p.removeView(view)
        }

        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.setMargins(marginLeft, marginTop, marginRight, marginBottom)
        view.layoutParams = params

        custom_view.addView(view)
    }

    fun removeCustomView(view: View) {
        if (view.parent == custom_view) {
            custom_view.removeView(view)
        }
    }
}