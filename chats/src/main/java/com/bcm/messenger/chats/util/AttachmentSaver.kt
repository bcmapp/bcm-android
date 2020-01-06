package com.bcm.messenger.chats.util

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.appcompat.app.AppCompatActivity
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.core.AmeFileUploader
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.mms.GlideApp
import com.bcm.messenger.common.mms.PartAuthority
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.MediaUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.StorageUtil
import com.bcm.messenger.utility.Util
import com.bcm.messenger.utility.logger.ALog
import com.bumptech.glide.request.target.Target
import com.orhanobut.logger.Logger
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by wjh on 2018/6/14
 */
object AttachmentSaver {
    fun saveAttachmentOnAsync(activity: AppCompatActivity, mediaUrl: String?, mediaType: String?, name: String?) {
        AmePopup.loading.show(activity)
        Observable.create(ObservableOnSubscribe<Pair<Boolean, File?>> {
            try {
                val url = mediaUrl ?: throw Exception("media url is null")
                val type = mediaType ?: throw Exception("media type is null")
                val resultFile = saveAttachment(AppContextHolder.APP_CONTEXT, url, type, name)
                        ?: throw Exception("save file is null")
                val result = Pair(true, resultFile)
                it.onNext(result)
            } catch (ex: Exception) {
                Logger.e(ex, "MediaPreviewActivity save error")
                it.onNext(Pair(false, null))
            } finally {
                it.onComplete()
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnTerminate {
                    AmePopup.loading.dismiss()
                }
                .subscribe { result ->
                    if (result.first) {
                        AmePopup.result.succeed(activity, activity.getString(R.string.chats_media_preview_save_success), true) {
                        }
                    } else {
                        AmePopup.result.failure(activity, activity.getString(R.string.chats_media_preview_save_fail), true) {
                        }
                    }
                }
    }

    fun saveAttachmentOnAsync(activity: AppCompatActivity, masterSecret: MasterSecret, mediaUri: Uri?, mediaType: String?, name: String?) {
        AmePopup.loading.show(activity)
        Observable.create(ObservableOnSubscribe<Pair<Boolean, File?>> {
            try {
                val uri = mediaUri ?: throw Exception("media uri is null")
                val type = mediaType ?: throw Exception("media type is null")
                val resultFile = saveAttachment(AppContextHolder.APP_CONTEXT, masterSecret, uri, type, name)
                        ?: throw Exception("save file is null")
                val result = Pair(true, resultFile)
                it.onNext(result)
            } catch (ex: Exception) {
                Logger.e(ex, "MediaPreviewActivity save error")
                it.onNext(Pair(false, null))
            } finally {
                it.onComplete()
            }

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnTerminate {
                    AmePopup.loading.dismiss()
                }
                .subscribe({ result ->
                    if (result.first) {
                        AmePopup.result.succeed(activity, activity.getString(R.string.chats_media_preview_save_success), true) {
                        }
                    } else {
                        AmePopup.result.failure(activity, activity.getString(R.string.chats_media_preview_save_fail), true) {
                        }
                    }
                }, {
                })
    }

    fun saveAttachment(context: Context, url: String, contentType: String?, name: String?): File? {
        if (!StorageUtil.canWriteInExternalStorageDir()) {
            return null
        }
        try {
            val targetFile = GlideApp.with(context).load(url).downloadOnly(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL).get()
            return saveAttachment(context, targetFile, contentType, name)

        } catch (ex: Exception) {
            ALog.e("AttachmentSaver", "saveAttachment fail", ex)
        }
        return null
    }

    fun saveAttachment(context: Context, masterSecret: MasterSecret, mediaUri: Uri, mediaType: String?, name: String?, saveAsTemp: Boolean = false): File? {
        if (!StorageUtil.canWriteInExternalStorageDir()) {
            return null
        }
        try {
            val attachment = Attachment(mediaUri, mediaType ?: "", System.currentTimeMillis(), name)
            return saveAttachment(context, masterSecret, attachment, saveAsTemp)

        } catch (ex: Exception) {
            ALog.e("AttachmentSaver", "saveAttachment fail", ex)
        }
        return null
    }

    /**
     * create temp
     */
    fun saveTempAttachment(context: Context, masterSecret: MasterSecret, uri: Uri, contentType: String?, name: String?): File? {
        try {
            val newContentType = if (contentType.isNullOrEmpty()) {
                MediaUtil.getMimeType(context, uri) ?: ""
            } else {
                contentType
            }
            val fileName = if (name.isNullOrEmpty()) {
                "${uri.path.replace("/", "")}.${MimeTypeMap.getSingleton().getExtensionFromMimeType(contentType)}"
            } else {
                name
            }

            val attachment = Attachment(uri, newContentType, System.currentTimeMillis(), fileName)
            return saveAttachment(context, masterSecret, attachment, true)

        } catch (ex: Exception) {
            ALog.e("AttachmentSaver", "saveAttachment fail", ex)
        }
        return null
    }


    @Throws(Exception::class)
    private fun saveAttachment(context: Context, attachment: File, sourceType: String?, name: String?): File? {
        var outputStream: FileOutputStream? = null
        try {
            val contentType = if (sourceType.isNullOrEmpty()) {
                BcmFileUtils.getFileTypeString(attachment.absolutePath)
            } else {
                sourceType
            }
            var fileName = if (name.isNullOrEmpty()) {
                generateOutputFileName(context, contentType, System.currentTimeMillis())
            } else {
                name
            }
            fileName = File(fileName).name

            val outputDirectory = createOutputDirectoryFromContentType(contentType)
            val attachmentFile = createOutputFile(outputDirectory, fileName)
            if (attachmentFile.exists() && attachmentFile.length() > 0) {
                return attachmentFile
            }

            val inputStream = FileInputStream(attachment)
            outputStream = FileOutputStream(attachmentFile)
            Util.copy(inputStream, outputStream)

            MediaScannerConnection.scanFile(context, arrayOf(attachmentFile.getAbsolutePath()),
                    arrayOf(contentType), null)

            return attachmentFile
        } finally {
            outputStream?.close()
        }
    }

    @Throws(Exception::class)
    private fun saveAttachment(context: Context, masterSecret: MasterSecret, attachment: Attachment, saveAsTemp: Boolean = false): File? {

        var outputStream: FileOutputStream? = null
        try {
            val contentType = if (attachment.contentType.isEmpty()) {
                MediaUtil.getMimeType(context, attachment.uri) ?: ""
            } else {
                attachment.contentType
            }
            var fileName = if (attachment.fileName.isNullOrEmpty()) {
                generateOutputFileName(context, contentType, attachment.date)
            } else {
                attachment.fileName
            }
            fileName = File(fileName).name

            val outputDirectory = createOutputDirectoryFromContentType(contentType, saveAsTemp)
            val attachmentFile = createOutputFile(outputDirectory, fileName, saveAsTemp)
            if (attachmentFile.exists() && attachmentFile.length() > 0) {
                return attachmentFile
            }

            val inputStream = if (attachment.uri.toString().startsWith("/storage")) {
                PartAuthority.getAttachmentStream(context, masterSecret, BcmFileUtils.getFileUri(attachment.uri.toString()))
            } else {
                PartAuthority.getAttachmentStream(context, masterSecret, attachment.uri)
                        ?: return null
            }

            outputStream = FileOutputStream(attachmentFile)
            Util.copy(inputStream, outputStream)

            MediaScannerConnection.scanFile(context, arrayOf(attachmentFile.absolutePath),
                    arrayOf(contentType), null)

            return attachmentFile
        } finally {
            outputStream?.close()
        }
    }

    private fun createOutputDirectoryFromContentType(contentType: String?, saveAsTemp: Boolean = false): File {
        if (saveAsTemp) {
            val path = File(AmeFileUploader.TEMP_DIRECTORY)
            if (!path.exists()) {
                path.mkdir()
            }
            return path
        }

        val outputDirectory = if (MediaUtil.isVideoType(contentType)) {
            StorageUtil.getVideoDir()
        } else if (MediaUtil.isAudioType(contentType)) {
            StorageUtil.getAudioDir()
        } else if (MediaUtil.isImageType(contentType)) {
            StorageUtil.getImageDir()
        } else {
            StorageUtil.getDownloadDir()
        }
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        }
        return outputDirectory
    }

    private fun generateOutputFileName(context: Context, contentType: String, timestamp: Long): String {
        val extension = if (contentType.isEmpty()) {
            "dat"
        } else {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(contentType)
        }
        val base = context.getString(R.string.common_app_prefix) + SimpleDateFormat("yyyy_MM_dd_HHmmss", Locale.getDefault()).format(timestamp)
        return "$base.$extension"
    }

    @Throws(IOException::class)
    private fun createOutputFile(outputDirectory: File, fileName: String, isTemp: Boolean = false): File {
        val fileParts = getFileNameParts(fileName)
        val base = fileParts[0]
        val extension = fileParts[1]

        var outputFile = File(outputDirectory, "$base.$extension")
        if (isTemp) {
            return outputFile
        }
        var i = 0
        while (outputFile.exists()) {
            outputFile = File(outputDirectory, base + "-" + ++i + "." + extension)
        }
        if (outputFile.isHidden) {
            throw IOException("Specified name would not be visible")
        }
        return outputFile
    }

    private fun getFileNameParts(fileName: String): Array<String?> {

        val result = arrayOfNulls<String>(2)
        val tokens = fileName.split("\\.(?=[^\\.]+$)".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        result[0] = tokens[0]

        if (tokens.size > 1)
            result[1] = tokens[1]
        else
            result[1] = ""

        return result
    }

    class Attachment(var uri: Uri, var contentType: String, var date: Long, var fileName: String?)
}