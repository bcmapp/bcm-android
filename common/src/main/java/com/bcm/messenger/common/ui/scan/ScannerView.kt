package com.bcm.messenger.common.ui.scan

import android.content.Context
import android.graphics.*
import android.graphics.Matrix.ScaleToFit
import android.graphics.Paint.Style
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import com.google.zxing.ResultPoint
import com.bcm.messenger.common.R
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.utils.*
import java.util.HashMap


/**
 * view
 * Created by wjh on 2018/06/06
 */
class ScannerView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    companion object {

        private const val TAG = "ScannerView"
        private const val LASER_ANIMATION_DELAY_MS = 50L
        private const val DOT_OPACITY = 0xa0
        private const val DOT_TTL_MS = 500

    }

    private val maskPaint: Paint
    private val laserPaint: Paint
    private val dotPaint: Paint
    private var mTextPaint: TextPaint? = null
    private val cornerWidth: Int
    private val cornerHeight: Int

    private var isResult: Boolean = false
    private val maskColor: Int
    private val maskResultColor: Int
    private val laserColor: Int
    private val cornerColor: Int
    private val dotColor: Int
    private val dotResultColor: Int
    private val dots = HashMap<FloatArray, Long>(16)
    private var mFrame: Rect? = null
    private val mMatrix = Matrix()
    private val tempPoint = FloatArray(2)

    private var mLaserMove = 0 //
    private var mLaserCurrent = 0 //
    private var mLaserToBottom = true //，true：，false： 
    private var mLaserHeight = 0 //
    private var mLaserBitmap: Bitmap? = null

    var drawLaser: Boolean = false //
    var drawCorner: Boolean = false //
    var drawExterior: Boolean = false //
    var drawPoint: Boolean = false //
    var pauseDraw: Boolean = false //
    var useCameraFrame: Boolean = false

    /**
     * 
     */
    private var mScanTip: CharSequence? = null

    init {

        val res = resources
        cornerColor = context.getColorCompat(R.color.common_scan_corner)
        maskColor = context.getColorCompat(R.color.common_scan_mask)
        maskResultColor = context.getColorCompat(R.color.common_scan_result_view)
        laserColor = context.getColorCompat(R.color.common_scan_laser)
        dotColor = context.getColorCompat(R.color.common_scan_dot)
        dotResultColor = context.getColorCompat(R.color.common_scan_result_dots)
        cornerWidth = res.getDimensionPixelSize(R.dimen.common_scan_dot_width)
        cornerHeight = res.getDimensionPixelSize(R.dimen.common_scan_corner_height)

        maskPaint = Paint()
        maskPaint.style = Style.FILL
        maskPaint.isAntiAlias = true

        laserPaint = Paint()
        laserPaint.isAntiAlias = true
        laserPaint.style = Style.FILL

        dotPaint = Paint()
        dotPaint.alpha = DOT_OPACITY
        dotPaint.style = Style.STROKE
        dotPaint.strokeWidth = res.getDimensionPixelSize(R.dimen.common_scan_dot_width).toFloat()
        dotPaint.isAntiAlias = true

        mLaserHeight = 2.dp2Px()
        mLaserMove = 3.dp2Px()

    }


    /**
     * 
     */
    fun setScanTip(tip: CharSequence) {
        mScanTip = tip
        if (mTextPaint == null) {
            mTextPaint = TextPaint()
            mTextPaint?.isAntiAlias = true
            mTextPaint?.textAlign = Paint.Align.CENTER
            mTextPaint?.color = context.getColorCompat(R.color.common_color_white)
            mTextPaint?.textSize = 16f.sp2Px()
        }
    }

    fun setFraming(frame: Rect, framePreview: RectF, displayRotation: Int,
                   cameraRotation: Int, cameraFlip: Boolean) {
        ALog.d(TAG, "setFraming")
        this.mFrame = frame
        mMatrix.setRectToRect(framePreview, RectF(frame), ScaleToFit.FILL)
        mMatrix.postRotate((-displayRotation).toFloat(), frame.exactCenterX(), frame.exactCenterY())
        mMatrix.postScale((if (cameraFlip) -1 else 1).toFloat(), 1f, frame.exactCenterX(), frame.exactCenterY())
        mMatrix.postRotate(cameraRotation.toFloat(), frame.exactCenterX(), frame.exactCenterY())

        invalidate()
    }

    fun setIsResult(isResult: Boolean) {
        this.isResult = isResult

        invalidate()
    }

    fun addDot(dot: ResultPoint) {
        dots[floatArrayOf(dot.x, dot.y)] = System.currentTimeMillis()
        if (drawPoint) {
            invalidate()
        }
    }

    public override fun onDraw(canvas: Canvas) {
        if (mFrame == null) {
            return
        }
        val width = width
        val height = height

        //
        drawExterior(canvas, mFrame, width, height)
        //
        drawCorner(canvas, mFrame)
        //
        drawText(canvas, mFrame)

        drawLaser(canvas, mFrame)

        // draw points
        drawPoint(canvas, mFrame)

        if (isResult) {
            laserPaint.color = dotResultColor
            dotPaint.color = dotResultColor
        } else {
            laserPaint.color = laserColor
            dotPaint.color = dotColor

            if (!pauseDraw) {
                // schedule redraw
                postInvalidateDelayed(LASER_ANIMATION_DELAY_MS)
            }
        }
    }

    private fun drawPoint(canvas: Canvas, frame: Rect?) {
        if (frame == null || !drawPoint) return
        val i = dots.entries.iterator()
        val now = System.currentTimeMillis()
        while (i.hasNext()) {
            val entry = i.next()
            val age = now - entry.value
            if (age < DOT_TTL_MS) {
                dotPaint.alpha = ((DOT_TTL_MS - age) * 256 / DOT_TTL_MS).toInt()

                mMatrix.mapPoints(tempPoint, entry.key)
                canvas.drawPoint(tempPoint[0], tempPoint[1], dotPaint)
            } else {
                i.remove()
            }
        }
    }

    //
    private fun drawCorner(canvas: Canvas, frame: Rect?) {
        if (frame == null || !drawCorner) {
            return
        }
        laserPaint.color = cornerColor
        //
        canvas.drawRect(frame.left.toFloat(), frame.top.toFloat(), (frame.left + cornerWidth).toFloat(), (frame.top + cornerHeight).toFloat(), laserPaint)
        canvas.drawRect(frame.left.toFloat(), frame.top.toFloat(), (frame.left + cornerHeight).toFloat(), (frame.top + cornerWidth).toFloat(), laserPaint)
        //
        canvas.drawRect((frame.right - cornerWidth).toFloat(), frame.top.toFloat(), frame.right.toFloat(), (frame.top + cornerHeight).toFloat(), laserPaint)
        canvas.drawRect((frame.right - cornerHeight).toFloat(), frame.top.toFloat(), frame.right.toFloat(), (frame.top + cornerWidth).toFloat(), laserPaint)
        //
        canvas.drawRect(frame.left.toFloat(), (frame.bottom - cornerWidth).toFloat(), (frame.left + cornerHeight).toFloat(), frame.bottom.toFloat(), laserPaint)
        canvas.drawRect(frame.left.toFloat(), (frame.bottom - cornerHeight).toFloat(), (frame.left + cornerWidth).toFloat(), frame.bottom.toFloat(), laserPaint)
        //
        canvas.drawRect((frame.right - cornerWidth).toFloat(), (frame.bottom - cornerHeight).toFloat(), frame.right.toFloat(), frame.bottom.toFloat(), laserPaint)
        canvas.drawRect((frame.right - cornerHeight).toFloat(), (frame.bottom - cornerWidth).toFloat(), frame.right.toFloat(), frame.bottom.toFloat(), laserPaint)
    }

    //  Draw a two pixel solid black border inside the framing rect
    private fun drawFrame(canvas: Canvas, frame: Rect?) {
        if (frame == null || !drawCorner) {
            return
        }
        maskPaint.color = if (isResult) maskResultColor else maskColor
        canvas.drawRect(frame.left.toFloat(), frame.top.toFloat(), (frame.right + 1).toFloat(), (frame.top + 2).toFloat(), maskPaint)
        canvas.drawRect(frame.left.toFloat(), (frame.top + 2).toFloat(), (frame.left + 2).toFloat(), (frame.bottom - 1).toFloat(), maskPaint)
        canvas.drawRect((frame.right - 1).toFloat(), frame.top.toFloat(), (frame.right + 1).toFloat(), (frame.bottom - 1).toFloat(), maskPaint)
        canvas.drawRect(frame.left.toFloat(), (frame.bottom - 1).toFloat(), (frame.right + 1).toFloat(), (frame.bottom + 1).toFloat(), maskPaint)
    }

    //  Draw the exterior (i.e. outside the framing rect) darkened
    private fun drawExterior(canvas: Canvas, frame: Rect?, width: Int, height: Int) {
        if (frame == null || !drawExterior) {
            return
        }
        maskPaint.color = if (isResult) maskResultColor else maskColor
        canvas.drawRect(0f, 0f, width.toFloat(), frame.top.toFloat(), maskPaint)
        canvas.drawRect(0f, frame.top.toFloat(), frame.left.toFloat(), (frame.bottom + 1).toFloat(), maskPaint)
        canvas.drawRect((frame.right + 1).toFloat(), frame.top.toFloat(), width.toFloat(), (frame.bottom + 1).toFloat(), maskPaint)
        canvas.drawRect(0f, (frame.bottom + 1).toFloat(), width.toFloat(), height.toFloat(), maskPaint)
    }

    /**
     * 
     */
    private fun drawText(canvas: Canvas, frame: Rect?) {
        val tip = mScanTip?.toString()
        val paint = mTextPaint
        if (frame != null && tip != null && paint != null) {
            val x = 38.dp2Px()
            val textTargetWidth = context.getScreenWidth() - x - x
            val textLayout = StaticLayout(tip, paint, textTargetWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, true)
            val y = frame.bottom + resources.getDimensionPixelSize(R.dimen.common_horizontal_gap_20)
            canvas.translate(context.getScreenWidth() / 2.0f, y.toFloat())
            textLayout.draw(canvas)
        }
    }

    /**
     * 
     */
    private fun drawLaser(canvas: Canvas, rect: Rect?) {

        if (rect == null || !drawLaser) return
        val bottom = if (useCameraFrame) {
            mFrame?.bottom ?: 0
        }else {
            this.bottom - 30.dp2Px()
        }
        val top = if (useCameraFrame) {
            mFrame?.top ?: 0
        }else {
            this.top + 30.dp2Px()
        }
        if (mLaserCurrent == 0) {
            mLaserCurrent = top
        }
        if (mLaserCurrent >= bottom) {
            mLaserToBottom = false
        }else if (mLaserCurrent <= top) {
            mLaserToBottom = true
        }

        if (mLaserBitmap == null) {
            mLaserBitmap = BitmapFactory.decodeResource(resources, R.drawable.common_scan_laser_bg)
        }

        canvas.drawBitmap(mLaserBitmap, rect.left.toFloat(), mLaserCurrent.toFloat(), laserPaint)

        if (mLaserToBottom) {
            mLaserCurrent += mLaserMove
        }else {
            mLaserCurrent -= mLaserMove
        }
    }

    private fun shadeColor(color: Int): Int {
        val hax = Integer.toHexString(color)
        val result = "20" + hax.substring(2)
        return Integer.valueOf(result, 16)
    }
}
