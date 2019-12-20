package com.bcm.messenger.chats.group.live

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.bcm.messenger.common.utils.AppUtil

const val SIZE_SMALL = 0
const val SIZE_LARGE = 1
const val SIZE_FULLSCREEN = 2

open class BaseLiveFlowWindow(private val context: Context) {
    protected val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val dp10 = AppUtil.dp2Px(context.resources, 10)
    private val dp200 = AppUtil.dp2Px(context.resources, 200)
    private val dp54 = AppUtil.dp2Px(context.resources, 54)
    private val statusBarHeight = AppUtil.getStatusBarHeight(context)
    private val screenWidth = AppUtil.getRealScreenWidth()
    private val screenHeight = AppUtil.getScreenHeight(context)
    private var widthSeparator = screenWidth / 2 - 0
    private var heightSeparator = screenHeight / 2 - 0

    protected fun switchToSmallSize(rootView: View, width: Int, height: Int): WindowManager.LayoutParams {
        val multiply = height.toFloat() / width.toFloat()
        val windowWidth: Int
        val windowHeight: Int
        if (width < height) {
            windowHeight = dp200
            windowWidth = Math.min((windowHeight / multiply).toInt(), dp200)
        } else {
            windowWidth = dp200
            windowHeight = Math.min((windowWidth * multiply).toInt(), dp200)
        }
        val newParams = initWindowParams(windowWidth, windowHeight)
        newParams.x = newParams.x - dp10
        newParams.y = newParams.y + dp54
        newParams.flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        widthSeparator = screenWidth / 2 - windowWidth / 2
        heightSeparator = screenHeight / 2 - windowHeight / 2

        try {
            if (rootView.parent != null) {
                manager.updateViewLayout(rootView, newParams)
            } else {
                manager.addView(rootView, newParams)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return newParams
    }

    protected fun switchToFullscreen(rootView: View): WindowManager.LayoutParams {
        val newParams = initWindowParams(AppUtil.getRealScreenWidth(), AppUtil.getRealScreenHeight())
        newParams.flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        try {
            if (rootView.parent != null) {
                manager.updateViewLayout(rootView, newParams)
            } else {
                manager.addView(rootView, newParams)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return newParams
    }

    private fun initWindowParams(width: Int, height: Int): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams()
        params.width = width
        params.height = height
        params.type = WindowManager.LayoutParams.FIRST_SUB_WINDOW
        params.gravity = Gravity.TOP or Gravity.START

        val dm = DisplayMetrics()
        manager.defaultDisplay.getMetrics(dm)
        params.x = if (dm.widthPixels > dm.heightPixels) {
            dm.heightPixels - params.width
        } else {
            dm.widthPixels - params.width
        }
        params.y = statusBarHeight

        return params
    }

    protected fun enterFullScreenMode() {
        if (context is Activity) {
            context.window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            AppUtil.hideKeyboard(context)
        }
    }

    protected fun exitFullScreenMode() {
        if (context is Activity) {
            context.window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    }

    protected fun setActivityPortrait() {
        if (context is Activity) {
            context.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    protected fun setActivityLandscape() {
        if (context is Activity) {
            context.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
    }

    protected fun doResetAnim(rootView: View, currentX: Int, params: WindowManager.LayoutParams) {
        if (currentX < 0) {
            ValueAnimator.ofInt(0, -currentX).apply {
                val originX = params.x
                addUpdateListener {
                    params.x = originX + it.animatedValue as Int
                    manager.updateViewLayout(rootView, params)
                }
                duration = 500
            }.start()
        } else {
            ValueAnimator.ofInt(0, currentX - (AppUtil.getRealScreenWidth() - params.width)).apply {
                val originX = params.x
                addUpdateListener {
                    params.x = originX - it.animatedValue as Int
                    manager.updateViewLayout(rootView, params)
                }
                duration = 500
            }.start()
        }
    }

    protected fun stickyToCorner(rootView: View, currentX: Int, currentY: Int, params: WindowManager.LayoutParams) {
        when {
            currentX <= widthSeparator && currentY <= heightSeparator -> {
                AnimatorSet().apply {
                    play(ValueAnimator.ofInt(currentX, dp10).apply {
                        addUpdateListener {
                            params.x = it.animatedValue as Int
                            manager.updateViewLayout(rootView, params)
                    } }).with(ValueAnimator.ofInt(currentY, dp54 + statusBarHeight).apply {
                        addUpdateListener {
                            params.y = it.animatedValue as Int
                            manager.updateViewLayout(rootView, params)
                        }
                    })
                    duration = 300
                }.start()
            }
            currentX > widthSeparator && currentY <= heightSeparator -> {
                AnimatorSet().apply {
                    play(ValueAnimator.ofInt(currentX, screenWidth - dp10 - params.width).apply {
                        addUpdateListener {
                            params.x = it.animatedValue as Int
                            manager.updateViewLayout(rootView, params)
                        }
                    }).with(ValueAnimator.ofInt(currentY, dp54 + statusBarHeight).apply {
                        addUpdateListener {
                            params.y = it.animatedValue as Int
                            manager.updateViewLayout(rootView, params)
                        }
                    })
                    duration = 300
                }.start()
            }
            currentX <= widthSeparator && currentY > heightSeparator -> {
                AnimatorSet().apply {
                    play(ValueAnimator.ofInt(currentX, dp10).apply {
                        addUpdateListener {
                            params.x = it.animatedValue as Int
                            manager.updateViewLayout(rootView, params)
                        } }).with(ValueAnimator.ofInt(currentY, screenHeight - dp10 - params.height).apply {
                        addUpdateListener {
                            params.y = it.animatedValue as Int
                            manager.updateViewLayout(rootView, params)
                        }
                    })
                    duration = 300
                }.start()
            }
            currentX > widthSeparator / 2 && currentY > heightSeparator -> {
                AnimatorSet().apply {
                    play(ValueAnimator.ofInt(currentX, screenWidth - dp10 - params.width).apply {
                        addUpdateListener {
                            params.x = it.animatedValue as Int
                            manager.updateViewLayout(rootView, params)
                        }
                    }).with(ValueAnimator.ofInt(currentY, screenHeight - dp10 - params.height).apply {
                        addUpdateListener {
                            params.y = it.animatedValue as Int
                            manager.updateViewLayout(rootView, params)
                        }
                    })
                    duration = 300
                }.start()
            }
        }
    }
}