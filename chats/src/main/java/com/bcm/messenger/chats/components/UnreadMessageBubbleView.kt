package com.bcm.messenger.chats.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.bcm.messenger.chats.R



/**
 * Unread bubble
 *
 * Created by shuanglingli on 2018/3/14.
 */
class UnreadMessageBubbleView : RelativeLayout {

    var unreadCount: Int = 0
        private set
    var lastSeenPosition = -1
    var lastSeen: Long = -1L

    private var unreadCountText: TextView? = null

    constructor(context: Context) : super(context) {}

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}

    override fun onFinishInflate() {
        super.onFinishInflate()
        this.unreadCountText = findViewById(R.id.unread_count_text)
    }

    fun setUnreadMessageCount(count: Int) {
        unreadCount = count
        unreadCountText?.text = resources.getString(R.string.chats_new_messages, if (count > 99) "99+" else count.toString())
    }


    fun setOrientationIcon(@DrawableRes resId: Int) {
        val drawable = resources.getDrawable(resId)
        drawable.setBounds(0, 0, drawable.minimumWidth, drawable.minimumHeight)
        unreadCountText?.setCompoundDrawables(drawable, null, null, null)
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        if (visibility == View.GONE) {
            unreadCount = 0
            lastSeenPosition = -1
        }
    }
}
