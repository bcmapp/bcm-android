package com.bcm.messenger.chats.mediapreview

import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.customview.widget.ViewDragHelper

/**
 * 具有下拉功能的ConstraintLayout
 * Created by Kin on 2018/11/5
 */
class DragLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr) {
    interface DragLayoutListener {
        fun onRelease()
        fun onPositionChanged(percentage: Float)
        fun canDrag(): Boolean
    }

    private lateinit var mDragHelper: ViewDragHelper
    private var dragListener: DragLayoutListener? = null

    private var mDragScale = true
    private var finalLeft = 0
    private var finalTop = 0
    private var dataType = 0

    private val customCallback = object : ViewDragHelper.Callback() {
        private var mNeedDrag = false
        private var mNeedRelease = false

        override fun tryCaptureView(p0: View, p1: Int): Boolean {
            return true
        }

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int {
            return if (mNeedDrag) left else 0
        }

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int {
            var topCopy = top
            if (mNeedDrag) return top
            if (top < 0) topCopy = 0
            else if (top > 300) mNeedDrag = true
            return topCopy
        }

        override fun onViewPositionChanged(changedView: View, left: Int, top: Int, dx: Int, dy: Int) {
            super.onViewPositionChanged(changedView, left, top, dx, dy)
            mNeedRelease = top > height * 0.1

            val present = 1 - top.toFloat() / height
            dragListener?.onPositionChanged(present)

            val maxScale = Math.min(present, 1f)
            val minScale = Math.max(0.2f, maxScale)
            changedView.scaleX = minScale
            changedView.scaleY = minScale
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            super.onViewReleased(releasedChild, xvel, yvel)

            if (mNeedRelease) {
                dragListener?.onRelease()
            } else {
                mNeedDrag = false
                mDragHelper.settleCapturedViewAt(finalLeft, finalTop)
                if (mDragScale) {
                    releasedChild.scaleX = 1f
                    releasedChild.scaleY = 1f
                }
                invalidate()
            }
        }

        override fun getViewVerticalDragRange(child: View): Int {
            return height / 2
        }
    }

    init {
        mDragHelper = ViewDragHelper.create(this, 1f, customCallback)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        when (dataType) {
            com.bcm.messenger.chats.mediapreview.bean.MEDIA_TYPE_IMAGE -> {
                finalLeft = getChildAt(1).left
                finalTop = getChildAt(1).top
            }
            com.bcm.messenger.chats.mediapreview.bean.MEDIA_TYPE_VIDEO -> {
                finalLeft = getChildAt(0).left
                finalTop = getChildAt(0).top
            }
            else -> {
                finalLeft = left
                finalTop = top
            }
        }
    }

    override fun computeScroll() {
        if (mDragHelper.continueSettling(true)) {
            invalidate()
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (ev == null) return false
        return try {
            if (ev.pointerCount > 1 || dragListener?.canDrag() != true) {
                return super.onInterceptTouchEvent(ev)
            }
            mDragHelper.shouldInterceptTouchEvent(ev)
        } catch (e: Exception) {
            false
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return false
        return try {
            mDragHelper.processTouchEvent(event)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun setDataType(type: Int) {
        dataType = type
    }

    fun setReleaseListener(listener: DragLayoutListener) {
        this.dragListener = listener
    }
}