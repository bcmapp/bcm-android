package com.bcm.messenger.utility

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.*

/**
 * Created by wjh on 2018/5/29
 */
class QREncoder(val data: String, formatString: String? = null, private val dimension: Int = 0, private val charset: String? = null) {

    private val WHITE = -0x1
    private val BLACK = -0x1000000

    private val format: BarcodeFormat

    init {
        var f: BarcodeFormat? = null
        if (formatString != null) {
            try {
                f = BarcodeFormat.valueOf(formatString)
            } catch (iae: IllegalArgumentException) {
                // Ignore it then
            }
        }
        if (f == null) {
            this.format = BarcodeFormat.QR_CODE
        } else {
            this.format = f
        }
    }

    @Throws(Exception::class)
    fun encodeAsBitmap(): Bitmap {
        val hints: MutableMap<EncodeHintType, Any> = Hashtable<EncodeHintType, Any>()
        hints[EncodeHintType.MARGIN] = 0
        hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.H
        if (charset != null) {
            hints[EncodeHintType.CHARACTER_SET] = charset
        }
        val writer = MultiFormatWriter()
        val result = writer.encode(data, format, dimension, dimension, hints)
        val width = result.width
        val height = result.height
        val pixels = IntArray(width * height)
        // All are 0, or black, by default
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (result.get(x, y)) BLACK else WHITE
            }
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}