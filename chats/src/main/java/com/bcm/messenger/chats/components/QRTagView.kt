package com.bcm.messenger.chats.components

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import com.bcm.messenger.chats.R

/**
 * Custom QRCode view
 */
class QRTagView : androidx.constraintlayout.widget.ConstraintLayout {

    private lateinit var mDot: ImageView
    private lateinit var mBack: ImageView

    private var mAnimateOne: AnimatorSet? = null
    private var mAnimateTwo: AnimatorSet? = null
    private var mDetached: Boolean = false

    constructor(context: Context) : this(context, null) {}

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0) {}

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initView()
    }

    private fun initView() {
        View.inflate(context, R.layout.chats_qr_tag_layout, this)
        mDot = findViewById(R.id.qr_tag_dot)
        mBack = findViewById(R.id.qr_tag_back)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mDetached = true
        mAnimateOne?.cancel()
        mAnimateTwo?.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mDetached = false
        mAnimateOne?.start()
        mAnimateTwo?.start()
    }

    fun start(scale: Float, startDuration: Long, stopDuration: Long) {

        mDot.visibility = View.GONE
        mBack.visibility = View.GONE
        mAnimateOne?.cancel()
        mAnimateOne = AnimatorSet().apply {
            duration = 100
            playTogether(ObjectAnimator.ofFloat(mDot, "scaleX", 1.0f, scale),
                    ObjectAnimator.ofFloat(mDot, "scaleY", 1.0f, scale))
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animation: Animator?) {

                }

                override fun onAnimationEnd(animation: Animator?) {
                    mDot.visibility = View.VISIBLE
                    mBack.visibility = View.VISIBLE
                }

                override fun onAnimationCancel(animation: Animator?) {

                }

                override fun onAnimationStart(animation: Animator?) {

                }

            })

        }
        mAnimateOne?.start()

        mAnimateTwo?.cancel()
        mAnimateTwo = AnimatorSet().apply {
            duration = startDuration
            playTogether(
                    ObjectAnimator.ofFloat(mBack, "alpha", 1.0f, 0.0f),
                    ObjectAnimator.ofFloat(mBack, "scaleX", scale, 1.0f),
                    ObjectAnimator.ofFloat(mBack, "scaleY", scale, 1.0f)
            )
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animation: Animator?) {

                }

                override fun onAnimationEnd(animation: Animator?) {
                    if (!mDetached) {
                        animation?.resume()
                        animation?.startDelay = stopDuration
                        animation?.start()
                    }
                }

                override fun onAnimationCancel(animation: Animator?) {

                }

                override fun onAnimationStart(animation: Animator?) {

                }

            })

        }
        mAnimateTwo?.start()

    }
}