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
import kotlin.math.abs

/**
 * 
 *
 * Created by Kin on 2019/6/28
 */

class PullDownLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : LinearLayout(context, attrs, defStyleAttr) {
    companion object {
        const val MOVE_NONE = 0
        const val MOVE_UP = 1
        const val MOVE_DOWN = 2

        private const val TAG = "PullDownLayout"
    }

    abstract class PullDownLayoutCallback {
       
        open fun onTopViewHeightChanged(height: Int, front: Int) {}

        /**
         * 
         */
        open fun onScrollViewScrolled(scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) {}
    }

    private var lastX = 0f // 
    private var lastY = 0f // 

    private var touchEvent = false // 
    private var moveDirection = MOVE_NONE // 
    private var topViewOriginHeight = 0 // 
    private var moved = false
    private val scaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop // 

    private var scrollView: BcmScrollView? = null // ScrollView
    private var isScrollViewReachTop = true // 
    private var clickToClose = false
    
    private lateinit var topView: View // TopView
    private var maxHeight = 0 // 
    private var minHeight = 0

    private var callback: PullDownLayoutCallback? = null // 

    /**
     * 
     */
    private fun expandAnim(): AnimatorSet {
        return AnimatorSet().apply {
            play(ValueAnimator.ofInt(topView.height, maxHeight).apply {
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
            play(ValueAnimator.ofInt(topView.height, minHeight).apply {
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
                topViewOriginHeight = topView.height

                touchEvent = clickToClose && topViewOriginHeight > minHeight && ev.rawY > maxHeight
            }
            MotionEvent.ACTION_MOVE -> {
                // 
                val disY = ev.rawY - lastY
                val absDisY = abs(disY)
                if (absDisY > scaledTouchSlop) {
                    // 
                    touchEvent = if (scrollView == null) {
                        // 
                        true
                    } else {
                        when {
                            // 
                            topViewOriginHeight == minHeight && !isScrollViewReachTop -> false
                            // 
                            topViewOriginHeight > minHeight || (isScrollViewReachTop && disY > 0) -> true
                            // 
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
                    if (topViewOriginHeight == minHeight && !isScrollViewReachTop) {
                        // 
                        touchEvent = false
                    } else if (topViewOriginHeight > minHeight || (isScrollViewReachTop && scrollY > 0) || topView.height > minHeight) {
                        // 
                        touchEvent = true

                        // 
                        moveDirection = if (scrollY > 0) MOVE_DOWN else MOVE_UP
                        // 
                        topView.layoutParams = topView.layoutParams.apply {
                            var newHeight = topViewOriginHeight + scrollY.toInt()
                            if (newHeight > maxHeight) newHeight = maxHeight
                            if (newHeight < minHeight) newHeight = minHeight
                            height = newHeight

                            callback?.onTopViewHeightChanged(newHeight, moveDirection)
                        }
                    } else {
                        // 
                        touchEvent = false
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (clickToClose && topView.height > maxHeight - 50 && !moved && event.rawY > maxHeight) {
                    touchEvent = true
                    hideAnim().start()
                } else {
                    when {
                        //
                        (topView.height > minHeight + 100 && moveDirection == MOVE_DOWN) || topView.height >= maxHeight - 100 -> expandAnim().start()
                        // 
                        (topView.height < maxHeight - 100 && moveDirection == MOVE_UP) || topView.height <= minHeight + 100 -> hideAnim().start()
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

    
    fun setTopView(topView: View, maxHeight: Int, minHeight: Int) {
        this.topView = topView
        this.maxHeight = maxHeight
        this.minHeight = minHeight
    }

   
    fun setCallback(callback: PullDownLayoutCallback) {
        this.callback = callback
    }

    fun expandTopView() {
        if (this.topView.height == minHeight) {
            expandAnim().start()
        }
    }

    fun closeTopView() {
        if (this.topView.height > minHeight) {
            hideAnim().start()
        }
    }

    fun isTopViewExpanded() = this.topView.height > minHeight

    fun setClickToCloseWhenExpanded() {
        clickToClose = true
    }
}