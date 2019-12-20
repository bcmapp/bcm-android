package com.bcm.messenger.chats.components

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.utils.AppUtil

/**
 * Created by Kin on 2018/12/19
 */
class LiveIconFlowWindow(activity: Activity, private val isGroupOwner: Boolean, private var isReplay: Boolean = false) {
    interface LiveIconFlowListener {
        fun onHide()
        fun onReplayHide()
        fun onMove(isMoving: Boolean)
        fun onDelete()
    }

    companion object {
        val TAG = "LiveIconFlowWindow"
    }

    private val manager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val rootView = LayoutInflater.from(activity).inflate(R.layout.chats_live_icon_flow, null) as FlowWindowCoverLayout
    private val videoIcon = rootView.findViewById<ImageView>(R.id.flow_video_icon)

    private var listener: LiveIconFlowListener? = null
    private var isMoving = false
    private var width = AppUtil.dp2Px(activity.resources, 48)
    private var height = AppUtil.dp2Px(activity.resources, 48)
    private var deleteSize = AppUtil.dp2Px(activity.resources, 100)
    private val params = WindowManager.LayoutParams()

    private val topLeftAnchorX = AppUtil.dp2Px(activity.resources, 10)
    private val topRightAnchorX = AppUtil.getRealScreenWidth() - width - AppUtil.dp2Px(activity.resources, 10)
    private val topAnchorY = AppUtil.getStatusBarHeight(activity) + AppUtil.dp2Px(activity.resources, 54)

    init {
        params.width = width
        params.height = height
        params.type = WindowManager.LayoutParams.FIRST_SUB_WINDOW
        params.gravity = Gravity.TOP or Gravity.START
        params.flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

        val dm = DisplayMetrics()
        manager.defaultDisplay.getMetrics(dm)
        params.x = topRightAnchorX
        params.y = topAnchorY

        rootView.enableDrag(true)
        rootView.setListener(object : FlowWindowCoverLayout.OnViewPositionChangedListener {
            override fun onViewPositionChanged(x: Int, y: Int) {
                params.x = x
                params.y = y
                manager.updateViewLayout(rootView, params)
                if (!isMoving) {
                    listener?.onMove(true)
                    isMoving = true
                }
            }

            override fun onViewPositionChangedEnd(x: Int, y: Int) {
                isMoving = false
                listener?.onMove(false)
                doResetAnim(x, y)
            }
        })
        rootView.setOnClickListener {
            hide()
        }
        if (isReplay) {
            videoIcon.setImageResource(R.drawable.chats_flow_stop_live_icon)
        }

        manager.addView(rootView, params)

        val translateX = ObjectAnimator.ofFloat(rootView, "translationX", params.width.toFloat(), 0.0f)
        translateX.duration = 500
        translateX.start()
    }


    private fun doResetAnim(currentX: Int, currentY: Int) {
        if (isGroupOwner && params.x + width / 2 < deleteSize && params.y + height / 2 > AppUtil.getRealScreenHeight() - deleteSize) {
            delete()
            return
        }
        val translateX = if (currentX > (AppUtil.getRealScreenWidth() - params.width) / 2) {
            ValueAnimator.ofInt(currentX, topRightAnchorX).apply {
                addUpdateListener {
                    params.x = it.animatedValue as Int
                    manager.updateViewLayout(rootView, params)
                }
                interpolator = LinearInterpolator()
                duration = 500
            }
        } else {
            ValueAnimator.ofInt(currentX, topLeftAnchorX).apply {
                addUpdateListener {
                    params.x = it.animatedValue as Int
                    manager.updateViewLayout(rootView, params)
                }
                interpolator = LinearInterpolator()
                duration = 500
            }
        }
        val translateY = ValueAnimator.ofInt(currentY, topAnchorY).apply {
            addUpdateListener {
                params.y = it.animatedValue as Int
                manager.updateViewLayout(rootView, params)
            }
            duration = 500
        }
        val set = AnimatorSet()
        set.playTogether(translateX, translateY)
        set.start()
    }

    fun hide(callback: Boolean = true) {
        if (rootView.parent != null) {
            manager.removeViewImmediate(rootView)
        }
        if (callback) {
            if (isReplay) {
                listener?.onReplayHide()
            } else {
                listener?.onHide()
            }
        }
    }

    fun setReplayMode() {
        videoIcon.setImageResource(R.drawable.chats_flow_stop_live_icon)
        isReplay = true
    }

    fun delete() {
        hide(false)
        listener?.onDelete()
    }

    fun setListener(listener: LiveIconFlowListener) {
        this.listener = listener
    }
}