package com.bcm.messenger.common.ui.popup.bottompopup

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import com.bcm.messenger.common.R

/**
 * Created by bcm.social.01 on 2018/11/6.
 */
class BottomPopupCellView :RelativeLayout {
    private lateinit var itemText:TextView
    constructor(context: Context) : this(context, null) {}
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0) {}
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int):super(context, attrs, defStyleAttr){}

    override fun onFinishInflate() {
        super.onFinishInflate()
        itemText = findViewById(R.id.item_text)
    }

    fun setText(text:String, @ColorInt color:Int) {
        itemText.text = text
        itemText.setTextColor(color)
    }
}