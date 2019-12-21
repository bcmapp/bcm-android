package com.bcm.messenger.common.ui

import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import androidx.constraintlayout.widget.ConstraintLayout
import com.bcm.messenger.common.R
import com.bcm.messenger.utility.logger.ALog
import kotlinx.android.synthetic.main.common_loading_view.view.*

/**
 * Created by Kin on 2019/7/11
 */
class CommonLoadingView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr) {
    companion object {
        private const val SIZE_SMALL = 1
        private const val SIZE_MIDDLE = 2
        private const val SIZE_LARGE = 3

        private const val COLOR_BLACK = 1
        private const val COLOR_WHITE = 2
    }

    private val anim: AnimatorSet

    init {
        inflate(context, R.layout.common_loading_view, this)

        val array = context.obtainStyledAttributes(attrs, R.styleable.CommonLoadingView)

        val size = array.getInt(R.styleable.CommonLoadingView_circle_size, SIZE_MIDDLE)
        val color = array.getInt(R.styleable.CommonLoadingView_circle_color, COLOR_BLACK)
        val isAutoStart = array.getBoolean(R.styleable.CommonLoadingView_auto_start, false)

        array.recycle()

        when (size) {
            SIZE_SMALL -> {
                loading_inner.setImageResource(R.drawable.common_loading_inner_black_small)
                loading_outer.setImageResource(R.drawable.common_loading_outer_black_small)
            }
            SIZE_MIDDLE -> {
                if (color == COLOR_BLACK) {
                    loading_inner.setImageResource(R.drawable.common_loading_inner_black_middle)
                    loading_outer.setImageResource(R.drawable.common_loading_outer_black_middle)
                } else {
                    loading_inner.setImageResource(R.drawable.common_loading_inner_white_middle)
                    loading_outer.setImageResource(R.drawable.common_loading_outer_white_middle)
                }
            }
            SIZE_LARGE -> {
                if (color == COLOR_BLACK) {
                    loading_inner.setImageResource(R.drawable.common_loading_inner_black_large)
                    loading_outer.setImageResource(R.drawable.common_loading_outer_black_large)
                } else {
                    loading_inner.setImageResource(R.drawable.common_loading_inner_white_large)
                    loading_outer.setImageResource(R.drawable.common_loading_outer_white_large)
                }
            }
        }

        anim = AnimatorSet().apply {
           
            playTogether(ObjectAnimator.ofFloat(loading_inner, "rotation", 360f, 0f).apply {
                duration = 3000
                repeatCount = Animation.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = LinearInterpolator(context, attrs)
            }, ObjectAnimator.ofFloat(loading_outer, "rotation", 0f, 360f).apply {
                duration = 3000
                repeatCount = Animation.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = LinearInterpolator(context, attrs)
            })
        }

        if (isAutoStart) {
            startAnim()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        anim.cancel()
    }

    fun startAnim() {
        ALog.i("CommonLoadingView", "startAnim")
        anim.start()
    }

    fun isStarted(): Boolean {
        return anim.isStarted
    }

    fun stopAnim() {
        ALog.i("CommonLoadingView", "stopAnim")
        anim.cancel()
    }

    fun setListener(listener: AnimatorListenerAdapter) {
        anim.addListener(listener)
    }
}