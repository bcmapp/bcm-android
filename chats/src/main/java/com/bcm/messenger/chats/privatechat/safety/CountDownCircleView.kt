package com.bcm.messenger.chats.privatechat.safety

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import androidx.annotation.AttrRes
import androidx.annotation.ColorRes
import androidx.appcompat.widget.AppCompatTextView
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getAttrColor
import java.util.*

/**
 * count down view
 * Created by zjl on 2018/8/29.
 */
class CountDownCircleView : AppCompatTextView {

    private val PADDING = 1.dp2Px().toFloat()
    private var mPaint = Paint()
    internal var mAngel = 0f
    @Volatile private var countDownMilli = 0
    private var mTimer: Timer? = null
    private var mValueAnimator: ValueAnimator? = null

    private var startedAt = 0L
    private var expiresIn = 0L

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    private fun init() {
        mPaint.color = context.getAttrColor(R.attr.common_text_secondary_color)
        mPaint.isAntiAlias = true
        mPaint.style = Paint.Style.FILL
    }

    fun setColor(@AttrRes color: Int) {
        mPaint.color = context.getAttrColor(color)
    }

    private fun calculateProgress(startedAt: Long, expiresIn: Long): Float {
        val progressed = System.currentTimeMillis() - startedAt
        val percentComplete = progressed.toFloat() / expiresIn.toFloat()

        return percentComplete * 360
    }

    fun start(startedAt: Long, expiresIn: Long) {
        this.startedAt = startedAt
        this.expiresIn = expiresIn

        tw.doWork()
        CommonTimer.register(tw)
    }

    fun stop() {
        CommonTimer.unregister(tw)
    }

    fun start(time: Long) {
        countDownMilli = COUNT + 1
        mAngel = 0f
        if (mTimer != null) {
            mTimer!!.cancel()
        }

        mValueAnimator?.cancel()
        mValueAnimator = ValueAnimator.ofFloat(0f, 360f)
        mValueAnimator?.duration = time * 1000L
        mValueAnimator?.interpolator = LinearInterpolator()
        mValueAnimator?.addUpdateListener { animation ->
            mAngel = animation.animatedValue as Float - 360
            invalidate()
        }

        mValueAnimator?.start()

        mTimer = Timer()
        mTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                countDownMilli--
                mAngel = calculateProgress(startedAt, expiresIn) - 360
                if (countDownMilli == 0) {
                    mTimer!!.cancel()
                }
                (context as Activity).runOnUiThread { invalidate() }
            }
        }, 0, 1000)
    }

    private val tw = object : CommonTimer.TimerWork {
        override fun doWork() {
            mAngel = calculateProgress(startedAt, expiresIn) - 360
            (context as Activity).runOnUiThread { invalidate() }
        }
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val rectF = RectF(PADDING, PADDING, width - PADDING, height - PADDING)
        canvas.drawArc(rectF, -90f, mAngel, true, mPaint)
    }

    companion object {
        private val COUNT = 60
    }
}