package com.bcm.messenger.common.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import kotlin.math.abs

/**
 * Created by Kin on 2019/12/10
 */
class ConstraintPullDownLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr) {
    companion object {
        const val MOVE_NONE = 0
        const val MOVE_UP = 1
        const val MOVE_DOWN = 2

        private const val TAG = "ConstraintPullDownLayout"
    }

    abstract class PullDownLayoutCallback {
        open fun onTouchDown() {}

        /**
         * TopView的高度改变了
         *
         * @param height 当前高度
         */
        open fun onTopViewHeightChanged(height: Int, direction: Int) {}

        /**
         * ScrollView滚动了
         */
        open fun onScrollViewScrolled(scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) {}

        open fun onTouchUp(direction: Int) {}
    }

    private var lastX = 0f // 初始X坐标
    private var lastY = 0f // 初始Y坐标

    private var touchEvent = false // 是否消耗触摸事件
    private var moveDirection = MOVE_NONE // 移动的方向
    private var topViewOriginHeight = 0 // 点击的时候TopView的初始高度
    private var moved = false
    private val scaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop // 移动误差的大小

    private var scrollView: BcmScrollView? = null // ScrollView
    private var recyclerView: BcmRecyclerView? = null
    private var isScrollViewReachTop = true // ScrollView是否在顶部
    private var clickToClose = false
    private var interceptTouch = false
    private var needVibrate = true
    private var reboundSize = 100
    private var viewPagerState = ViewPager.SCROLL_STATE_IDLE

//    private lateinit var topView: View // TopView
    private var topViewCurrentHeight = 0
    private var topViewMaxHeight = 0 // TopView的最大高度
    private var topViewMinHeight = 0

    private var callback: PullDownLayoutCallback? = null // 回调

    /**
     * 展开的动画
     */
    private fun expandAnim(): AnimatorSet {
        return AnimatorSet().apply {
            play(ValueAnimator.ofInt(topViewCurrentHeight, topViewMaxHeight).apply {
                addUpdateListener {
                    topViewCurrentHeight = it.animatedValue as Int
                    callback?.onTopViewHeightChanged(topViewCurrentHeight, moveDirection)
                }
            })

            interpolator = DecelerateInterpolator()
            duration = 150

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator?) {
                    scrollView?.interruptScroll = true
                    recyclerView?.interruptScroll = true
                }

                override fun onAnimationEnd(animation: Animator?) {
                    callback?.onTopViewHeightChanged(topViewCurrentHeight, moveDirection)
                    moveDirection = MOVE_NONE
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
            play(ValueAnimator.ofInt(topViewCurrentHeight, topViewMinHeight).apply {
                addUpdateListener {
//                    ALog.i(TAG, "animated value = ${it.animatedValue as Int}")
                    topViewCurrentHeight = it.animatedValue as Int
                    callback?.onTopViewHeightChanged(topViewCurrentHeight, moveDirection)
                }
            })

            interpolator = DecelerateInterpolator()
            duration = 300

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    callback?.onTopViewHeightChanged(topViewCurrentHeight, moveDirection)
                    scrollView?.interruptScroll = false
                    recyclerView?.interruptScroll = false
                    moveDirection = MOVE_NONE
                    this@apply.removeAllListeners()
                }
            })
        }
    }

    fun onInterceptTouchEventFromOutside(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            lastX = ev.rawX
            lastY = ev.rawY
            topViewOriginHeight = topViewCurrentHeight

            return false
        }

        return onInterceptTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (viewPagerState != ViewPager.SCROLL_STATE_IDLE) return false
        if (ev == null) return super.onInterceptTouchEvent(ev)

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                // 按下屏幕，记录当前的X Y坐标和TopView的高度，不拦截事件
                lastX = ev.rawX
                lastY = ev.rawY
                topViewOriginHeight = topViewCurrentHeight

                touchEvent = interceptTouch && topViewOriginHeight > topViewMinHeight
            }
            MotionEvent.ACTION_MOVE -> {
                // 检测到移动
                val disY = ev.rawY - lastY
                val absDisY = abs(disY)
                if (absDisY > scaledTouchSlop) {
                    // 移动距离大于误差
                    touchEvent = if (scrollView == null && recyclerView == null) {
                        // 没有ScrollView，总是拦截事件
                        true
                    } else {
                        when {
                            // TopView初始高度为0且ScrollView不在顶部，不拦截事件，交由ScrollView处理
                            topViewOriginHeight == topViewMinHeight && !isScrollViewReachTop -> false
                            // TopView初始高度不为0或者ScrollView在顶部且向下滑动，拦截事件，显示TopView
                            topViewOriginHeight > topViewMinHeight || (isScrollViewReachTop && disY > 0) -> true
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
        if (viewPagerState != ViewPager.SCROLL_STATE_IDLE) return false
        if (event == null) return super.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 没有子View消费事件，这里强制消费
                moved = false
                touchEvent = true
                callback?.onTouchDown()
            }
            MotionEvent.ACTION_MOVE -> {
                // 开始移动
                val scrollY = event.rawY - lastY
                val absScrollY = abs(scrollY)
                if (absScrollY >= scaledTouchSlop) {
                    moved = true
                    if (topViewOriginHeight == topViewMinHeight && !isScrollViewReachTop) {
                        // TopView初始高度为0且ScrollView不在顶部，不消费事件，交由ScrollView处理
                        touchEvent = false
                    } else if (topViewOriginHeight > topViewMinHeight || (isScrollViewReachTop && scrollY > 0) || topViewCurrentHeight > topViewMinHeight) {
                        // TopView初始高度大于0，或者ScrollView在顶部且向下滑动，或者TopView当前高度大于0，消费事件
                        touchEvent = true

                        // 判断移动方向
                        if (moveDirection == MOVE_NONE) {
                            moveDirection = if (scrollY > 0) MOVE_DOWN else MOVE_UP
                        }
                        // 设置TopView的高度
                        var newHeight = topViewOriginHeight + scrollY.toInt()
                        if (newHeight > topViewMaxHeight) newHeight = topViewMaxHeight
                        if (newHeight < topViewMinHeight) newHeight = topViewMinHeight
                        topViewCurrentHeight = newHeight

                        if (scrollY > 0) {
                            if (newHeight >= reboundSize && newHeight != topViewMaxHeight && needVibrate) {
                                vibrate()
                                needVibrate = false
                            }
                        } else {
                            if (newHeight <= topViewMaxHeight - reboundSize && newHeight != 0 && needVibrate) {
//                                vibrate()
                                needVibrate = false
                            }
                        }

                        callback?.onTopViewHeightChanged(newHeight, moveDirection)
                    } else {
                        // 其他情况不拦截
                        touchEvent = false
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                var needCallback = true
                if (interceptTouch && topViewCurrentHeight > topViewMaxHeight - 50 && !moved && event.rawY > topViewMaxHeight) {
                    touchEvent = true
                    if (clickToClose) {
                        hideAnim().start()
                    }
                } else {
                    when {
                        // TopView当前高度大于100px且滑动方向向下，或者TopView高度大于最大高度-100px，播放展示动画
                        (topViewCurrentHeight > topViewMinHeight + reboundSize && moveDirection == MOVE_DOWN) || topViewCurrentHeight >= topViewMaxHeight - reboundSize -> {
                            expandAnim().start()
//                            moveDirection = MOVE_DOWN
                            needCallback = moveDirection == MOVE_DOWN
                        }
                        // TopView当前高度小于最大高度-100px且滑动方向向上，或者TopView高度小于100px，播放关闭动画
                        (topViewCurrentHeight < topViewMaxHeight - reboundSize && moveDirection == MOVE_UP) || topViewCurrentHeight <= topViewMinHeight + reboundSize -> {
                            hideAnim().start()
//                            moveDirection = MOVE_UP
                            needCallback = moveDirection == MOVE_UP
                        }
                    }
                    touchEvent = false
                }
                if (needCallback && topViewOriginHeight != topViewCurrentHeight) {
                    callback?.onTouchUp(moveDirection)
                }
                needVibrate = true
//                moveDirection = MOVE_NONE
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
     * 设置RecyclerView
     * 如果有RecyclerView必须设置，否则RecyclerView不能滚动
     *
     * @param scrollView 要设置的RecyclerView
     */
    fun setScrollView(scrollView: BcmRecyclerView) {
        this.recyclerView = scrollView
        this.recyclerView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var oldScrollX = 0
            private var oldScrollY = 0

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                callback?.onScrollViewScrolled(dx, dy, oldScrollX, oldScrollY)
                oldScrollX = dx
                oldScrollY = dy
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    isScrollViewReachTop = !recyclerView.canScrollVertically(-1)
                }
            }
        })
    }

    fun resetReachTop() {
        isScrollViewReachTop = true
    }

    /**
     * 设置需要下拉显示的顶部View
     *
     * @param maxHeight 下拉最大高度，单位px
     */
    fun setTopViewSize(maxHeight: Int, minHeight: Int) {
        this.topViewMaxHeight = maxHeight
        this.topViewMinHeight = minHeight
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
        if (topViewCurrentHeight == topViewMinHeight) {
            expandAnim().start()
        }
    }

    fun expandTopViewAndCallback() {
        if (topViewCurrentHeight == topViewMinHeight) {
            expandAnim().start()
            callback?.onTouchUp(MOVE_DOWN)
        }
    }

    fun closeTopView() {
        if (topViewCurrentHeight > topViewMinHeight) {
            hideAnim().start()
        }
    }

    fun closeTopViewAndCallback() {
        if (topViewCurrentHeight > topViewMinHeight) {
            hideAnim().start()
            callback?.onTouchUp(MOVE_UP)
        }
    }

    fun isTopViewExpanded() = topViewCurrentHeight > topViewMinHeight

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

    fun setViewPagerState(state: Int) {
        viewPagerState = state
    }

    private fun vibrate() {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(50)
        }
    }
}