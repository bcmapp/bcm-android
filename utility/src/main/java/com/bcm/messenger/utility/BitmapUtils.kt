package com.bcm.messenger.utility

import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.ThumbnailUtils
import android.util.Size
import com.bcm.messenger.utility.logger.ALog
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import java.io.*
import kotlin.math.max

/**
 * Created by zjl on 2018/4/28.
 */
object BitmapUtils {

    private const val TAG = "BitmapUtils"

    fun getImageDimensions(inputStream: InputStream): Size {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        val fis = BufferedInputStream(inputStream)
        BitmapFactory.decodeStream(fis, null, options)
        try {
            fis.close()
        } catch (ioe: IOException) {
            ioe.printStackTrace()
        }
        return Size(options.outWidth, options.outHeight)
    }

    fun getActualImageSize(path: String): Size {
        val size = getImageSize(path)
        return getActualImageSize(path, size.width, size.height)
    }

    fun getImageSize(imagePath: String): Size {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(imagePath, options)
        return Size(options.outWidth, options.outHeight)
    }

    private fun getActualImageSize(path: String, width: Int, height: Int): Size {
        val result = intArrayOf(0, 0)
        val degree = readPictureDegree(path)
        when (degree) {
            0 -> {
                result[0] = width
                result[1] = height
            }
            90 -> {
                result[0] = height
                result[1] = width
            }
            180 -> {
                result[0] = width
                result[1] = height
            }
            else -> {
                result[0] = height
                result[1] = width
            }
        }
        return Size(result[0], result[1])
    }

    private fun calculateSampleSize(sourceWidth: Int, sourceHeight: Int, desireWidth: Int, desireHeight: Int): Int {
        val w = sourceWidth
        val h = sourceHeight
        var sample = 1
        if(desireWidth == 0 || desireHeight == 0) {
            return sample
        }
        if(h > desireHeight || w > desireWidth) {
            val hr = Math.round(h.toFloat() / desireHeight)
            val wr = Math.round(w.toFloat() / desireWidth)
            sample = if(hr < wr) wr else hr
        }
        return sample
    }

    fun toByteArray(bitmap: Bitmap?, quality: Int = 100): ByteArray? {
        if (bitmap == null) return null
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, quality, stream)
        return stream.toByteArray()
    }

    fun compressBitmap(path: String, desireWidth: Int, desireHeight: Int): Bitmap {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, options)
        val size = getActualImageSize(path, options.outWidth, options.outHeight)
        options.inSampleSize = calculateSampleSize(size.width, size.height, desireWidth, desireHeight)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(path, options)
    }

    fun getByteCount(bitmap: Bitmap): Int {
        return bitmap.allocationByteCount
    }

    fun getImageThumbnail(imagePath: String, width: Int, height: Int): Bitmap {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(imagePath, options)
        val h = options.outHeight
        val w = options.outWidth
        val scaleWidth = w / width
        val scaleHeight = h / height
        var scale = 1
        scale = if (scaleWidth < scaleHeight) {
            scaleWidth
        } else {
            scaleHeight
        }
        if (scale <= 0) {
            scale = 1
        }
        options.inSampleSize = scale
        options.inJustDecodeBounds = false
        var bitmap = BitmapFactory.decodeFile(imagePath, options)
        bitmap = ThumbnailUtils.extractThumbnail(bitmap, width, height, ThumbnailUtils.OPTIONS_RECYCLE_INPUT)
        return bitmap
    }

    fun getImageThumbnailPath(imagePath: String, maxSize: Int = 300, rate: Int = 50): String {
        try {
            val MAX_THUMBNAIL_SIZE = maxSize

            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(imagePath, options)
            val width = options.outWidth
            val height = options.outHeight

            var destWidth = width
            var destHeight = height

            val imageSize = max(width, height)
            if (imageSize > MAX_THUMBNAIL_SIZE) {
                if (imageSize == width) {
                    destHeight = MAX_THUMBNAIL_SIZE * height / imageSize
                    destWidth = MAX_THUMBNAIL_SIZE
                } else {
                    destHeight = MAX_THUMBNAIL_SIZE
                    destWidth = MAX_THUMBNAIL_SIZE * width / imageSize
                }

                val bitmap = getImageThumbnail(imagePath, destWidth, destHeight)

                var thumbPath = ""
                var outputStream: FileOutputStream? = null
                try {
                    thumbPath = imagePath + ".thumb"
                    outputStream = FileOutputStream(File(thumbPath))
                    bitmap.compress(Bitmap.CompressFormat.PNG, rate, outputStream)
                    outputStream.close()
                    outputStream = null
                    return thumbPath
                } catch (e: Exception) {
                    ALog.e("thumbnail", e)
                    return ""
                } finally {
                    try {
                        outputStream?.close()
                    } catch (e: Exception) {
                        ALog.e("thumbnail", e)
                    }
                }
            }
        } catch (e: Exception) {
            ALog.e(TAG, "getImageThumbnailPath error", e)
            return ""
        }
        return imagePath
    }

    fun compressImageForThumbnail(file: File): Bitmap? {
        return compressImageForThumbnail(file.absolutePath)
    }

    fun compressImageForThumbnail(imagePath: String, destMaxSize: Int = 300): Bitmap? {
        try {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(imagePath, options)
            val originWidth = options.outWidth
            val originHeight = options.outHeight

            var destWidth = originWidth
            var destHeight = originHeight

            val originMaxSize = max(destWidth, destHeight)

            return if (originMaxSize > destMaxSize) {
                destHeight = destMaxSize * originHeight / originMaxSize
                destWidth = destMaxSize * originWidth / originMaxSize
                getImageThumbnail(imagePath, destWidth, destHeight)
            } else {
                options.inJustDecodeBounds = false
                BitmapFactory.decodeFile(imagePath, options)
            }
        } catch (e: Exception) {
            ALog.e("BitmapUtils","compressImageForThumbnail error", e)
        }
        return null
    }

    fun readPictureDegree(path: String): Int {
        var degree = 0
        try {
            val exifInterface = ExifInterface(path)
            val orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> degree = 90
                ExifInterface.ORIENTATION_ROTATE_180 -> degree = 180
                ExifInterface.ORIENTATION_ROTATE_270 -> degree = 270
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return degree
        }

        return degree
    }

    fun rotateBitmapByDegree(bm: Bitmap, degree: Int): Bitmap {
        var returnBm: Bitmap? = null

        val matrix = Matrix()
        matrix.postRotate(degree.toFloat())

        returnBm = Bitmap.createBitmap(bm, 0, 0, bm.width, bm.height, matrix, true)

        if (returnBm == null) {
            returnBm = bm
        }
        if (bm !== returnBm) {
            bm.recycle()
        }
        return returnBm
    }
}

