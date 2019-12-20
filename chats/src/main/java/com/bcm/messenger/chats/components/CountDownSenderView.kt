package com.bcm.messenger.chats.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.animation.LinearInterpolator
import android.util.AttributeSet
import android.view.View
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.utils.AppUtil


/**
 * Created by zjl on 2018/8/6.
 */
class CountDownSenderView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {
    private val mRingColor: Int
    private val mRingWidth: Float
    private val mRingProgessTextSize: Int
    private var mWidth: Int = 0
    private var mHeight: Int = 0

    private val mPaint: Paint
    private var mRectF: RectF = RectF()

    private var mCountdownTime: Int = 0
    private var mCurrentProgress: Float = 0F
    private var mListener: OnCountDownFinishListener? = null

    private var bitmapSend: Bitmap
    private var bitmapLoading: Bitmap
    private var canSend = true

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.CountDownView)
        mRingColor = a.getColor(R.styleable.CountDownView_ringColor, AppUtil.getColor(context.resources, R.color.common_color_379BFF))
        mRingWidth = a.getFloat(R.styleable.CountDownView_ringWidth, 15f)
        mRingProgessTextSize = a.getDimensionPixelSize(R.styleable.CountDownView_progressTextSize, AppUtil.dp2Px(resources, 20))
        mCountdownTime = a.getInteger(R.styleable.CountDownView_countdownTime, 0)
        a.recycle()
        mPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mPaint.isAntiAlias = true
        mPaint.color = mRingColor
        mPaint.style = Paint.Style.STROKE
        mPaint.strokeWidth = mRingWidth
        this.setWillNotDraw(false)

        bitmapSend = BitmapFactory.decodeResource(context.resources, R.drawable.chats_message_send_icon)
        bitmapLoading = BitmapFactory.decodeResource(context.resources, R.drawable.chats_message_send_loading_icon)
    }

    fun setCountDownTime(time: Int) {
        mCountdownTime = time
    }

    fun setCountdownTime(mCountdownTime: Int) {
        this.mCountdownTime = mCountdownTime
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        mWidth = measuredWidth
        mHeight = measuredHeight
        mRectF.left = 0 + mRingWidth / 2
        mRectF.top = 0 + mRingWidth / 2
        mRectF.right = mWidth - mRingWidth / 2
        mRectF.bottom = mHeight - mRingWidth / 2
        imageLeft = (width / 2 - bitmapSend.width / 2).toFloat()
        imageTop = (height / 2 - bitmapSend.height / 2).toFloat()
    }

    private var imageLeft = 0F
    private var imageTop = 0F

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isClickable) {
            canvas.drawArc(mRectF, -90f, mCurrentProgress - 360, false, mPaint)
        }

        if (canSend)
            canvas.drawBitmap(bitmapSend, imageLeft, imageTop, mPaint)
        else
            canvas.drawBitmap(bitmapLoading, imageLeft, imageTop, mPaint)
    }

    private fun getValA(countdownTime: Long): ValueAnimator {
        val valueAnimator = ValueAnimator.ofFloat(0f, 100f)
        valueAnimator.duration = countdownTime
        valueAnimator.interpolator = LinearInterpolator()
        valueAnimator.repeatCount = 0
        return valueAnimator
    }


    fun startCountDown() {
        isClickable = false
        val valueAnimator = getValA((mCountdownTime * 1000).toLong())
        valueAnimator.addUpdateListener { animation ->
            val i = animation.animatedValue as Float
            mCurrentProgress = (360 * (i / 100f)).toInt().toFloat()
            invalidate()
            canSend = false
        }
        valueAnimator.start()
        valueAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                mListener?.countDownFinished()

                isClickable = true
                canSend = true
            }

        })
    }

    fun setAddCountDownListener(mListener: OnCountDownFinishListener) {
        this.mListener = mListener
    }

    interface OnCountDownFinishListener {
        fun countDownFinished()
    }


}
