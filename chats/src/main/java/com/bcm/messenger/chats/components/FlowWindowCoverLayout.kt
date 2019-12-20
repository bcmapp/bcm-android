package com.bcm.messenger.chats.components

import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import android.util.AttributeSet
import android.view.MotionEvent

/**
 * Created by Kin on 2018/12/19
 */
class FlowWindowCoverLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr) {
    interface OnViewPositionChangedListener {
        fun onViewPositionChanged(x: Int, y: Int)
        fun onViewPositionChangedEnd(x: Int, y: Int)
    }

    private var listener: OnViewPositionChangedListener? = null
    private var enableDrag = false

    private var viewX = 0f
    private var viewY = 0f
    private var mTouchStartX = 0f
    private var mTouchStartY = 0f
    private var startTime = 0L

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return super.onTouchEvent(event)
        viewX = event.rawX
        viewY = event.rawY
        var isClick = false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                mTouchStartX = event.x
                mTouchStartY = event.y
                startTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_MOVE -> {
                updateViewPosition()
            }
            MotionEvent.ACTION_UP -> {
                isClick = System.currentTimeMillis() - startTime <= 100
                if (!isClick || !enableDrag)
                    viewPositionEnd()
                mTouchStartX = 0f
                mTouchStartY = 0f
            }
        }
        if (isClick) {
            performClick()
        }
        return true
    }

    private fun updateViewPosition() {
        listener?.onViewPositionChanged((viewX - mTouchStartX).toInt(), (viewY - mTouchStartY).toInt())
    }

    private fun viewPositionEnd() {
        listener?.onViewPositionChangedEnd((viewX - mTouchStartX).toInt(), (viewY - mTouchStartY).toInt())
    }

    fun setListener(listener: OnViewPositionChangedListener) {
        this.listener = listener
    }

    fun enableDrag(enable: Boolean) {
        this.enableDrag = enable
    }
}