package com.bcm.messenger.common.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.annotation.Nullable

/**
 * web
 */
class WebProgressView @JvmOverloads constructor(context: Context, @Nullable attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {

    private var mPaint: Paint? = null
    private var mWidth: Int = 0
    private var mHeight: Int = 0
    private var progress: Int = 0//

    init {
        init()
    }

    private fun init() {
        //
        mPaint = Paint()
        mPaint?.isDither = true
        mPaint?.isAntiAlias = true
        mPaint?.strokeWidth = 10f
        mPaint?.color = Color.RED
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        mWidth = w
        mHeight = h
        super.onSizeChanged(w, h, ow, oh)
    }


    override fun onDraw(canvas: Canvas) {
        mPaint?.let {
            canvas.drawRect(0f, 0f, (mWidth * progress / 100).toFloat(), mHeight.toFloat(), it)
        }
        super.onDraw(canvas)
    }

    /**
     *  
     *
     * @param newProgress 
     */
    fun setProgress(newProgress: Int) {
        this.progress = newProgress
        invalidate()
    }

    /**
     * 
     *
     * @param color 
     */
    fun setColor(color: Int) {
        mPaint?.color = color
    }
}