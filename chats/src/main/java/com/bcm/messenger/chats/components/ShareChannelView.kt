package com.bcm.messenger.chats.components

import android.content.Context
import android.graphics.Paint
import androidx.constraintlayout.widget.ConstraintLayout

import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.AttrRes
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.ui.GroupMemberPhotoView
import com.bcm.messenger.common.utils.getAttrColor

/**
 * Created by zjl on 2018/6/9.
 */
class ShareChannelView : FrameLayout {

    private var title: TextView
    private var avater: GroupMemberPhotoView
    private var link: TextView
    private var content: TextView
    private var viewBtn: Button
    private var container: ConstraintLayout

    private var listener: ChannelOnClickListener? = null

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        View.inflate(context, R.layout.chats_share_channel_view, this)
        container = findViewById(R.id.share_channel_container)
        title = findViewById(R.id.share_channel_title)
        avater = findViewById(R.id.share_channel_avater)
        link = findViewById(R.id.share_channel_link)
        content = findViewById(R.id.share_channel_content)
        viewBtn = findViewById(R.id.share_channel_btn)
        viewBtn.setOnClickListener { v ->
            listener?.onClick(v)
        }
    }


    fun setLinkAppearance(@AttrRes mainTextColor: Int, @AttrRes linkColor: Int, isSendByMe: Boolean) {
        title.setTextColor(context.getAttrColor(mainTextColor))
        content.setTextColor(context.getAttrColor(mainTextColor))
        link.setTextColor(context.getAttrColor(linkColor))
        viewBtn.setTextColor(context.getAttrColor(linkColor))
        if (isSendByMe) {
            viewBtn.setBackgroundResource(R.drawable.chats_channel_view_white_bg)
        } else {
            viewBtn.setBackgroundResource(R.drawable.chats_channel_view_blue_bg)
        }
    }


    fun setAvatar(address: Address) {
        avater.setAvatar(address)
    }


    fun setTitleContent(title: String?, content: String?) {
        if (!title.isNullOrEmpty()) {
            this.title.text = title
        }
        if (!content.isNullOrEmpty()) {
            this.content.text = content
        }
    }


    fun setLink(linkText: String?) {
        setLink(linkText,false)
    }


    fun setLink(linkText: String?, isNew: Boolean) {
        if (!linkText.isNullOrEmpty()) {
            if (isNew) {
                link.text = linkText
            } else {
                link.text = context.getString(R.string.chats_channel_share_describe)
            }
            link.paint.flags = Paint.UNDERLINE_TEXT_FLAG
        }
    }


    interface ChannelOnClickListener {
        fun onClick(v: View)
    }


    fun setChannelClickListener(listener: ChannelOnClickListener) {
        this.listener = listener
    }
}