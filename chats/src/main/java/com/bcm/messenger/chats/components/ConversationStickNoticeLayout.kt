package com.bcm.messenger.chats.components

import android.content.Context
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import android.view.View
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.core.corebean.AmeGroupInfo
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.getColor
import com.bcm.messenger.utility.StringAppearanceUtil
import kotlinx.android.synthetic.main.chats_layout_stick_notice.view.*

/**
 * Created by wjh on 2019/5/24
 */
class ConversationStickNoticeLayout : androidx.constraintlayout.widget.ConstraintLayout {

    interface OnStickNoticeClickListener {
        fun onSecureClick()
        fun onRelationClick()
    }

    private var mListener: OnStickNoticeClickListener? = null

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        View.inflate(context, R.layout.chats_layout_stick_notice, this)

        stick_notify_text_one.setOnClickListener {
            mListener?.onSecureClick()
        }

        stick_notify_text_two.setOnClickListener {
            mListener?.onRelationClick()
        }

        stick_notify_text_one.visibility = View.GONE
        stick_notify_text_two.visibility = View.GONE
    }


    fun setOnStickNoticeClickListener(listener: OnStickNoticeClickListener?) {
        mListener = listener
    }


    fun setGroupInfo(groupInfo: AmeGroupInfo) {
        stick_notify_text_one?.visibility = View.VISIBLE
        val text = "  " + context.getString(R.string.chats_stick_group_notify_secure_text)
        val d = AppUtil.getDrawable(resources, R.drawable.chats_chat_secure_icon)
        d.setBounds(0, 0, d.intrinsicWidth, d.intrinsicHeight)
        stick_notify_text_one?.text = StringAppearanceUtil.addImage(text, d, 0)
    }


    fun setRecipient(recipient: Recipient) {
        stick_notify_text_one?.visibility = View.VISIBLE
        val text = "  " + context.getString(R.string.chats_stick_notify_secure_text)
        val d = AppUtil.getDrawable(resources, R.drawable.chats_chat_secure_icon)
        d.setBounds(0, 0, d.intrinsicWidth, d.intrinsicHeight)
        stick_notify_text_one?.text = StringAppearanceUtil.addImage(text, d, 0)

        if (recipient.isFriend) {
            stick_notify_text_two?.visibility = View.GONE
        }else {
            stick_notify_text_two?.visibility = View.VISIBLE
            val span = SpannableStringBuilder(context.getString(R.string.chats_stick_notify_relation_temp_text, recipient.name))
            span.append("\n\n")
            span.append(StringAppearanceUtil.applyAppearance(context.getString(R.string.chats_relation_add_friend_action), color = getColor(R.color.common_app_primary_color)))
            stick_notify_text_two?.text = span
        }
    }


    fun setNormal(notice: CharSequence) {
        stick_notify_text_one?.visibility = View.VISIBLE
        stick_notify_text_one?.text = notice
    }

    fun showLoading(loading: Boolean) {
        if (loading) {
            stick_notify_loading.visibility = View.VISIBLE
            stick_notify_loading.startAnim()
        }else {
            stick_notify_loading.visibility = View.GONE
            stick_notify_loading.stopAnim()
        }
    }
}