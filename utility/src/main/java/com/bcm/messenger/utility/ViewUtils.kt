package com.bcm.messenger.utility

import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.TranslateAnimation
import android.widget.TextView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.bcm.messenger.utility.concurrent.ListenableFuture
import com.bcm.messenger.utility.concurrent.SettableFuture

/**
 * Created by wjh on 2018/5/8
 */
object ViewUtils {
    fun rightIn(inter: View, outer: View, duration: Long = 500L) {
        val animateIn = TranslateAnimation(inter.width.toFloat(), 0f, 0f, 0f)
        animateIn.fillAfter = true
        animateIn.interpolator = LinearInterpolator()
        animateIn.duration = duration
        val animateOut = TranslateAnimation(0f, -outer.width.toFloat(), 0f, 0f)
        animateOut.fillAfter = true
        animateOut.interpolator = LinearInterpolator()
        animateOut.duration = duration
        animateOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationRepeat(animation: Animation?) {

            }

            override fun onAnimationEnd(animation: Animation?) {
                outer.visibility = View.INVISIBLE
            }

            override fun onAnimationStart(animation: Animation?) {
                inter.visibility = View.VISIBLE
            }

        })
        inter.clearAnimation()
        inter.startAnimation(animateIn)
        outer.clearAnimation()
        outer.startAnimation(animateOut)
    }

    fun rightOut(inter: View, outer: View, duration: Long = 500L) {
        val animateIn = TranslateAnimation(-inter.width.toFloat(), 0f, 0f, 0f)
        animateIn.fillAfter = true
        animateIn.interpolator = LinearInterpolator()
        animateIn.duration = duration
        animateIn.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationRepeat(animation: Animation?) {
            }

            override fun onAnimationEnd(animation: Animation?) {
                outer.visibility = View.INVISIBLE
            }

            override fun onAnimationStart(animation: Animation?) {
                inter.visibility = View.VISIBLE
            }

        })
        val animateOut = TranslateAnimation(0f, inter.width.toFloat(), 0f, 0f)
        animateOut.fillAfter = true
        animateOut.interpolator = LinearInterpolator()
        animateOut.duration = duration
        inter.clearAnimation()
        inter.startAnimation(animateIn)
        outer.clearAnimation()
        outer.startAnimation(animateOut)
    }

    fun isTouchPointInView(targetView: View, xAxis: Int, yAxis: Int): Boolean {
        val location = IntArray(2)
        targetView.getLocationOnScreen(location)
        val left = location[0]
        val top = location[1]
        val right = left + targetView.measuredWidth
        val bottom = top + targetView.measuredHeight
        return (yAxis in top..bottom) && (xAxis in left..right)
    }


    private fun getAlphaAnimation(from: Float, to: Float, duration: Int): Animation {
        val anim = AlphaAnimation(from, to)
        anim.interpolator = FastOutSlowInInterpolator()
        anim.duration = duration.toLong()
        return anim
    }

    fun fadeIn(view: View, duration: Int) {
        animateIn(view, getAlphaAnimation(0f, 1f, duration))
    }

    fun fadeOut(view: View, duration: Int): ListenableFuture<Boolean> {
        return fadeOut(view, duration, View.GONE)
    }

    fun fadeOut(view: View, duration: Int, visibility: Int): ListenableFuture<Boolean> {
        return animateOut(view, getAlphaAnimation(1f, 0f, duration), visibility)
    }

    fun animateOut(view: View, animation: Animation, visibility: Int): ListenableFuture<Boolean> {
        val future = SettableFuture<Boolean>()
        if (view.visibility == visibility) {
            future.set(true)
        } else {
            view.clearAnimation()
            animation.reset()
            animation.startTime = 0
            animation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {}

                override fun onAnimationRepeat(animation: Animation) {}

                override fun onAnimationEnd(animation: Animation) {
                    view.visibility = visibility
                    future.set(true)
                }
            })
            view.startAnimation(animation)
        }
        return future
    }

    fun animateIn(view: View, animation: Animation) {
        if (view.visibility == View.VISIBLE) return

        view.clearAnimation()
        animation.reset()
        animation.startTime = 0
        view.visibility = View.VISIBLE
        view.startAnimation(animation)
    }

}


fun TextView.setDrawableRight(resId: Int, size: Int = 0) {
    if (resId == 0) {
        this.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        return
    }
    val img = context.getDrawable(resId)
    if (size != 0) {
        img?.let {
            it.setBounds(0, 0, size, size)
            this.setCompoundDrawables(null, null, img, null)
        }
    } else {
        this.setCompoundDrawablesWithIntrinsicBounds(0, 0, resId, 0)
    }
}

fun TextView.setDrawableLeft(resId: Int, size: Int = 0) {
    if (resId == 0) {
        this.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        return
    }
    val img = context.getDrawable(resId)
    if (size != 0) {
        img?.let {
            it.setBounds(0, 0, size, size)
            this.setCompoundDrawables(img, null, null, null)
        }
    } else {
        this.setCompoundDrawablesWithIntrinsicBounds(resId, 0, 0, 0)
    }
}

