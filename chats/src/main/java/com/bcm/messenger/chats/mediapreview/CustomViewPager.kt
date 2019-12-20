package com.bcm.messenger.chats.mediapreview

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager

/**
 * Created by Kin on 2018/11/8
 */
class CustomViewPager @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewPager(context, attrs) {
    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        try {
            return super.onTouchEvent(ev)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        try {
            return super.onInterceptTouchEvent(ev)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}