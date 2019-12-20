package com.bcm.messenger.chats.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.bcm.messenger.chats.R
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.utils.dp2Px
import kotlinx.android.synthetic.main.chats_rtc_call_item.view.*

/**
 */
class ChatRtcCallItem : LinearLayout {

    companion object {
        private const val TAG = "ChatRtcCallItem"
        const val TYPE_MUTE = 1
        const val TYPE_END = 2
        const val TYPE_SPEAKER = 3
        const val TYPE_ACCEPT = 4
        const val TYPE_DECLINE = 5
        const val TYPE_VIDEO = 6
        const val TYPE_SWITCH = 7
    }

    private var mIconSize: Int = 0
    private var mType = 0
    private var mListener: OnControlActionListener? = null

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0, 0) {}

    constructor(context: Context) : this(context, null, 0, 0) {}

    override fun setOrientation(orientation: Int) {}

    private fun initialize() {
        super.setOrientation(VERTICAL)
        View.inflate(context, R.layout.chats_rtc_call_item, this)

        rtc_item_icon.setOnCheckedChangeListener { _, isChecked ->
            ALog.d(TAG, "onCheckChanged isChecked: $isChecked")

            if (mListener?.onChecked(mType, isChecked) == false) {
                rtc_item_icon.isChecked = !isChecked
                ALog.d(TAG, "onCheckChanged onCheck fail")

            }

        }

        val onClickListener = OnClickListener {
        }
        rtc_item_icon?.setOnClickListener(onClickListener)
        rtc_item_content?.setOnClickListener(onClickListener)

        setIconSize(64.dp2Px())
    }

    override fun setEnabled(enabled: Boolean) {
        rtc_item_icon?.isEnabled = enabled
        rtc_item_content?.isEnabled = enabled
        if (!enabled) {
            alpha = 0.4f
        } else {
            alpha = 1.0f
        }
        super.setEnabled(enabled)
    }

    override fun setOnClickListener(l: OnClickListener?) {
        rtc_item_icon?.setOnClickListener(l)
        rtc_item_content?.setOnClickListener(l)
    }


    fun setOnControlActionListener(listener: OnControlActionListener?) {
        mListener = listener
    }


    fun setIconSize(size: Int) {
        mIconSize = size
        val lp = rtc_item_icon.layoutParams
        lp.width = size
        lp.height = size
        rtc_item_icon.layoutParams = lp
    }

    fun getType(): Int {
        return mType
    }


    fun setType(type: Int, showContent: Boolean = true) {
        mType = type
        rtc_item_content?.visibility = if (showContent) View.VISIBLE else View.GONE

        when (mType) {
            TYPE_MUTE -> {
                rtc_item_icon?.setBackgroundResource(R.drawable.chats_call_mute_selector)
                rtc_item_content.text = resources.getString(R.string.chats_call_mute_text)
            }
            TYPE_SPEAKER -> {
                rtc_item_icon?.setBackgroundResource(R.drawable.chats_call_speaker_selector)
                rtc_item_content?.text = resources.getString(R.string.chats_call_speaker_text)
            }
            TYPE_END -> {
                rtc_item_icon?.setBackgroundResource(R.drawable.chats_call_hangup_icon)
                rtc_item_content?.text = resources.getString(R.string.chats_call_cancel_text)
            }
            TYPE_ACCEPT -> {
                rtc_item_icon?.setBackgroundResource(R.drawable.chats_call_accept_icon)
                rtc_item_content?.text = resources.getString(R.string.chats_call_accept_text)
            }
            TYPE_DECLINE -> {
                rtc_item_icon?.setBackgroundResource(R.drawable.chats_call_hangup_icon)
                rtc_item_content?.text = resources.getString(R.string.chats_call_decline_text)
            }
            TYPE_VIDEO -> {
                rtc_item_icon?.setBackgroundResource(R.drawable.chats_call_video_selector)
                rtc_item_content?.text = resources.getString(R.string.chats_call_video_text)
            }
            TYPE_SWITCH -> {
                rtc_item_icon?.setBackgroundResource(R.drawable.chats_call_camera_switch_selector)
                rtc_item_content?.text = resources.getString(R.string.chats_call_camera_switch_text)
            }
        }
    }


    fun setChecked(checked: Boolean) {
        rtc_item_icon?.isChecked = checked
    }

    fun isChecked(): Boolean {
        return rtc_item_icon?.isChecked ?: false
    }

    interface OnControlActionListener {
        fun onChecked(type: Int, isChecked: Boolean): Boolean
    }

}
