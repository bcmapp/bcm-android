package com.bcm.messenger.chats.components.popupwindow

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.bcm.messenger.chats.R

import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.utility.logger.ALog
import java.lang.ref.WeakReference

/**
 * Created by zjl on 2018/9/12.
 */
object ChooseItemPopupWindow {

    fun createPopup(context: Context, anchorView: View, list: ArrayList<Pair<String, ChooseItemPopupWindow.Listener?>>): ChooseItemPopupWindow {
        val popupWindow = PopupWindow(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val contentView = LayoutInflater.from(context).inflate(R.layout.chats_choose_item_popup, null, false)
        val chatChooseLayout = contentView.findViewById<LinearLayout>(R.id.chats_choose_layout)
        for ((index, item) in list.withIndex()) {
            val tv = TextView(context)
            val layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            tv.layoutParams = layoutParams
            tv.setPadding(AppUtil.dp2Px(context.resources, 15), AppUtil.dp2Px(context.resources, 5), AppUtil.dp2Px(context.resources, 15), AppUtil.dp2Px(context.resources, 5))
            tv.text = item.first
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            tv.setTextColor(AppUtil.getColor(context.resources, R.color.common_color_white))
            tv.setOnClickListener {
                item.second?.onClick()
                popupWindow.dismiss()
            }

            chatChooseLayout.addView(tv)
            if (index < list.size - 1) {
                val v = View(context)
                v.setBackgroundResource(R.color.chats_text_interval)
                val layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                layoutParams.width = AppUtil.dp2Px(context.resources, 1)
                layoutParams.height = AppUtil.dp2Px(context.resources, 33)
                v.layoutParams = layoutParams
                chatChooseLayout.addView(v)
            }
        }
        popupWindow.contentView = contentView
        popupWindow.isFocusable = true
        popupWindow.isOutsideTouchable = true
        popupWindow.isTouchable = true
        popupWindow.setBackgroundDrawable(BitmapDrawable())
        val locations = IntArray(2)
        anchorView.getLocationOnScreen(locations)
        var x = locations[0]
        if (anchorView.width < contentView.width) {
            val l = contentView.width - anchorView.width
            x -= l
        }

        if (locations[1] > getStatusHeight(context) + AppUtil.dp2Px(context.resources, 75)) {
            popupWindow.showAtLocation(anchorView, Gravity.NO_GRAVITY, x, locations[1] - AppUtil.dp2Px(context.resources, 45))
        } else {
            popupWindow.showAtLocation(anchorView, Gravity.NO_GRAVITY, x, locations[1] + anchorView.height + AppUtil.dp2Px(context.resources, 10))
        }
        return this
    }

    interface Listener {
        fun onClick()
    }

    private fun getStatusHeight(context: Context): Int {
        var result = 0
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = context.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    interface Builder {
        fun addItem(text: String, listener: ChooseItemPopupWindow.Listener?): Builder
        fun setAnchorView(view: View): Builder
        fun build(): ChooseItemPopupWindow?
    }
}

class ChooseItemBuilder : ChooseItemPopupWindow.Builder {
    private val TAG = "ChooseItemBuilder"
    val list = ArrayList<Pair<String, ChooseItemPopupWindow.Listener?>>()
    private var anchorView: WeakReference<View>? = null

    companion object {
        fun Builder(anchorView: View): ChooseItemBuilder {
            return ChooseItemBuilder(anchorView)
        }

        fun Builder(): ChooseItemBuilder {
            return ChooseItemBuilder()
        }
    }

    constructor()

    constructor(anchorView: View) {
        this.anchorView = WeakReference(anchorView)
    }

    override fun addItem(text: String, listener: ChooseItemPopupWindow.Listener?): ChooseItemPopupWindow.Builder {
        list.add(Pair(text, listener))
        return this
    }

    override fun setAnchorView(view: View): ChooseItemPopupWindow.Builder {

        this.anchorView = WeakReference(view)
        return this
    }

    override fun build(): ChooseItemPopupWindow? {
        return if (anchorView != null && anchorView!!.get() != null) {
            ChooseItemPopupWindow.createPopup(anchorView!!.get()!!.context, anchorView!!.get()!!, list)
        } else {
            ALog.e(TAG, "context is null or anchorView is null")
            null
        }
    }
}