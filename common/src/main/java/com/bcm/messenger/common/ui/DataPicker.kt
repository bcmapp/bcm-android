package com.bcm.messenger.common.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.NumberPicker
import com.bcm.messenger.common.R

/**
 * Created by Kin on 2018/9/21
 */
class DataPicker @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : NumberPicker(context, attrs) {
    private var textColor = 0
    private var dividerColor = 0
    private var dividerHeight = 0f

    init {
        val array = context.obtainStyledAttributes(attrs, R.styleable.DataPicker)
        textColor = array.getColor(R.styleable.DataPicker_textColor, 0)
        dividerColor = array.getColor(R.styleable.DataPicker_dividerColor, 0)
        dividerHeight = array.getDimension(R.styleable.DataPicker_dividerHeight, 0f)
        array.recycle()

        if (dividerColor != 0) {
            setDividerColor()
        }

        if (dividerHeight != 0f) {
            setDividerHeight()
        }

        if (textColor != 0) {
            updateView()
        }
    }

    override fun addView(child: View?) {
        super.addView(child)
        updateView()
    }

    override fun addView(child: View?, index: Int) {
        super.addView(child, index)
        updateView()
    }

    override fun addView(child: View?, params: ViewGroup.LayoutParams?) {
        super.addView(child, params)
        updateView()
    }

    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
        super.addView(child, index, params)
        updateView()
    }

    override fun addView(child: View?, width: Int, height: Int) {
        super.addView(child, width, height)
        updateView()
    }

    private fun updateView() {
        try {
            val field = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint")
            if (field != null) {
                field.isAccessible = true
                (field.get(this) as Paint).color = textColor
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        for (i in 0 until childCount) {
            val view = getChildAt(i)
            if (view is EditText && textColor != 0) {
                view.setTextColor(textColor)
            }
        }
    }

    private fun setDividerColor() {
        try {
            val field = NumberPicker::class.java.getDeclaredField("mSelectionDivider")
            if (field != null) {
                field.isAccessible = true
                field.set(this, ColorDrawable(dividerColor))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setDividerHeight() {
        try {
            val field = NumberPicker::class.java.getDeclaredField("mSelectionDividerHeight")
            if (field != null) {
                field.isAccessible = true
                field.set(this, dividerHeight.toInt())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}