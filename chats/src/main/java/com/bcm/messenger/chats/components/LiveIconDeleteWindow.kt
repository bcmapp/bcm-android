package com.bcm.messenger.chats.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.utils.AppUtil

/**
 * Created by zjl on 2019/1/8
 */
class LiveIconDeleteWindow(activity: Activity) {
    interface LiveIconDeleteListener {
        fun onHide()
    }

    private val manager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val rootView = LayoutInflater.from(activity).inflate(R.layout.chats_live_icon_delete_flow, null) as ConstraintLayout
    private var listener: LiveIconDeleteListener? = null
    private val height = AppUtil.dp2Px(activity.resources, 120)

    private val showAnim = AnimatorSet().apply {
        duration = 300
        play(ObjectAnimator.ofFloat(rootView, "translationX", -height.toFloat(), 0f))
                .with(ObjectAnimator.ofFloat(rootView, "translationY", height.toFloat(), 0f))
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                rootView.visibility = View.VISIBLE
            }
        })
    }

    private val hideAnim = AnimatorSet().apply {
        duration = 300
        play(ObjectAnimator.ofFloat(rootView, "translationX", 0f, -height.toFloat()))
                .with(ObjectAnimator.ofFloat(rootView, "translationY", 0f, height.toFloat()))
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                rootView.visibility = View.GONE
            }
        })
    }

    init {
        val params = WindowManager.LayoutParams()
        params.width = height
        params.height = height
        params.type = WindowManager.LayoutParams.FIRST_SUB_WINDOW
        params.gravity = Gravity.TOP or Gravity.START
        params.flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

        val dm = DisplayMetrics()
        manager.defaultDisplay.getMetrics(dm)
        params.x = 0
        params.y = AppUtil.dp2Px(activity.resources, AppUtil.getRealScreenHeight() - height)

        manager.addView(rootView, params)
        rootView.visibility = View.GONE
    }

    fun show() {
        showAnim.start()
    }

    fun hide() {
        hideAnim.start()
    }

    fun dismiss(callback: Boolean = true) {
        if (rootView.parent != null) {
            manager.removeViewImmediate(rootView)
        }
        if (callback) {
            listener?.onHide()
        }
    }

    fun setListener(listener: LiveIconDeleteListener) {
        this.listener = listener
    }
}