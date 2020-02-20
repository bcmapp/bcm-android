package com.bcm.messenger.chats.components.titlebar

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.components.ChatsBurnSetting
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.provider.AMELogin
import kotlinx.android.synthetic.main.chats_titlebar_layout.view.*
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.utils.*
import com.bcm.route.api.BcmRouter

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
    private var address: Address? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        View.inflate(context, R.layout.chats_titlebar_layout, this)
        chat_status_fill.layoutParams = chat_status_fill.layoutParams.apply {
            height = context.getStatusBarHeight()
        }

        bar_back_text.setOnClickListener {
            mCallback?.onLeft(mMultiSelect)
        }
        bar_back_img.setOnClickListener {
            mCallback?.onLeft(mMultiSelect)
        }
        bar_recipient_photo.setOnClickListener {
            mCallback?.onRight(mMultiSelect)
        }
        bar_center.setOnClickListener {
            mCallback?.onTitle(mMultiSelect)
        }

        bar_title_sub.setOnClickListener {
            mCallback?.onTitle(mMultiSelect)
        }
    }

    fun setOnChatTitleCallback(callback: OnChatTitleCallback?) {
        mCallback = callback
    }

    fun addOption(@DrawableRes icon: Int, @AttrRes tint:Int = R.attr.common_foreground_color, invoke: (() -> Unit)? = null) {
        if (existOption(icon)) {
            return
        }

        val imageView = ImageView(context)
        imageView.scaleType = ImageView.ScaleType.CENTER
        val hp = 2.dp2Px()
        imageView.setPadding(hp, 0, hp, 0)
        imageView.setImageResource(icon)
        if (tint != 0) {
            imageView.setColorFilter(context.getAttrColor(tint))
        }
        imageView.tag = icon
        imageView.visibility = View.VISIBLE
        imageView.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
        bar_center.addView(imageView)

        if (null != invoke) {
            imageView.setOnClickListener {
                invoke.invoke()
            }
        }
    }

    fun removeOption(@DrawableRes icon: Int) {
        for (i in 0 until bar_center.childCount) {
            val child = bar_center.getChildAt(i) ?: continue
            if (child is ImageView) {
                if (child.tag == icon) {
                    bar_center.removeView(child)
                    break
                }
            }
        }
    }

    private fun existOption(@DrawableRes icon: Int): Boolean {
        for (i in 0 until bar_center.childCount) {
            val child = bar_center.getChildAt(i) ?: continue
            if (child is ImageView) {
                if (child.tag == icon) {
                    return true
                }
            }
        }
        return false
    }

    fun setPrivateChat(recipient: Recipient?) {
        mInPrivateChat = true
        if (recipient == null) {
            bar_title_layout.visibility = View.GONE
            return
        } else {
            address = recipient.address
            bar_title_main.text = recipient.name
            if (recipient.isFriend) {
                bar_title_sub.visibility = View.GONE
            } else {
                bar_title_sub.visibility = View.VISIBLE
                bar_title_sub.text = context.getString(R.string.chats_recipient_stranger_role)
            }

            val type = ChatsBurnSetting.expireToType(recipient.expireMessages)
            if (type > 0) {
                bar_right_layout.setBackgroundResource(R.drawable.common_tiktalk_ring)
                burn_time.visibility = View.VISIBLE
                burn_time.text = ChatsBurnSetting.typeToString(type)
            } else {
                bar_right_layout.setBackgroundResource(0)
                burn_time.visibility = View.GONE
            }

            bar_recipient_photo.showPrivateAvatar(recipient)
        }
        setMultiSelectionMode(mMultiSelect)
    }

    fun setGroupChat(accountContext: AccountContext, gid: Long, name: String, memberCount: Int) {
        mInPrivateChat = false

        address = GroupUtil.addressFromGid(accountContext, gid)

        bar_title_sub.visibility = View.GONE
        bar_title_main.visibility = View.VISIBLE
        bar_title_main.text = "$name($memberCount)"
        bar_recipient_photo.showGroupAvatar(accountContext, gid)
        setMultiSelectionMode(mMultiSelect)
    }

    fun updateGroupName(name: String, memberCount: Int) {
        bar_title_main.text = "$name($memberCount)"
    }

    fun updateGroupAvatar(accountContext: AccountContext, gid: Long, path: String) {
        bar_recipient_photo.showGroupAvatar(accountContext, gid, path = path)
    }

    fun setMultiSelectionMode(multiSelect: Boolean) {
        mMultiSelect = multiSelect
        showRight(!multiSelect)
        if (multiSelect) {
            bar_back_text.text = context.getString(R.string.chats_select_mode_cancel_btn)
            bar_back_text.visibility = View.VISIBLE
            bar_back_img.visibility = View.GONE
        } else {
            bar_back_text.visibility = View.INVISIBLE
            bar_back_img.visibility = View.VISIBLE
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
}