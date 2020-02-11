package com.bcm.messenger.common.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.MotionEvent
import com.bcm.messenger.common.R
import com.bcm.messenger.common.ui.emoji.EmojiEditText
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getAttrColor
import com.bcm.messenger.common.utils.getDrawable

/**
 * Created by Kin on 2018/12/13
 */
class ClearButtonEditText @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : EmojiEditText(context, attrs, defStyleAttr) {

    private var clearDrawable = getDrawable(R.drawable.common_input_clear_icon)
    private var drawableHeight = 18.dp2Px()
    private var drawableWidth = 18.dp2Px()
    private var drawablePadding = 10.dp2Px()
    private var customPaddingEnd = 10.dp2Px()
    private var tenDp = 10.dp2Px()

    private var clearButtonShowed: Boolean? = null

    private val textWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            if (s == null || s.isEmpty()) {
                hideClearButton()
            } else {
                showClearButton()
            }
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        }
    }

    init {
        if (attrs != null) {
            loadAttrs(context, attrs)
        }
    }

    private fun loadAttrs(context: Context, attrs: AttributeSet) {
        val array = context.obtainStyledAttributes(attrs, R.styleable.ClearButtonEditText)
        val clearButtonShow = array.getBoolean(array.getIndex(R.styleable.ClearButtonEditText_showClearButton), false)
        clearDrawable = array.getDrawable(R.styleable.ClearButtonEditText_clearButtonResource) ?: getDrawable(R.drawable.common_input_clear_icon)
        clearDrawable.setTint(context.getAttrColor(R.attr.common_icon_color_grey))
        drawableWidth = array.getDimension(R.styleable.ClearButtonEditText_clearButtonWidth, drawableWidth.toFloat()).toInt()
        drawableHeight = array.getDimension(R.styleable.ClearButtonEditText_clearButtonHeight, drawableHeight.toFloat()).toInt()
        drawablePadding = array.getDimension(R.styleable.ClearButtonEditText_clearButtonPadding, drawablePadding.toFloat()).toInt()
        customPaddingEnd = array.getDimension(R.styleable.ClearButtonEditText_paddingEnd, customPaddingEnd.toFloat()).toInt()
        if (clearButtonShow) {
            showClearButton()
        } else {
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

        addTextChangedListener(textWatcher)
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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeTextChangedListener(textWatcher)
    }
}