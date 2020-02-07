package com.bcm.messenger.common.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import com.bcm.messenger.common.R
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.ui.emoji.EmojiEditText
import com.bcm.messenger.common.utils.getAttrColor
import com.bcm.messenger.common.utils.getDrawable
import com.bcm.messenger.utility.AppContextHolder

/**
 * Created by Kin on 2018/12/13
 */
class ClearButtonEditText @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : EmojiEditText(context, attrs, defStyleAttr) {

    private var clearDrawable = AppUtil.getDrawable(resources, R.drawable.common_input_clear_icon)
    private var drawableHeight = AppUtil.dp2Px(resources, 18)
    private var drawableWidth = AppUtil.dp2Px(resources, 18)
    private var drawablePadding = AppUtil.dp2Px(resources, 10)
    private var customPaddingEnd = AppUtil.dp2Px(resources, 10)
    private var tenDp = AppUtil.dp2Px(resources, 10)

    private var clearButtonShowed: Boolean? = null

    init {
        if (attrs != null) {
            loadAttrs(context, attrs)
        }
    }

    private fun loadAttrs(context: Context, attrs: AttributeSet) {
        val array = context.obtainStyledAttributes(attrs, R.styleable.ClearButtonEditText)
        val clearButtonShow = array.getBoolean(array.getIndex(R.styleable.ClearButtonEditText_showClearButton), false)
        clearDrawable = array.getDrawable(R.styleable.ClearButtonEditText_clearButtonResource)?: getDrawable(R.drawable.common_input_clear_icon)
        clearDrawable.setTint(AppContextHolder.APP_CONTEXT.getAttrColor(R.attr.common_text_third_color))
        drawableWidth = array.getDimension(R.styleable.ClearButtonEditText_clearButtonWidth, drawableWidth.toFloat()).toInt()
        drawableHeight = array.getDimension(R.styleable.ClearButtonEditText_clearButtonHeight, drawableHeight.toFloat()).toInt()
        drawablePadding = array.getDimension(R.styleable.ClearButtonEditText_clearButtonPadding, drawablePadding.toFloat()).toInt()
        customPaddingEnd = array.getDimension(R.styleable.ClearButtonEditText_paddingEnd, customPaddingEnd.toFloat()).toInt()
        if (clearButtonShow) {
            showClearButton()
        }else {
            hideClearButton()
        }
        array.recycle()
    }

    fun showClearButton() {
        if (clearButtonShowed == true) return
        clearDrawable.setBounds(0, 0, drawableWidth, drawableHeight)
        setCompoundDrawables(null, null, clearDrawable, null)
        compoundDrawablePadding = drawablePadding
        setPadding(paddingStart, paddingTop, customPaddingEnd, paddingBottom)
        clearButtonShowed = true
    }

    fun hideClearButton() {
        if (clearButtonShowed == false) return
        setCompoundDrawables(null, null, null, null)
        compoundDrawablePadding = 0
        setPadding(paddingStart, paddingTop, paddingStart, paddingBottom)
        clearButtonShowed = false
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (clearButtonShowed == true) {
            val x = event?.x ?: 0f
            if (x > width - paddingEnd - clearDrawable.intrinsicWidth - tenDp) {
                if (event?.action == MotionEvent.ACTION_UP) {
                    setText("")
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}