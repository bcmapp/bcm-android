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
 * 可伸缩头部的Layout，基于LinearLayout
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
        /**
         * TopView的高度改变了
         *
         * @param height 当前高度
         */
        open fun onTopViewHeightChanged(height: Int, front: Int) {}

        /**
         * ScrollView滚动了
         */
        open fun onScrollViewScrolled(scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) {}
    }

    private var lastX = 0f // 初始X坐标
    private var lastY = 0f // 初始Y坐标

    private var touchEvent = false // 是否消耗触摸事件
    private var moveDirection = MOVE_NONE // 移动的方向
    private var topViewOriginHeight = 0 // 点击的时候TopView的初始高度
    private var moved = false
    private val scaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop // 移动误差的大小

    private var scrollView: BcmScrollView? = null // ScrollView
    private var isScrollViewReachTop = true // ScrollView是否在顶部
    private var clickToClose = false
    
    private lateinit var topView: View // TopView
    private var maxHeight = 0 // TopView的最大高度
    private var minHeight = 0

    private var callback: PullDownLayoutCallback? = null // 回调

    /**
     * 展开的动画
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
     * 隐藏的动画
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
                // 按下屏幕，记录当前的X Y坐标和TopView的高度，不拦截事件
                lastX = ev.rawX
                lastY = ev.rawY
                topViewOriginHeight = topView.height

                touchEvent = clickToClose && topViewOriginHeight > minHeight && ev.rawY > maxHeight
            }
            MotionEvent.ACTION_MOVE -> {
                // 检测到移动
                val disY = ev.rawY - lastY
                val absDisY = abs(disY)
                if (absDisY > scaledTouchSlop) {
                    // 移动距离大于误差
                    touchEvent = if (scrollView == null) {
                        // 没有ScrollView，总是拦截事件
                        true
                    } else {
                        when {
                            // TopView初始高度为0且ScrollView不在顶部，不拦截事件，交由ScrollView处理
                            topViewOriginHeight == minHeight && !isScrollViewReachTop -> false
                            // TopView初始高度不为0或者ScrollView在顶部且向下滑动，拦截事件，显示TopView
                            topViewOriginHeight > minHeight || (isScrollViewReachTop && disY > 0) -> true
                            // 其余情况都不拦截
                            else -> false
                        }
                    }
                }
            }
            // 手指离开，不拦截事件
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touchEvent = false
        }

        return touchEvent
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null || !::topView.isInitialized) return super.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 没有子View消费事件，这里强制消费
                moved = false
                touchEvent = true
            }
            MotionEvent.ACTION_MOVE -> {
                // 开始移动
                val scrollY = event.rawY - lastY
                val absScrollY = abs(scrollY)
                if (absScrollY >= scaledTouchSlop) {
                    moved = true
                    if (topViewOriginHeight == minHeight && !isScrollViewReachTop) {
                        // TopView初始高度为0且ScrollView不在顶部，不消费事件，交由ScrollView处理
                        touchEvent = false
                    } else if (topViewOriginHeight > minHeight || (isScrollViewReachTop && scrollY > 0) || topView.height > minHeight) {
                        // TopView初始高度大于0，或者ScrollView在顶部且向下滑动，或者TopView当前高度大于0，消费事件
                        touchEvent = true

                        // 判断移动方向
                        moveDirection = if (scrollY > 0) MOVE_DOWN else MOVE_UP
                        // 设置TopView的高度
                        topView.layoutParams = topView.layoutParams.apply {
                            var newHeight = topViewOriginHeight + scrollY.toInt()
                            if (newHeight > maxHeight) newHeight = maxHeight
                            if (newHeight < minHeight) newHeight = minHeight
                            height = newHeight

                            callback?.onTopViewHeightChanged(newHeight, moveDirection)
                        }
                    } else {
                        // 其他情况不拦截
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
                        // TopView当前高度大于100px且滑动方向向下，或者TopView高度大于最大高度-100px，播放展示动画
                        (topView.height > minHeight + 100 && moveDirection == MOVE_DOWN) || topView.height >= maxHeight - 100 -> expandAnim().start()
                        // TopView当前高度小于最大高度-100px且滑动方向向上，或者TopView高度小于100px，播放关闭动画
                        (topView.height < maxHeight - 100 && moveDirection == MOVE_UP) || topView.height <= minHeight + 100 -> hideAnim().start()
                    }
                    touchEvent = false
                }
                moveDirection = MOVE_NONE
            }
        }
        return touchEvent
    }

    /**
     * 设置ScrollView
     * 如果有ScrollView必须设置，否则ScrollView不能滚动
     *
     * @param scrollView 要设置的ScrollView
     */
    fun setScrollView(scrollView: BcmScrollView) {
        this.scrollView = scrollView
        this.scrollView?.setOnScrollChangeListener(object : NestedScrollView.OnScrollChangeListener {
            override fun onScrollChange(v: NestedScrollView?, scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) {
                isScrollViewReachTop = scrollY == 0
                callback?.onScrollViewScrolled(scrollX, scrollY, oldScrollX, oldScrollY)
            }
        })
    }

    /**
     * 设置需要下拉显示的顶部View
     *
     * @param topView 顶部View
     * @param maxHeight 下拉最大高度，单位px
     */
    fun setTopView(topView: View, maxHeight: Int, minHeight: Int) {
        this.topView = topView
        this.maxHeight = maxHeight
        this.minHeight = minHeight
    }

    /**
     * 设置回调
     *
     * @param callback 回调
     */
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