package com.bcm.messenger.common.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager

/**
 * 
 * Created by zjl on 2018/3/2.
 */
class NoScrollViewPager : ViewPager {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    var isSlidingEnable = true

    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        return isSlidingEnable && super.onTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return isSlidingEnable && super.onInterceptTouchEvent(ev)
    }
}