package com.bcm.messenger.chats.components

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatTextView

/**
 * Created by bcm.social.01 on 2019/1/29.
 */
class MarqueeTextView: AppCompatTextView {
    constructor(context: Context) : super(context){
        init()
    }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs){
        init()
    }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr){
        init()
    }

    private fun init() {
        setSingleLine()
        ellipsize = TextUtils.TruncateAt.MARQUEE
        marqueeRepeatLimit = -1
    }

    override fun isFocused(): Boolean {
        return visibility == View.VISIBLE
    }
}