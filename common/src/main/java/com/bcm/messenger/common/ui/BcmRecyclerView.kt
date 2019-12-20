package com.bcm.messenger.common.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView

/**
 * Created by Kin on 2019/12/9
 */
class BcmRecyclerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : RecyclerView(context, attrs, defStyleAttr) {
    var interruptScroll = false

    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        if (!interruptScroll) return super.onTouchEvent(ev)
        return interruptScroll
    }
}