package com.bcm.messenger.common.ui

import android.content.Context
import androidx.core.widget.NestedScrollView
import android.util.AttributeSet
import android.view.MotionEvent

/**
 * Created by Kin on 2019/7/2
 */
class BcmScrollView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : NestedScrollView(context, attrs, defStyleAttr) {
    var interruptScroll = false

    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        if (!interruptScroll) return super.onTouchEvent(ev)
        return interruptScroll
    }
}