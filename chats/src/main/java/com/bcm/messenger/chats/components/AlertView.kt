package com.bcm.messenger.chats.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.TargetApi
import android.content.Context
import android.os.Build.VERSION_CODES
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.privatechat.safety.CountDownCircleView

/**
 * Chat message alert view.
 * E.g. send failed
 */
class AlertView : LinearLayout {

    private lateinit var failedIndicator: ImageView
    private lateinit var unreadIndicator: ImageView
    private lateinit var notificationIndicator: ImageView
    private lateinit var notificationDotIndicator: View
    private lateinit var expirationTimer: CountDownCircleView

    private lateinit var hideAnim: AnimatorSet

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(attrs)
    }

    @TargetApi(VERSION_CODES.HONEYCOMB)
    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        initialize(attrs)
    }

    private fun initialize(attrs: AttributeSet?) {
        View.inflate(context, R.layout.chats_alert_view, this)

        failedIndicator = findViewById(R.id.sms_failed_indicator)
        unreadIndicator = findViewById(R.id.unread_indicator)
        notificationDotIndicator = findViewById(R.id.notification_unread_indicator)
        notificationIndicator = findViewById(R.id.notification_indicator)
        expirationTimer = findViewById(R.id.expiration_indicator)

    }


    fun setNone() {
        this.failedIndicator.visibility = View.GONE
        this.unreadIndicator.visibility = View.GONE
        this.notificationIndicator.visibility = View.GONE
        this.notificationDotIndicator.visibility = View.GONE
        this.expirationTimer.visibility = View.GONE
    }


    fun setFailed() {
        this.visibility = View.VISIBLE
        failedIndicator.visibility = View.VISIBLE
        notificationDotIndicator.visibility = View.GONE
    }


    fun clearUnreadCount() {
        unreadIndicator.visibility = View.GONE
        notificationDotIndicator.visibility = View.GONE
    }


    fun setUnread(unread: Int, animate: Boolean) {
        val unreadView = getUnreadView()

        if (unread <= 0) {
            if (unreadView.visibility == View.VISIBLE && animate) {
                hideAnim.start()
            } else {
                unreadView.visibility = View.GONE
            }
        } else {
            hideAnim.cancel()
            unreadView.visibility = View.VISIBLE
        }

    }

    fun initHideAnim() {
        hideAnim = AnimatorSet().apply {
            val unreadView = getUnreadView()

            duration = 500
            play(ObjectAnimator.ofFloat(unreadView, "scaleX", 1f, 2f))
                    .with(ObjectAnimator.ofFloat(unreadView, "scaleY", 1f, 2f))
                    .with(ObjectAnimator.ofFloat(unreadView, "alpha", 1f, 0f))
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    unreadView.scaleX = 1f
                    unreadView.scaleY = 1f
                    unreadView.alpha = 1f
                    unreadView.visibility = View.GONE
                }
            })
        }
    }

    private fun getUnreadView(): View {
        return if (notificationIndicator.visibility == View.VISIBLE) {
            unreadIndicator.visibility = View.GONE
            notificationDotIndicator
        } else {
            notificationDotIndicator.visibility = View.GONE
            unreadIndicator
        }
    }


    fun setNotificationAlert(unreadCount: Int, showNotificationAlert: Boolean, animate: Boolean) {

        if (showNotificationAlert) {
            notificationIndicator.visibility = View.VISIBLE

        } else {
            notificationIndicator.visibility = View.GONE

        }

        initHideAnim()
        setUnread(unreadCount, animate)

    }


    fun setExpirationVisible(visible: Boolean) {
        if (visible) {
            this.visibility = View.VISIBLE
            expirationTimer.visibility = View.VISIBLE
        } else {
            expirationTimer.visibility = View.GONE
        }
    }

    fun setExpirationTimer(started: Long, expiresIn: Long) {
        expirationTimer.start(started, expiresIn)
    }

    fun setExpirationColor(@ColorRes color: Int) {
        expirationTimer.setColor(color)
    }

    fun stopExpiration() {
        expirationTimer.stop()
    }
}
