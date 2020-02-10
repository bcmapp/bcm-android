package com.bcm.messenger.chats.components.titlebar

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.PopupWindow
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.utils.AppUtil


/**
 * bcm.social.01 2019/1/27.
 */
class ChatTitleDropMenu : PopupWindow() {
    private val TAG = "PopupWindow"
    private var itemList = mutableListOf<ChatTitleDropItem>()

    fun updateMenu(itemList: List<ChatTitleDropItem>) {
        this.itemList.clear()
        this.itemList.addAll(itemList)
    }

    fun show(anchor: View) {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        isTouchable = true
        isFocusable = true
        isOutsideTouchable = true
        animationStyle = 0

        val contentView = LayoutInflater.from(anchor.context).inflate(R.layout.chats_title_bar_menu_layout, null) as LinearLayout
        contentView.setBackgroundResource(R.color.common_default_background_color)
        contentView.setOnClickListener {
            dismiss()
        }

        for (i in itemList) {
            val v = ChatTitleDropItemView(anchor.context)
            contentView.addView(v)
            v.setViewItem(i) {
                dismiss()
            }
            v.setOnClickListener {
                dismiss()
            }
        }

        contentView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val width = anchor.context.resources.displayMetrics.widthPixels
        this.height = contentView.measuredHeight
        this.width = width


        this.contentView = contentView
        super.showAsDropDown(anchor)

        update()

        val anim = AnimationUtils.loadAnimation(anchor.context, R.anim.common_scroll_in)
        contentView.startAnimation(anim)
    }
}