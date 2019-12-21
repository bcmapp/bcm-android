package com.bcm.messenger.common.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import androidx.core.widget.NestedScrollView
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 *
 *
 * Created by Kin on 2019/6/28
 */

class PullDownLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr) {
    companion object {
        const val MOVE_NONE = 0
        const val MOVE_UP = 1
        const val MOVE_DOWN = 2

        private const val TAG = "PullDownLayout"
    }

    abstract class PullDownLayoutCallback {
        open fun onTopViewHeightChanged(height: Int, direction: Int) {}
        open fun onScrollViewScrolled(scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) {}
        open fun onTouchUp(direction: Int) {}
    }

    private var lastX = 0f
    private var lastY = 0f

    private var touchEvent = false
    private var moveDirection = MOVE_NONE
    private var topViewOriginHeight = 0
    private var moved = false
    private val scaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop //

    private var scrollView: BcmScrollView? = null
    private var recyclerView: BcmRecyclerView? = null
    private var isScrollViewReachTop = true
    private var clickToClose = false
    private var interceptTouch = false
    private var reboundSize = 100

    private lateinit var topView: View
    private var topViewMaxHeight = 0
    private var topViewMinHeight = 0

    private var callback: PullDownLayoutCallback? = null //

    /**
     *
     */
    private fun expandAnim(): AnimatorSet {
        return AnimatorSet().apply {
            play(ValueAnimator.ofInt(topView.height, topViewMaxHeight).apply {
                addUpdateListener {
                    topView.layoutParams = topView.layoutParams.apply {
                        height = it.animatedValue as Int
                        callback?.onTopViewHeightChanged(height, MOVE_DOWN)
                    }
                }
            })

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator?) {
                    scrollView?.interruptScroll = true
                    recyclerView?.interruptScroll = true
                    this@apply.removeAllListeners()
                }
            })
        }
    }

    /**
     *
     */
    private fun hideAnim(): AnimatorSet {
        return AnimatorSet().apply {
            play(ValueAnimator.ofInt(topView.height, topViewMinHeight).apply {
                addUpdateListener {
                    topView.layoutParams = topView.layoutParams.apply {
                        height = it.animatedValue as Int
                        callback?.onTopViewHeightChanged(height, MOVE_UP)
                    }
                }
            })

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    scrollView?.interruptScroll = false
                    recyclerView?.interruptScroll = false
                    this@apply.removeAllListeners()
                }
            })
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (ev == null || !::topView.isInitialized) return super.onInterceptTouchEvent(ev)

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = ev.rawX
                lastY = ev.rawY
                topViewOriginHeight = topView.layoutParams.height

                touchEvent = interceptTouch && topViewOriginHeight > topViewMinHeight && ev.rawY > topViewMaxHeight
            }
            MotionEvent.ACTION_MOVE -> {
                //
                val disY = ev.rawY - lastY
                val absDisY = abs(disY)
                if (absDisY > scaledTouchSlop) {
                    touchEvent = if (scrollView == null && recyclerView == null) {
                        true
                    } else {
                        when {
                            topViewOriginHeight == topViewMinHeight && !isScrollViewReachTop -> false
                            topViewOriginHeight > topViewMinHeight || (isScrollViewReachTop && disY > 0) -> true

                            else -> false
                        }
                    }
                }
            }
            //
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touchEvent = false
        }

        return touchEvent
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null || !::topView.isInitialized) return super.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                //
                moved = false
                touchEvent = true
            }
            MotionEvent.ACTION_MOVE -> {
                //
                val scrollY = event.rawY - lastY
                val absScrollY = abs(scrollY)
                if (absScrollY >= scaledTouchSlop) {
                    moved = true
                    if (topViewOriginHeight == topViewMinHeight && !isScrollViewReachTop) {
                        touchEvent = false
                    } else if (topViewOriginHeight > topViewMinHeight || (isScrollViewReachTop && scrollY > 0) || topView.height > topViewMinHeight) {
                        touchEvent = true

                        //
                        moveDirection = if (scrollY > 0) MOVE_DOWN else MOVE_UP
                        var newHeight = topViewOriginHeight + scrollY.toInt()
                        if (newHeight > topViewMaxHeight) newHeight = topViewMaxHeight
                        if (newHeight < topViewMinHeight) newHeight = topViewMinHeight
                        topView.layoutParams = topView.layoutParams.apply {
                            height = newHeight
                        }
//                        topView.layout(0, 0, topView.width, newHeight)
                        callback?.onTopViewHeightChanged(newHeight, moveDirection)
                    } else {
                        //
                        touchEvent = false
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (interceptTouch && topView.height > topViewMaxHeight - 50 && !moved && event.rawY > topViewMaxHeight) {
                    touchEvent = true
                    if (clickToClose) {
                        hideAnim().start()
                    }
                } else {
                    when {
                        (topView.height > topViewMinHeight + reboundSize && moveDirection == MOVE_DOWN) || topView.height >= topViewMaxHeight - reboundSize -> {
                            expandAnim().start()
                            moveDirection = MOVE_DOWN
                        }

                        (topView.height < topViewMaxHeight - reboundSize && moveDirection == MOVE_UP) || topView.height <= topViewMinHeight + reboundSize -> {
                            hideAnim().start()
                            moveDirection = MOVE_UP
                        }
                    }
                    touchEvent = false
                }
                moveDirection = MOVE_NONE
            }
        }
        return touchEvent
    }

    fun setScrollView(scrollView: BcmScrollView) {
        this.scrollView = scrollView
        this.scrollView?.setOnScrollChangeListener(object : NestedScrollView.OnScrollChangeListener {
            override fun onScrollChange(v: NestedScrollView?, scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) {
                isScrollViewReachTop = scrollY == 0
                callback?.onScrollViewScrolled(scrollX, scrollY, oldScrollX, oldScrollY)
            }
        })
    }

    fun setScrollView(scrollView: BcmRecyclerView) {
        this.recyclerView = scrollView
        this.recyclerView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var oldScrollX = 0
            private var oldScrollY = 0

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                isScrollViewReachTop = dy == 0
                callback?.onScrollViewScrolled(dx, dy, oldScrollX, oldScrollY)
                oldScrollX = dx
                oldScrollY = dy
            }
        })
    }

    fun setTopView(topView: View, maxHeight: Int, minHeight: Int) {
        this.topView = topView
        this.topViewMaxHeight = maxHeight
        this.topViewMinHeight = minHeight
    }


    fun setCallback(callback: PullDownLayoutCallback) {
        this.callback = callback
    }

    fun expandTopView() {
        if (this.topView.height == topViewMinHeight) {
            expandAnim().start()
        }
    }

    fun closeTopView() {
        if (this.topView.height > topViewMinHeight) {
            hideAnim().start()
        }
    }

    fun isTopViewExpanded() = this.topView.height > topViewMinHeight

    fun setClickToCloseWhenExpanded() {
        clickToClose = true
        interceptTouch = true
    }

    fun setInterceptTouch() {
        interceptTouch = true
    }

    fun setReboundSize(size: Int) {
        reboundSize = size
    }
}