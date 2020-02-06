package com.bcm.messenger.chats.components.titlebar

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.utils.getColor
import kotlinx.android.synthetic.main.chats_title_bar_drop_item_view.view.*

/**
 * bcm.social.01 2019/1/27.
 */
class ChatTitleDropItemView:FrameLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        View.inflate(context, R.layout.chats_title_bar_drop_item_view, this)
    }

    fun setViewItem(item: ChatTitleDropItem, action:(v:View)->Unit){
        drop_title.text = item.title
        if (item.icon != 0){
            val drawable= resources.getDrawable(item.icon, null)
            drawable.setTint(resources.getColor(item.tint))
            drawable.setBounds(0, 0, drawable.minimumWidth, drawable.minimumHeight)
            drop_title.setCompoundDrawables(null, drawable, null,null)
        } else {
            drop_title.setCompoundDrawables(null, null, null,null)
        }

        if (item.action != null){
            drop_title.setOnClickListener {
                action(it)
                item.action.invoke(it)
            }
        }
    }
}