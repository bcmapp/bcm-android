package com.bcm.messenger.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager
import kotlin.math.abs

/**
 * Created by Kin on 2019/12/31
 */
class HomeViewPager @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewPager(context, attrs) {
    interface DragListener {
        fun onDragVertically(ev: MotionEvent?): Boolean
        fun onInterceptEvent(ev: MotionEvent?): Boolean
    }

    private var xDistance = 0f
    private var yDistance = 0f
    private var xLast = 0f
    private var yLast = 0f

    private var intercept = false

    var listener: DragListener? = null

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return if (listener?.onInterceptEvent(ev) == true) {
            true
        } else {
            super.onInterceptTouchEvent(ev)
        }
    }

    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        if (!intercept) {
            when (ev?.action) {
                MotionEvent.ACTION_DOWN -> {
                    xDistance = 0f
                    yDistance = 0f
                    xLast = ev.x
                    yLast = ev.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val curX = ev.x
                    val curY = ev.y

                    xDistance += abs(curX - xLast)
                    yDistance += abs(curY - yLast)
                    xLast = curX
                    yLast = curY

                    intercept = xDistance < yDistance
                }
            }
        } else {
            intercept = listener?.onDragVertically(ev) ?: false
        }

        return if (intercept) {
            true
        } else {
            super.onTouchEvent(ev)
        }
    }
}