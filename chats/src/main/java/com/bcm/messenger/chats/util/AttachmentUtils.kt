package com.bcm.messenger.chats.util

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.mms.DocumentSlide
import com.bcm.messenger.common.mms.ImageSlide
import com.bcm.messenger.common.mms.PartAuthority
import com.bcm.messenger.common.mms.Slide
import com.bcm.messenger.common.providers.PersistentBlobProvider
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.MediaUtil
import com.bcm.messenger.utility.BitmapUtils
import com.bcm.messenger.utility.logger.ALog
import java.io.File
import java.io.IOException

/**
 * Created by wjh on 2018/3/29
 */
object AttachmentUtils {

    fun getImageSlide(context: Context, uri: Uri): Slide? {
        if (uri.scheme == "file") {
            return ImageSlide(context, uri, File(uri.path).length())
        }
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val fileName = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                val fileSize = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
                val mimeType = context.contentResolver.getType(uri)
                return ImageSlide(context, uri, fileSize)
            }
        } finally {
            cursor?.close()
        }
        return null
    }

    fun getDocumentSlide(context: Context, uri: Uri, fileName: String, mimeType: String): Slide? {
        val file = File(uri.path)
        return DocumentSlide(context, uri, mimeType, file.length(), fileName)
    }

    fun getDocumentSlide(context: Context, uri: Uri): Slide? {
        if (uri.scheme == "file") {
            val file = File(uri.path)
            var fileName = file.name
            if (fileName.endsWith(".apk", true)) {
                fileName = "$fileName.1"
            }
            return DocumentSlide(context, uri, getMimeType(file.name), file.length(), fileName)
        }
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                var fileName = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                val fileSize = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
                var mimeType = context.contentResolver.getType(uri)
                if (mimeType == null || mimeType.endsWith("android.package-archive",true)) {
                    mimeType = "application/octet-stream"
                }

                if (fileName.endsWith(".apk", true)){
                    fileName = "$fileName.1"
                }
                return DocumentSlide(context, uri, mimeType, fileSize, fileName)
            }
        } finally {
            cursor?.close()
        }
        return null
    }

    fun getMimeType(fileName: String?): String {
        if (fileName == null)
            return ""
        val stringArray = fileName.split(".")
        val suffix = stringArray.last()
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(suffix) ?: ""
        if (mimeType.endsWith("android.package-archive",true)) {
             return "application/octet-stream"
        }
        return mimeType
    }


    fun getAudioContent(context: Context, uri: Uri, fileSize: Long, duration: Long, url: String = ""): AmeGroupMessage.AudioContent? {

        if (PartAuthority.isLocalUri(uri)) {
            val mimeType = PersistentBlobProvider.getMimeType(context, uri)
            return AmeGroupMessage.AudioContent(url, fileSize, duration, mimeType ?: "")
        }

        return null
    }


    fun getAttachmentContent(accountContext: AccountContext, context: Context, contentUri: Uri, path: String?): AmeGroupMessage.AttachmentContent? {

        fun getAttachmentName(path: String): String {
            try {
                return path.substring(path.lastIndexOf(File.separator) + 1, path.length)
            } catch (ex: Exception) {
            }
            return path
        }

        var cursor: Cursor? = null
        try {
            var attachmentName: String? = null
            var attachmentSize = 0L
            var mimeType: String? = MediaUtil.getMimeType(context, contentUri)
            var attachmentPath: String? = path

            if (ContentResolver.SCHEME_FILE == contentUri.scheme) {
                if (attachmentPath == null) {
                    attachmentPath = contentUri.path
                }

            } else {
                cursor = context.contentResolver.query(contentUri, null, null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    attachmentName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                    attachmentSize = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
                }
                if (attachmentPath == null) {
                    attachmentPath = BcmFileUtils.getFileAbsolutePath(accountContext, context, contentUri)
                }
            }

            if (mimeType.isNullOrEmpty()) {
                mimeType = BcmFileUtils.getFileTypeString(attachmentPath)
                if (mimeType.isNullOrEmpty()) {
                    mimeType = MediaUtil.IMAGE_JPEG
                }
            }

            if (attachmentName.isNullOrEmpty()) {
                attachmentName = getAttachmentName(attachmentPath ?: "")
            }

            if (attachmentSize <= 0) {
                attachmentSize = BcmFileUtils.getFileSize(File(attachmentPath))
            }

            return when {

                MediaUtil.isImageType(mimeType) -> {
                    val size = BitmapUtils.getImageSize(attachmentPath ?: "")
                    AmeGroupMessage.ImageContent("", size.width, size.height, mimeType, "", "", "", attachmentSize)

                }
                MediaUtil.isVideoType(mimeType) -> {

                    val duration = getPlayTime(context, contentUri)
                    var previewWidth = 0
                    var previewHeight = 0
                    BcmFileUtils.getVideoFrameInfo(accountContext, context, attachmentPath) { _, width, height ->
                        previewWidth = width
                        previewHeight = height
                    }
                    AmeGroupMessage.VideoContent("", mimeType, attachmentSize, duration, "", contentUri.toString(), previewWidth, previewHeight, "")

                }
                else -> {
                    if (attachmentName.endsWith(".apk", true)) {
                        attachmentName = "$attachmentName.1"
                    }

                    if (mimeType.endsWith("android.package-archive",true)) {
                        mimeType = "application/octet-stream"
                    }
                    AmeGroupMessage.FileContent("", attachmentName, attachmentSize, mimeType)
                }
            }

        } finally {
            cursor?.close()
        }

    }


    fun getPlayTime(context: Context, uri: Uri?): Long {
        val mmr = MediaMetadataRetriever()
        return try {
            if (uri != null) {
                mmr.setDataSource(context, uri)
            }

            val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val d = duration.toLong() / 1000
            if (d > 1) {
                d
            } else {
                1
            }
        } catch (ex: Exception) {
            ALog.e("AttachmentUtils", "getPlayTime error ", ex)
            0L
        } finally {
            mmr.release()
        }
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
}