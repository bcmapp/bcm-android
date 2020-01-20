package com.bcm.messenger.common.core

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.bcmhttp.FileHttp
import com.bcm.messenger.common.bcmhttp.IMHttp
import com.bcm.messenger.common.bcmhttp.interceptor.RedirectInterceptorHelper
import com.bcm.messenger.common.utils.AccountContextMap
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.bcmhttp.callback.Callback
import com.bcm.messenger.utility.bcmhttp.callback.FileDownCallback
import com.bcm.messenger.utility.bcmhttp.callback.JsonDeserializeCallback
import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp
import com.bcm.messenger.utility.bcmhttp.utils.config.ProgressRequestTag
import com.bcm.messenger.utility.bcmhttp.utils.streams.StreamRequestBody
import com.bcm.messenger.utility.bcmhttp.utils.streams.StreamUploadData
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.proguard.NotGuard
import com.google.gson.annotations.SerializedName
import okhttp3.Call
import java.io.File
import java.util.*

/**
 * Upload attachment to AWS S3
 *
 * @param context Context
 * @param type Attachment type, one of private message(pmsg), group message(gmsg), profile(profile)
 * @param file File to upload
 * @param callback Upload callback
 */
object AmeFileUploader : AccountContextMap<AmeFileUploader.AmeFileUploaderImpl>({
    AmeFileUploaderImpl(it)
}) {

    val DCIM_DIRECTORY = Environment.getExternalStorageDirectory().absolutePath + "/DCIM/bcm/"

    init {
        val file = File(DCIM_DIRECTORY)
        if (!file.exists()) {
            file.mkdirs()
        }
    }

    enum class AttachmentType private constructor(val type: String) {
        PRIVATE_MESSAGE("pmsg"),
        GROUP_MESSAGE("gmsg"),
        PROFILE("profile")
    }

    //，，
    class FileUploadResult(val isSuccess: Boolean, val location: String?, val id: String?)

    //
    interface MultiFileUploadCallback {

        fun onFailed(resultMap: Map<String, FileUploadResult>?)

        fun onSuccess(resultMap: Map<String, FileUploadResult>?)
    }

    interface MultiStreamUploadCallback {
        fun onFailed(resultMap: Map<StreamUploadData, FileUploadResult>?)

        fun onSuccess(resultMap: Map<StreamUploadData, FileUploadResult>?)
    }


    interface FileUploadCallback {

        fun onUploadSuccess(url: String?, id: String?) {}

        fun onUploadFailed(filepath: String, msg: String?) {

        }

        fun onProgressChange(currentProgress: Float) {
            //
        }
    }

    interface StreamUploadCallback {
        fun onUploadSuccess(url: String?, id: String?) {}

        fun onUploadFailed(data: StreamUploadData?, msg: String?) {}

        fun onProgressChange(currentProgress: Float) {}
    }

    class FileUploadEntity : NotGuard {

        @SerializedName("location")
        var location: String? = null

        @SerializedName("id")
        var id: String? = null

    }

    class AwsUploadReqEntity(private val type: String, private val region: String) : NotGuard {

        fun toJson(): String {
            return GsonUtils.toJson(this)
        }
    }

    class AwsUploadResEntity : NotGuard {
        val postUrl: String? = null
        val downloadUrl: String? = null
        val fields: List<AwsUploadField>? = null

        inner class AwsUploadField : NotGuard {
            val key: String? = null
            val value: String? = null
        }
    }

    class AmeFileUploaderImpl(accountContext: AccountContext) {

        private val TAG = "AmeFileUploader"

        val ATTACHMENT_URL = "http://ameim.bs2dl.yy.com/attachments/"
        val AWS_UPLOAD_INFO_PATH = "/v1/attachments/s3/upload_certification" // Path to get AWS S3 certification data in IM server

        val DOWNLOAD_PATH: String
        val AUDIO_DIRECTORY: String
        val THUMBNAIL_DIRECTORY: String
        val DOCUMENT_DIRECTORY: String
        val VIDEO_DIRECTORY: String
        val MAP_DIRECTORY: String
        val ENCRYPT_DIRECTORY: String
        val DECRYPT_DIRECTORY: String//（）
        val TEMP_DIRECTORY: String//
        val CHAT_FILE_DIRECTORY: String // Directory of encrypted chat files with MasterSecret
        val DCIM_DIRECTORY get() = AmeFileUploader.DCIM_DIRECTORY

        init {
            DOWNLOAD_PATH = accountContext.accountDir
            AUDIO_DIRECTORY = "$DOWNLOAD_PATH/audio"
            THUMBNAIL_DIRECTORY = "$DOWNLOAD_PATH/thumbnail"
            DOCUMENT_DIRECTORY = "$DOWNLOAD_PATH/document"
            VIDEO_DIRECTORY = "$DOWNLOAD_PATH/video"
            MAP_DIRECTORY = "$DOWNLOAD_PATH/map"
            ENCRYPT_DIRECTORY = "$DOWNLOAD_PATH/encrypt"
            DECRYPT_DIRECTORY = "$DOWNLOAD_PATH/decrypt"
            TEMP_DIRECTORY = "$DOWNLOAD_PATH/temp"
            CHAT_FILE_DIRECTORY = "$DOWNLOAD_PATH/chat-files"

            var f = File(AUDIO_DIRECTORY)
            if (!f.exists()) {
                f.mkdirs()
            }

            f = File(DOCUMENT_DIRECTORY)
            if (!f.exists()) {
                f.mkdirs()
            }

            f = File(THUMBNAIL_DIRECTORY)
            if (!f.exists()) {
                f.mkdirs()
            }

            f = File(VIDEO_DIRECTORY)
            if (!f.exists()) {
                f.mkdirs()
            }

            f = File(MAP_DIRECTORY)
            if (!f.exists()) {
                f.mkdirs()
            }

            f = File(ENCRYPT_DIRECTORY)
            if (!f.exists()) {
                f.mkdirs()
            }

            f = File(DECRYPT_DIRECTORY)
            if (!f.exists()) {
                f.mkdirs()
            }

            f = File(TEMP_DIRECTORY)
            if (!f.exists()) {
                f.mkdirs()
            }

            f = File(CHAT_FILE_DIRECTORY)
            if (!f.exists()) {
                f.mkdirs()
            }
        }

        private fun getMimeType(context: Context, path: String): String {
            try {
                val uri = Uri.fromFile(File(path))
                val mimeType = getMimeType(context, uri)
                if (mimeType != null) {
                    return mimeType
                }

            } catch (ex: Exception) {
                ALog.e(TAG, "getMimeType fail", ex)
            }

            return "application/octet-stream"
        }

        private fun getMimeType(context: Context, uri: Uri?): String? {
            if (uri == null) {
                return null
            }
            var type = context.contentResolver.getType(uri)
            if (type == null) {
                val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase())
            }

            return getCorrectedMimeType(type)
        }

        private fun getCorrectedMimeType(mimeType: String?): String? {
            if (mimeType == null) {
                return null
            }

            when (mimeType) {
                "image/jpg" -> return if (MimeTypeMap.getSingleton().hasMimeType("image/jpeg"))
                    "image/jpeg"
                else
                    mimeType
                else -> return mimeType
            }
        }


        /**
         * ，, map ，， url
         * onSuccess ， onFailed
         *
         * @param context Context.
         * @param type Attachment type
         * @param filePaths Will be uploaded files paths.
         * @param callback Upload callback.
         */
        fun uploadMultiFileToAws(accountContext: AccountContext,
                                 context: Context,
                                 type: AttachmentType,
                                 filePaths: List<String>,
                                 callback: MultiFileUploadCallback) {
            val failedResultMap = HashMap<String, FileUploadResult>()
            val successResultMap = HashMap<String, FileUploadResult>()
            for (filePath in filePaths) {
                uploadAttachmentToAws(accountContext, context, type, File(filePath), object : FileUploadCallback {
                    override fun onUploadSuccess(url: String?, id: String?) {
                        synchronized(TAG) {
                            successResultMap[filePath] = FileUploadResult(true, url, id ?: "")
                            if (successResultMap.size + failedResultMap.size == filePaths.size) {
                                if (failedResultMap.size == 0) {
                                    callback.onSuccess(successResultMap)
                                } else {
                                    failedResultMap.putAll(successResultMap)
                                    callback.onFailed(failedResultMap)
                                }

                            }
                        }
                    }

                    override fun onUploadFailed(filepath: String, msg: String?) {
                        ALog.w(TAG, msg)
                        synchronized(TAG) {
                            failedResultMap[filePath] = FileUploadResult(false, "", "")
                            if (failedResultMap.size + successResultMap.size == filePaths.size) {
                                failedResultMap.putAll(successResultMap)
                                callback.onFailed(failedResultMap)
                            }
                        }
                    }
                })
            }
        }

        fun uploadMultiStreamToAws(accountContext: AccountContext,
                                   type: AttachmentType,
                                   uploadDataList: List<StreamUploadData>,
                                   callback: MultiStreamUploadCallback) {
            val failedResultMap = HashMap<StreamUploadData, FileUploadResult>()
            val successResultMap = HashMap<StreamUploadData, FileUploadResult>()
            for (data in uploadDataList) {
                uploadStreamToAws(accountContext, type, data, object : StreamUploadCallback {
                    override fun onUploadSuccess(url: String?, id: String?) {
                        synchronized(TAG) {
                            successResultMap[data] = FileUploadResult(true, url, id ?: "")
                            if (successResultMap.size + failedResultMap.size == uploadDataList.size) {
                                if (failedResultMap.size == 0) {
                                    callback.onSuccess(successResultMap)
                                } else {
                                    failedResultMap.putAll(successResultMap)
                                    callback.onFailed(failedResultMap)
                                }

                            }
                        }
                    }

                    override fun onUploadFailed(data: StreamUploadData?, msg: String?) {
                        ALog.w(TAG, msg)
                        data ?: return
                        synchronized(TAG) {
                            failedResultMap[data] = FileUploadResult(false, "", "")
                            if (failedResultMap.size + successResultMap.size == uploadDataList.size) {
                                failedResultMap.putAll(successResultMap)
                                callback.onFailed(failedResultMap)
                            }
                        }
                    }
                }, Callback.THREAD_CURRENT)
            }
        }


        @JvmOverloads
        fun uploadAttachmentToAws(accountContext: AccountContext,
                                  context: Context,
                                  type: AttachmentType,
                                  file: File,
                                  callback: FileUploadCallback,
                                  threadMode: Int = Callback.THREAD_CURRENT) {

            getAwsUploadInfo(accountContext, type, object : JsonDeserializeCallback<AwsUploadResEntity>() {
                override fun onError(call: Call, e: Exception, id: Long) {
                    callback.onUploadFailed(file.absolutePath, e.message ?: "")
                }

                override fun onResponse(response: AwsUploadResEntity, id: Long) {
                    realUploadFileToAws(context, response.postUrl, response.fields!!, file, object : com.bcm.messenger.utility.bcmhttp.callback.FileUploadCallback<Void>() {
                        override fun callInThreadMode(): Int {
                            return threadMode
                        }

                        override fun onError(call: Call, e: Exception, id: Long) {
                            callback.onUploadFailed(file.absolutePath, e.message ?: "")
                        }

                        override fun onResponse(uploadResponse: Void, id: Long) {
                            callback.onUploadSuccess(response.downloadUrl, id.toString())
                        }

                        override fun inProgress(progress: Int, total: Long, id: Long) {
                            callback.onProgressChange(progress.toFloat())
                        }
                    })
                }
            })
        }

        fun uploadStreamToAws(accountContext: AccountContext,
                              type: AttachmentType,
                              data: StreamUploadData,
                              callback: StreamUploadCallback,
                              threadMode: Int) {

            getAwsUploadInfo(accountContext, type, object : JsonDeserializeCallback<AwsUploadResEntity>() {
                override fun onError(call: Call, e: Exception, id: Long) {
                    callback.onUploadFailed(data, e.message ?: "")
                }

                override fun onResponse(response: AwsUploadResEntity, id: Long) {
                    realUploadFileToAws(response.postUrl, response.fields!!, data, object : com.bcm.messenger.utility.bcmhttp.callback.FileUploadCallback<Void>() {
                        override fun callInThreadMode(): Int {
                            return threadMode
                        }

                        override fun onError(call: Call, e: Exception, id: Long) {
                            callback.onUploadFailed(data, e.message ?: "")
                        }

                        override fun onResponse(uploadResponse: Void, id: Long) {
                            callback.onUploadSuccess(response.downloadUrl, id.toString())
                        }

                        override fun inProgress(progress: Int, total: Long, id: Long) {
                            callback.onProgressChange(progress.toFloat())
                        }
                    })
                }
            })
        }

        /**
         * （）
         * @param context
         * @param file
         * @param callback
         */
        fun uploadGroupAvatar(accountContext: AccountContext,
                              context: Context,
                              file: File,
                              callback: FileUploadCallback) {
            uploadAttachmentToAws(accountContext, context, AttachmentType.PROFILE, file, callback, Callback.THREAD_MAIN)
        }

        private fun getAwsUploadInfo(accountContext: AccountContext,
                                     type: AttachmentType,
                                     callback: JsonDeserializeCallback<AwsUploadResEntity>) {
            val region = RedirectInterceptorHelper.imServerInterceptor.getCurrentServer().area
            val body = AwsUploadReqEntity(type.type, Integer.toString(region)).toJson()

            IMHttp.get(accountContext).postString()
                    .url(BcmHttpApiHelper.getApi(AWS_UPLOAD_INFO_PATH))
                    .addHeader("content-type", "application/json")
                    .content(body)
                    .build()
                    .writeTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                    .readTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                    .connTimeOut(BaseHttp.DEFAULT_MILLISECONDS)
                    .enqueue(callback)
        }

        private fun realUploadFileToAws(context: Context, url: String?, formFields: List<AwsUploadResEntity.AwsUploadField>, file: File, callback: com.bcm.messenger.utility.bcmhttp.callback.FileUploadCallback<Void>) {
            val builder = FileHttp.postForm()
            builder.url(url)
            for (field in formFields) {
                builder.addFormData(field.key!!, field.value!!)
            }
            // AWS says file must be the last field
            builder.addFormFile("file", file.name, file, getMimeType(context, file.absolutePath))
                    .tag(ProgressRequestTag(true, false))
                    .build()
                    .writeTimeOut(BaseHttp.DEFAULT_FILE_MILLISECONDS)
                    .readTimeOut(BaseHttp.DEFAULT_FILE_MILLISECONDS)
                    .connTimeOut(BaseHttp.DEFAULT_FILE_MILLISECONDS)
                    .enqueue(callback)
        }

        private fun realUploadFileToAws(url: String?, formFields: List<AwsUploadResEntity.AwsUploadField>, data: StreamUploadData, callback: com.bcm.messenger.utility.bcmhttp.callback.FileUploadCallback<Void>) {
            val builder = FileHttp.postForm()
            builder.url(url)
            for (field in formFields) {
                builder.addFormData(field.key!!, field.value!!)
            }
            // AWS says file must be the last field
            builder.addFormPart("file", data.name, StreamRequestBody(data.inputStream, data.mimeType, data.dataSize))
                    .tag(ProgressRequestTag(true, false))
                    .build()
                    .writeTimeOut(BaseHttp.DEFAULT_FILE_MILLISECONDS)
                    .readTimeOut(BaseHttp.DEFAULT_FILE_MILLISECONDS)
                    .connTimeOut(BaseHttp.DEFAULT_FILE_MILLISECONDS)
                    .enqueue(callback)
        }

        /**
         * （）
         * @param context
         * @param url
         * @param callback
         */
        fun downloadFile(context: Context, url: String, callback: FileDownCallback) {
            FileHttp.get()
                    .url(url)
                    .tag(ProgressRequestTag(false, true))
                    .build()
                    .writeTimeOut(BaseHttp.DEFAULT_FILE_MILLISECONDS)
                    .readTimeOut(BaseHttp.DEFAULT_FILE_MILLISECONDS)
                    .connTimeOut(BaseHttp.DEFAULT_FILE_MILLISECONDS)
                    .enqueue(callback)
        }
    }
}
