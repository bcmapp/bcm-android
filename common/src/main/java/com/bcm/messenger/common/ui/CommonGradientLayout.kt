package com.bcm.messenger.common.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.palette.graphics.Palette

/**
 * Created by Kin on 2019/7/4
 */
class CommonGradientLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr) {
    private var colorArray = intArrayOf()

    private val paint = Paint()
    private var backgroundGradient: LinearGradient? = null

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        if (colorArray.isEmpty()) {
            return
        }

        val width = width.toFloat()
        val height = height.toFloat()

        paint.shader = backgroundGradient
        canvas?.drawRect(0f, 0f, width, height, paint)
    }

    fun setGradientBackground(bitmap: Bitmap) {
        Palette.Builder(bitmap).generate { palette ->
            if (palette != null) {
                val swatches = palette.swatches
                val sortedSwatches = swatches.sortedByDescending { it.population }

                if (sortedSwatches.size >= 2) {
                    val color1 = sortedSwatches[0].rgb
                    val color2 = sortedSwatches[1].rgb

                    colorArray = intArrayOf(color1, color2)

                    backgroundGradient = LinearGradient(0f, 0f, 0f, height.toFloat(), colorArray, null, Shader.TileMode.CLAMP)
                    invalidate()
                }
            }
        }
    }

    fun setGradientBackground(bitmap: Bitmap, colorCallback: (isLightColor: Boolean) -> Unit) {
        Palette.Builder(bitmap).generate { palette ->
            if (palette != null) {
                val swatches = palette.swatches
                val sortedSwatches = swatches.sortedByDescending { it.population }

                if (sortedSwatches.size >= 2) {
                    val color1 = sortedSwatches[0].rgb
                    val color2 = sortedSwatches[1].rgb

                    colorArray = intArrayOf(color1, color2)

                    backgroundGradient = LinearGradient(0f, 0f, 0f, height.toFloat(), colorArray, null, Shader.TileMode.CLAMP)
                    invalidate()

                    colorCallback(isLightColor(Color.red(color1), Color.green(color1), Color.blue(color1)))
                }
            }
        }
    }

    private fun isLightColor(red: Int, green: Int, blue: Int): Boolean {
        return (red * 0.299 + green * 0.578 + blue * 0.114) >= 192
    }
}