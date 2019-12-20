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
 * Created by bcm.social.01 on 2019/1/27.
 */
class ChatTitleDropMenu: PopupWindow(){
    private val TAG = "PopupWindow"

    fun showAsDropDown(anchor: View, itemList:List<ChatTitleDropItem>) {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        isTouchable = true
        isFocusable = true
        isOutsideTouchable = true
        animationStyle = 0

        val contentView = LayoutInflater.from(anchor.context).inflate(R.layout.chats_title_bar_menu_layout, null) as LinearLayout
        contentView.setOnClickListener{
            dismiss()
        }

        for (i in itemList){
            val v = ChatTitleDropItemView(anchor.context)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(2, 0, 2, 0)
            v.layoutParams = params
            contentView.addView(v)
            v.setViewItem(i){
                dismiss()
            }
            v.setOnClickListener{
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