package com.bcm.messenger.wallet.utils.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Environment
import android.os.StatFs
import android.text.TextUtils
import java.io.*
import java.security.cert.X509Certificate
import javax.net.ssl.*

/**
 * @author Aaron
 * @email aaron@magicwindow.cn
 * @date 10/03/2018 19:11
 * @description
 */
object FileUtils {
    private val FILE_SAVE_PATH = "mw_cache"
    private val DO_NOT_VERIFY = HostnameVerifier { hostname, session -> true }
    private val TAG = "BcmFileUtils"


    //        DebugLog.e("aaron totalBlocks:"+totalBlocks+", blockSize:"+blockSize+", memory:"+totalBlocks * blockSize);
    val totalInternalMemorySize: Long
        get() {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            var blockSize: Long = 0
            var totalBlocks: Long = 0

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                blockSize = stat.blockSizeLong
                totalBlocks = stat.blockCountLong

            } else {
                blockSize = stat.blockSize.toLong()
                totalBlocks = stat.blockCount.toLong()
            }
            return totalBlocks * blockSize
        }

    fun getMWCachePath(context: Context): String {
        return getDiskCacheDir(context, FILE_SAVE_PATH)!!.toString() + File.separator
    }

    fun loadImage(url: String, filename: String): Bitmap? {
        try {
            val fis = FileInputStream(url + filename)
            return BitmapFactory.decodeStream(fis)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            return null
        }

    }

    /**
     * Trust every server - dont check for any certificate
     */
    private fun trustAllHosts() {
        // Create a trust manager that does not validate certificate chains
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {

            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> {
                return arrayOf()
            }

            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        })

        // Install the all-trusting trust manager
        try {
            val sc = SSLContext.getInstance("TLS")
            sc.init(null, trustAllCerts, java.security.SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun hasExternalCache(context: Context): Boolean {
        return (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()
                && Util.checkPermissions(context, "android.permission.WRITE_EXTERNAL_STORAGE")
                && context.externalCacheDir != null)
    }

    fun getDiskCacheDir(context: Context, fileDir: String?): File? {

        var cacheDirectory: File?
        if (hasExternalCache(context)) {
            cacheDirectory = context.externalCacheDir
        } else {
            cacheDirectory = context.cacheDir
        }
        if (cacheDirectory == null) {
            cacheDirectory = context.cacheDir
            if (cacheDirectory == null) {
                return null
            }
        }
        if (fileDir != null) {
            val file = File(cacheDirectory, fileDir)
            return if (!file.exists() && !file.mkdir()) {
                cacheDirectory
            } else {
                file
            }
        }
        return cacheDirectory
    }

    fun delFile(path: String) {
        if (!TextUtils.isEmpty(path)) {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    fun compressFile(oldpath: String, newPath: String): File? {
        var compressBitmap: Bitmap? = decodeFile(oldpath)
        var newBitmap: Bitmap? = ratingImage(oldpath, compressBitmap)
        val os = ByteArrayOutputStream()
        newBitmap!!.compress(Bitmap.CompressFormat.PNG, 60, os)
        val bytes = os.toByteArray()

        var file: File? = null
        try {
            file = getFileFromBytes(bytes, newPath)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (!newBitmap.isRecycled) {
                newBitmap.recycle()
                newBitmap = null
            }
            if (compressBitmap != null) {
                if (!compressBitmap.isRecycled) {
                    compressBitmap.recycle()
                    compressBitmap = null
                }
            }
        }
        return file
    }

    private fun ratingImage(filePath: String, bitmap: Bitmap?): Bitmap {
        val degree = readPictureDegree(filePath)
        return rotatingImageView(degree, bitmap)
    }

    fun rotatingImageView(angle: Int, bitmap: Bitmap?): Bitmap {
        //旋转图片 动作
        val matrix = Matrix()
        matrix.postRotate(angle.toFloat())
        // 创建新的图片
        return Bitmap.createBitmap(bitmap!!, 0, 0,
                bitmap.width, bitmap.height, matrix, true)
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
                else -> degree = 0
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return degree
    }

    fun getFileFromBytes(b: ByteArray, outputFile: String): File? {
        var ret: File? = null
        var stream: BufferedOutputStream? = null
        try {
            ret = File(outputFile)
            val fstream = FileOutputStream(ret)
            stream = BufferedOutputStream(fstream)
            stream.write(b)
        } catch (e: Exception) {
            // log.error("helper:getINSTANCE file from byte process error!");
            e.printStackTrace()
        } finally {
            closeQuietly(stream)
        }
        return ret
    }

    fun decodeFile(fPath: String): Bitmap {
        val opts = BitmapFactory.Options()
        opts.inJustDecodeBounds = true
        opts.inDither = false // Disable Dithering mode
        opts.inPurgeable = true // Tell to gc that whether it needs free
        opts.inInputShareable = true // Which kind of reference will be used to
        opts.inPreferredConfig = Bitmap.Config.RGB_565
        BitmapFactory.decodeFile(fPath, opts)
        val REQUIRED_SIZE = 200
        var scale = 1
        if (opts.outHeight > REQUIRED_SIZE || opts.outWidth > REQUIRED_SIZE) {
            val heightRatio = Math.round(opts.outHeight.toFloat() / REQUIRED_SIZE.toFloat())
            val widthRatio = Math.round(opts.outWidth.toFloat() / REQUIRED_SIZE.toFloat())
            scale = Math.min(heightRatio, widthRatio)
        }
        opts.inJustDecodeBounds = false
        opts.inSampleSize = scale
        return BitmapFactory.decodeFile(fPath, opts).copy(Bitmap.Config.ARGB_8888, false)
    }

    fun getFileType(path: String): String? {
        val file = File(path)
        if (file.exists() && file.isFile) {
            val fileName = file.name
            return fileName.substring(fileName.lastIndexOf(".") + 1)
        }

        return null
    }

    fun closeQuietly(closeable: Closeable?) {
        if (closeable != null) {
            try {
                closeable.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }
    }
}