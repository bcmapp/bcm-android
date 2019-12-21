package com.bcm.messenger.common.utils

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.text.TextUtils
import android.text.format.Formatter
import android.util.ArrayMap
import androidx.core.content.FileProvider
import com.bcm.messenger.common.BuildConfig
import com.bcm.messenger.common.core.AmeFileUploader
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.BitmapUtils
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.*
import java.util.*
import kotlin.collections.HashMap
import kotlin.experimental.and


/**
 * 
 * Created by zjl on 2018/5/21.
 */
object BcmFileUtils {

    private val TAG = "BcmFileUtils"

    const val IMAGE_PNG = "image/png"
    private const val IMAGE_JPEG = "image/jpeg"
    const val IMAGE_GIF = "image/gif"
    private const val IMAGE_XTIFF = "image/x-tiff"
    private const val IMAGE_PJPEG = "image/pjpeg"
    private const val IMAGE_TIFF = "image/tiff"
    private const val IMAGE_BMP = "image/bmp"
    private var MIME_MAP = ArrayMap<String, String>(70)
    private var FILE_SUFFIX_MAP = ArrayMap<String, String>(70)

    init {
        MIME_MAP[".3gp"] = "video/3gpp"
        MIME_MAP[".apk"] = "application/vnd.android.package-archive"
        MIME_MAP[".asf"] = "video/x-ms-asf"
        MIME_MAP[".avi"] = "video/x-msvideo"
        MIME_MAP[".bin"] = "application/octet-stream"
        MIME_MAP[".bmp"] = "image/bmp"
        MIME_MAP[".txt"] = "text/plain"
        MIME_MAP[".c"] = "text/plain"
        MIME_MAP[".class"] = "application/octet-stream"
        MIME_MAP[".conf"] = "text/plain"
        MIME_MAP[".cpp"] = "text/plain"
        MIME_MAP[".doc"] = "application/msword"
        MIME_MAP[".exe"] = "application/octet-stream"
        MIME_MAP[".gif"] = "image/gif"
        MIME_MAP[".gtar"] = "application/x-gtar"
        MIME_MAP[".gz"] = "application/x-gzip"
        MIME_MAP[".h"] = "text/plain"
        MIME_MAP[".htm"] = "text/html"
        MIME_MAP[".html"] = "text/html"
        MIME_MAP[".jar"] = "application/java-archive"
        MIME_MAP[".java"] = "text/plain"
        MIME_MAP[".jpg"] = "image/jpeg"
        MIME_MAP[".jpeg"] = "image/jpeg"
        MIME_MAP[".js"] = "application/x-javascript"
        MIME_MAP[".log"] = "text/plain"
        MIME_MAP[".mp3"] = "audio/x-mpeg"
        MIME_MAP[".m3u"] = "audio/x-mpegurl"
        MIME_MAP[".m4a"] = "audio/mp4a-latm"
        MIME_MAP[".m4b"] = "audio/mp4a-latm"
        MIME_MAP[".m4p"] = "audio/mp4a-latm"
        MIME_MAP[".flac"] = "audio/flac"
        MIME_MAP[".m4u"] = "video/vnd.mpegurl"
        MIME_MAP[".m4v"] = "video/x-m4v"
        MIME_MAP[".mov"] = "video/quicktime"
        MIME_MAP[".mp2"] = "audio/x-mpeg"
        MIME_MAP[".mp4"] = "video/mp4"
        MIME_MAP[".mpc"] = "application/vnd.mpohun.certificate"
        MIME_MAP[".mpg"] = "video/mpeg"
        MIME_MAP[".mpe"] = "video/mpeg"
        MIME_MAP[".mpeg"] = "video/mpeg"
        MIME_MAP[".mpg4"] = "video/mp4"
        MIME_MAP[".mpga"] = "audio/mpeg"
        MIME_MAP[".msg"] = "application/vnd.ms-outlook"
        MIME_MAP[".ogg"] = "audio/ogg"
        MIME_MAP[".pdf"] = "application/pdf"
        MIME_MAP[".png"] = "image/png"
        MIME_MAP[".pps"] = "application/vnd.ms-powerpoint"
        MIME_MAP[".ppt"] = "application/vnd.ms-powerpoint"
        MIME_MAP[".prop"] = "text/plain"
        MIME_MAP[".rar"] = "application/x-rar-compressed"
        MIME_MAP[".rc"] = "text/plain"
        MIME_MAP[".rmvb"] = "audio/x-pn-realaudio"
        MIME_MAP[".rtf"] = "application/rtf"
        MIME_MAP[".sh"] = "text/plain"
        MIME_MAP[".tar"] = "application/x-tar"
        MIME_MAP[".tgz"] = "application/x-compressed"
        MIME_MAP[".wav"] = "audio/x-wav"
        MIME_MAP[".wma"] = "audio/x-ms-wma"
        MIME_MAP[".wmv"] = "audio/x-ms-wmv"
        MIME_MAP[".wps"] = "application/vnd.ms-works"
        MIME_MAP[".xml"] = "text/plain"
        MIME_MAP[".z"] = "application/x-compress"
        MIME_MAP[".zip"] = "application/zip"
        MIME_MAP[""] = "*/*"

        for ((k, v) in MIME_MAP) {
            if (FILE_SUFFIX_MAP[v] == null) {
                FILE_SUFFIX_MAP[v] = k
            }
        }
    }

    /**
     * MIME_TYPE
     */
    fun getMimeTypeByNme(fileName: String): String {
        try {
            val index = fileName.lastIndexOf(".")
            if (index >= 0) {
                val suffix = fileName.substring(index).toLowerCase(Locale.getDefault())
                return MIME_MAP[suffix] ?: "*/*"
            }
        }catch (ex: Exception) {
            ALog.e(TAG, "findMimeTypeByName error", ex)
        }
        return "*/*"
    }

    fun getSuffixByMime(mimeType: String): String {
        try {
            val suffix = FILE_SUFFIX_MAP[mimeType]
            if (suffix != null && suffix.startsWith(".")) {
                return suffix.substring(1)
            }
        }catch (ex: Exception) {
            ALog.e(TAG, "findMimeTypeByName error", ex)
        }
        return "dat"
    }

    /**
     * 
     */
    fun installApk(context: Context, filePath: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val uri = FileProvider.getUriForFile(AppContextHolder.APP_CONTEXT, BuildConfig.BCM_APPLICATION_ID + ".fileprovider", File(filePath))
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.setDataAndType(uri, "application/vnd.android.package-archive")
            } else {
                intent.setDataAndType(getFileUri(filePath), "application/vnd.android.package-archive")
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

        }catch (ex: Exception) {
            ALog.e(TAG, "installApk fail", ex)
        }
    }

    /**
     * bcm
     */
    fun isImageType(mineType: String): Boolean {
        return when (mineType) {
            IMAGE_GIF, IMAGE_BMP, IMAGE_JPEG, IMAGE_PJPEG, IMAGE_TIFF, IMAGE_PNG, IMAGE_XTIFF -> true
            else -> false
        }
    }

    /**
     * url()
     * Android MediaMetadataRetriever
     * url,Bitmap
     *
     * @param uri
     * @param result
     * @return
     */
    @SuppressLint("CheckResult")
    fun getRemoteVideoFrameInfo(uri: String?, result: (url: String?, previewPath: String?) -> Unit) {
        if (uri == null) {
            return result(null, null)
        }
        val name = EncryptUtils.encryptMD5ToString(uri)
        var tmpPath = File(AmeFileUploader.TEMP_DIRECTORY, name).absolutePath
        if (isExist(tmpPath)) {
            return result(uri, tmpPath)
        }

        Observable.create(ObservableOnSubscribe<String> {

            var previewBmp: Bitmap? = null
            val retriever = MediaMetadataRetriever()
            try {
                val resourceUri = uri.trim()
                if (resourceUri.startsWith("http", true)) {
                    retriever.setDataSource(uri, HashMap())
                } else {
                    retriever.setDataSource(uri)
                }
                previewBmp = retriever.frameAtTime
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
            } finally {
                retriever.release()
            }

            if (null != previewBmp) {//，
                tmpPath = saveBitmap2File(previewBmp, name)
            }

            if (null != tmpPath) {
                it.onNext(tmpPath)
            } else {
                it.onError(Exception("loading bitmap failed"))
            }
            it.onComplete()

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({

                    result(uri, it)
                }, {
                    result(uri, null)
                })

    }


    /**
     * （）
     */
    fun getVideoFrameBitmap(context: Context, uri: Uri?): Bitmap? {

        if (uri == null) {
            return null
        }
        val videoPath = getFileAbsolutePath(context, uri)
                ?: return null
        var previewPath: String? = null
        val name = EncryptUtils.encryptMD5ToString(videoPath)
        previewPath = File(AmeFileUploader.TEMP_DIRECTORY, name).absolutePath
        if (previewPath != null && isExist(previewPath)) {
            return BitmapFactory.decodeFile(previewPath)

        } else {
            var previewBmp: Bitmap? = null
            val retriever = MediaMetadataRetriever()
            try {
                //
                retriever.setDataSource(videoPath)
                //
                previewBmp = retriever.frameAtTime
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                retriever.release()
            }

            if (null != previewBmp) {//，
                saveBitmap2File(previewBmp, name)
            }
            return previewBmp
        }
    }

    /**
     * 
     */
    fun getVideoFramePath(context: Context, uri: Uri): String? {
        var path: String? = null
        getVideoFrameInfo(context, uri) { previewPath, _, _ ->
            path = previewPath
        }
        return path
    }

    /**
     * 
     */
    fun getVideoFrameInfo(context: Context, path: String?, result: (previewPath: String?, width: Int, height: Int) -> Unit) {
        if (path == null) {
            return result(null, 0, 0)
        }
        var previewPath: String? = null
        var previewW: Int = 0
        var previewH: Int = 0
        val name = EncryptUtils.encryptMD5ToString(path)
        previewPath = File(AmeFileUploader.TEMP_DIRECTORY, name).absolutePath
        if (previewPath != null && isExist(previewPath)) {
            val size = BitmapUtils.getImageSize(previewPath)
            previewW = size.width
            previewH = size.height

        } else {
            var previewBmp: Bitmap? = null
            val retriever = MediaMetadataRetriever()
            try {
                //
                retriever.setDataSource(path)
                //
                previewBmp = retriever.frameAtTime
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                retriever.release()
            }

            if (null != previewBmp) {//，
                previewPath = saveBitmap2File(previewBmp, name)
            }
        }
        return result(previewPath, previewW, previewH)
    }

    fun getVideoFrameInfo(path: String?): Triple<String?, Int, Int> {
        if (path == null) return Triple(null, 0, 0)

        val name = EncryptUtils.encryptMD5ToString(path)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            val bitmap = retriever.frameAtTime
            return Triple(saveBitmap2File(bitmap, name), bitmap.width, bitmap.height)
        } catch (tr: Throwable) {
            ALog.w(TAG, "Get video frame error. ${tr.message}")
        } finally {
            retriever.release()
        }

        return Triple(null, 0, 0)
    }

    /**
     * ()
     */
    fun getVideoFrameInfo(context: Context, uri: Uri?, result: (previewPath: String?, width: Int, height: Int) -> Unit) {
        getVideoFrameInfo(context, getFileAbsolutePath(context, uri), result)
    }

    @Throws(Exception::class)
    fun getFileSize(file: File): Long {
        var size: Long = 0
        if (file.exists()) {
            FileInputStream(file).use {
                size = it.available().toLong()
            }

        }
        return size
    }

    /**
     * Uri(Android4.4Uri)，uricontent uri，
     * @param context
     * @param fileUri
     */
    @TargetApi(19)
    fun getFileAbsolutePath(context: Context?, fileUri: Uri?): String? {
        if (context == null || fileUri == null)
            return null
        if (ContentResolver.SCHEME_FILE == fileUri.scheme) {
            return fileUri.path
        }
        else if(ContentResolver.SCHEME_CONTENT == fileUri.scheme) {
            try {
                val resultPath = if (DocumentsContract.isDocumentUri(context, fileUri)) {
                    if (isExternalStorageDocument(fileUri)) {
                        val docId = DocumentsContract.getDocumentId(fileUri)
                        val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        val type = split[0]
                        if ("primary".equals(type, ignoreCase = true)) {
                            Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                        }else {
                            null
                        }
                    } else if (isDownloadsDocument(fileUri)) {
                        val id = DocumentsContract.getDocumentId(fileUri)
                        val contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), id.toLong())
                        getContentDataPath(context, contentUri, null, null)

                    } else if (isMediaDocument(fileUri)) {
                        val docId = DocumentsContract.getDocumentId(fileUri)
                        val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        val type = split[0]
                        var contentUri: Uri? = null
                        when (type) {
                            "image" -> contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            "video" -> contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            "audio" -> contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        }
                        val selection = MediaStore.Images.Media._ID + "=?"
                        val selectionArgs = arrayOf(split[1])
                        getContentDataPath(context, contentUri, selection, selectionArgs)
                    } else {
                        null
                    }
                } else if ("content".equals(fileUri.scheme, ignoreCase = true)) {
                    if (isGooglePhotosUri(fileUri)) fileUri.lastPathSegment else getContentDataPath(context, fileUri, null, null)
                } else if ("file".equals(fileUri.scheme, ignoreCase = true)) {
                    fileUri.path
                } else {
                    null
                }
                if(resultPath == null || resultPath.isEmpty()) {
                    throw Exception("getFilePath is null")
                }
                return resultPath

            }catch (ex: Exception) {
                ALog.e("BcmFileUtils", "getFileAbsolutePath fail", ex)
                return getContentDataPath(context, fileUri, null, null)
            }
        }
        return null
    }

    /**
     * content uri，
     */
    private fun getContentDataPath(context: Context, uri: Uri?, selection: String?, selectionArgs: Array<String>?): String? {
        uri ?: return null
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, null, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val fileName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                val mimeType = MediaUtil.getMimeType(context, uri)//mimeType
                val destName = if (fileName.isNullOrEmpty()) {//，，mimetype
                    "temp_${System.currentTimeMillis()}" + (if (mimeType.isNullOrEmpty()) ".dat" else {
                        val part = mimeType.split("/")
                        if (part.size > 1) {
                            ".${part[1]}"
                        } else {
                            ".dat"
                        }
                    })
                }else {
                    fileName
                }
                //bcm
                val destPath = AmeFileUploader.DECRYPT_DIRECTORY ?: AmeFileUploader.AME_PATH
                val resultPath = destPath + File.separator + destName
                createFile(destPath, resultPath)
                //
                copy(context.contentResolver.openInputStream(uri), FileOutputStream(resultPath))
                return resultPath

            }
        } catch (e: Exception) {
            ALog.e(TAG, "getContentDataPath error", e)
        } finally {
            cursor?.close()
        }
        return null
    }

    /**
     * inout
     * @param `in`
     * @param out
     * @return
     */
    @Throws(IOException::class)
    fun copy(`in`: InputStream, out: OutputStream): Long {
        val buffer = ByteArray(4096)
        var read: Int
        var total: Long = 0
        try {
            do {
                read = `in`.read(buffer)
                if(read > 0) {
                    out.write(buffer, 0, read)
                    total += read
                }

            }while (read != -1)

        }catch (ex: Exception) {

        }finally {
            `in`.close()
            out.close()
        }
        return total
    }

    /**
     * 
     */
    fun saveBitmap2File(bitmap: Bitmap, imgName: String? = null, directory: String = AmeFileUploader.TEMP_DIRECTORY): String? {

        val bmpName = if (imgName.isNullOrEmpty()) {
            EncryptUtils.getSecretHex(5) + "_temp_bmp.jpg"
        } else {
            imgName
        }
        val dicFile = File(directory)
        if (!dicFile.exists()) {
            dicFile.mkdirs()
        }
        val file = File(directory, bmpName)
        var out: FileOutputStream? = null
        try {
            out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            return file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                out?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return null
    }

    @Throws(IOException::class)
    fun createFile(directory: String, path: String): Uri {
        val dir = File(directory)
        if (!dir.exists()) {
            try {
                //
                dir.mkdirs()
            } catch (e: Exception) {
                // TODO: handle exception
            }

        }

        val file = File(path)
        if (!file.exists()) {
            try {
                //
                file.createNewFile()
            } catch (e: Exception) {
            }

        }
        return Uri.fromFile(file)
    }

    /**
     * uri，
     */
    fun getFileUri(path: String): Uri {
        val file = File(path)
        if (!file.exists()) {
            try {
                //
                file.createNewFile()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return Uri.fromFile(file)
    }

    /**
     * 
     */
    fun delete(path: String?): Boolean {
        if (TextUtils.isEmpty(path)){
            return false
        }

        try {
            var file = File(path)
            if (!file.exists()) {
                val uri = Uri.parse(path)
                val absolutePath = getFileAbsolutePath(AppContextHolder.APP_CONTEXT, uri)
                file = File(absolutePath)
            }

            //
            if (file.absolutePath.contains(AppContextHolder.APP_CONTEXT.packageName)){
                file.delete()
            }
        } catch (e:Exception){
            ALog.e("deleteFile", e)
            return false
        }
        return true
    }

    /**
     * 
     */
    fun deleteDir(dir: File?): Boolean {
        if (dir != null && dir.isDirectory) {
            val child = dir.list()
            child.forEach {
                if (!deleteDir(File(dir, it))) {
                    return false
                }
            }
        }
        return dir?.delete() ?: true
    }

    /**
     * 
     */
    fun isExist(path: String?): Boolean {
        if (TextUtils.isEmpty(path)) {
            return false
        }
        var finalPath = path
        // uri
        try {
            val uri = Uri.parse(path)
            if (ContentResolver.SCHEME_CONTENT == uri.scheme) {
                return true
            }
            if (ContentResolver.SCHEME_FILE == uri.scheme) {
                finalPath = uri.path
            }
        }catch (ex: Exception) {
            ALog.e("isExist", ex)
        }
        try {
            val file = File(finalPath)
            return file.exists() && file.length() > 0
        } catch (e: Exception) {
            ALog.e("isExist", e)
        }
        return false
    }

    /**
     * mimetype
     */
    fun getMimeType(context: Context, filePath: String?): String {
        if (filePath == null) {
            return ""
        }
        var type = MediaUtil.getMimeType(context, Uri.fromFile(File(filePath)))
        if (type == null) {
            type = getFileTypeString(filePath)
            if (type.isNullOrEmpty()) {
                type = getMimeTypeByNme(filePath)
            }
        }
        return type
    }

    /**
     * 
     */
    fun getFileTypeString(filePath: String?): String {
        try {
            val type = getFileType(filePath)
            return type?.toString() ?: ""
        } catch (ex: Exception) {
            ALog.e(TAG, "getFileType error", ex)
        }
        return ""
    }

    /**
     * KB，MB，GB
     */
    fun formatSize(context: Context, target_size: Long): String {
        return Formatter.formatFileSize(context, target_size)
    }

    /**
     * ：1：20：30
     * @param timeMs
     * @return
     */
    fun stringForTime(timeMs: Long): String {
        val seconds = timeMs % 60
        val minutes = timeMs / 60 % 60
        val hours = timeMs / 3600
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    /**
     * 16
     */
    private fun bytes2hex(bytes: ByteArray): String {
        val hex = StringBuilder()
        for (i in bytes.indices) {
            val temp = Integer.toHexString((bytes[i] and 0xFF.toByte()).toInt())
            if (temp.length == 1) {
                hex.append("0")
            }
            hex.append(temp.toLowerCase())
        }
        return hex.toString()
    }

    /**
     * 
     */
    @Throws(IOException::class)
    private fun getFileHeader(filePath: String): String {
        val b = ByteArray(28)//,magic word,startwith
        var inputStream: InputStream? = null
        inputStream = FileInputStream(filePath)
        inputStream.read(b, 0, 28)
        inputStream.close()

        return bytes2hex(b)
    }

    /**
     * 
     */
    @Throws(IOException::class)
    private fun getFileType(filePath: String?): FileType? {
        if (filePath != null) {
            var fileHead: String? = getFileHeader(filePath)
            if (fileHead == null || fileHead.isEmpty()) {
                return null
            }
            fileHead = fileHead.toUpperCase(Locale.getDefault())
            val fileTypes = BcmFileUtils.FileType.values()
            for (type in fileTypes) {
                if (fileHead.startsWith(type.value)) {
                    return type
                }
            }
        }
        return null
    }

    /**
     * @param uri
     * The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    private fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    /**
     * @param uri
     * The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    private fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    /**
     * @param uri
     * The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    private fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

    /**
     * @param uri
     * The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    private fun isGooglePhotosUri(uri: Uri): Boolean {
        return "com.google.android.apps.photos.content" == uri.authority
    }

    private enum class FileType
    /**
     * Constructor.
     *
     * @param value
     */
    constructor(value: String) {

        /**
         * JEPG.
         */
        JPEG("FFD8FF"),


        /**
         * PNG.
         */
        PNG("89504E47"),


        /**
         * GIF.
         */
        GIF("47494638"),


        /**
         * TIFF.
         */
        TIFF("49492A00"),
        /**
         * RTF.
         */
        RTF("7B5C727466"),
        /**
         * DOC
         */
        DOC("D0CF11E0"),
        /**
         * XLS
         */
        XLS("D0CF11E0"),
        /**
         * ACCESS
         */
        MDB("5374616E64617264204A"),


        /**
         * Windows Bitmap.
         */
        BMP("424D"),


        /**
         * CAD.
         */
        DWG("41433130"),


        /**
         * Adobe Photoshop.
         */
        PSD("38425053"),


        /**
         * XML.
         */
        XML("3C3F786D6C"),


        /**
         * HTML.
         */
        HTML("68746D6C3E"),


        /**
         * Adobe Acrobat.
         */
        PDF("255044462D312E"),


        /**
         * ZIP Archive.
         */
        ZIP("504B0304"),


        /**
         * RAR Archive.
         */
        RAR("52617221"),


        /**
         * Wave.
         */
        WAV("57415645"),


        /**
         * AVI.
         */
        AVI("41564920");


        var value = ""

        init {
            this.value = value
        }

        override fun toString(): String {
            return when (this) {
                JPEG -> "image/jpeg"
                BMP -> "image/bmp"
                PNG -> "image/png"
                TIFF -> "image/tiff"
                GIF -> "image/gif"
                WAV -> "video/wav"
                AVI -> "video/avi"
                else -> "application/${name.toLowerCase()}"
            }
        }
    }

}