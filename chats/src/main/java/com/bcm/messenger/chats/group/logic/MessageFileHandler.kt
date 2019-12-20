package com.bcm.messenger.chats.group.logic

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Looper
import com.bcm.messenger.chats.util.GroupAttachmentProgressEvent
import com.bcm.messenger.chats.util.HistoryGroupAttachmentProgressEvent
import com.bcm.messenger.common.core.AmeFileUploader
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.grouprepository.model.AmeHistoryMessageDetail
import com.bcm.messenger.common.grouprepository.room.entity.GroupMessage
import com.bcm.messenger.common.mms.PartAuthority
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.MediaUtil
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.crypto.encrypt.ChatFileEncryptDecryptUtil
import com.bcm.messenger.common.crypto.encrypt.EncryptMediaUtils
import com.bcm.messenger.common.crypto.encrypt.FileInfo
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.bcmhttp.callback.Callback
import com.bcm.messenger.utility.bcmhttp.callback.FileDownCallback
import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp
import com.bcm.messenger.utility.bcmhttp.utils.streams.StreamUploadData
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import okhttp3.Call
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.io.FileInputStream
import java.util.*

/**
 * Created by bcm.social.01 on 2018/8/8.
 */
object MessageFileHandler {

    private val TAG = "MessageFileHandler"

    interface MessageFileCallback {
        fun onResult(success: Boolean, uri: Uri?)
    }

    data class UploadResult(var url: String, val fileInfo: FileInfo, val sign: String, val width: Int, val height: Int)

    private var mThumbnailCallbackMap = WeakHashMap<AmeGroupMessageDetail, MutableSet<(MessageFileCallback)>>()
    private var mAttachmentCallbackMap = WeakHashMap<AmeGroupMessageDetail, MutableSet<MessageFileCallback>>()


    private fun addCallback(map: MutableMap<AmeGroupMessageDetail, MutableSet<MessageFileCallback>>,
                            messageDetail: AmeGroupMessageDetail, callback: MessageFileCallback?) {
        if (callback == null) return
        var set = map[messageDetail]
        if (set == null) {
            set = mutableSetOf()
            map[messageDetail] = set
        }
        set.add(callback)
    }


    @SuppressLint("CheckResult")
    fun downloadThumbnail(messageDetail: AmeGroupMessageDetail, callback: MessageFileCallback? = null) {
        if (messageDetail.message.content !is AmeGroupMessage.ThumbnailContent) {
            return downloadAttachment(messageDetail, callback)
        }

        addCallback(mThumbnailCallbackMap, messageDetail, callback)
        if (messageDetail.isThumbnailDownloading) {
            ALog.i(TAG, "message indexId:${messageDetail.indexId} gid: ${messageDetail.gid} is thumbnailDownloading")
            return
        }

        var useThumbnail = true


        fun localResponse(success: Boolean, uri: Uri?) {

            if (Looper.myLooper() == Looper.getMainLooper()) {
                ALog.logForSecret(TAG, "downloadThumbnail localResponse success: $success, uri: $uri")
                messageDetail.isThumbnailDownloading = false
                if (uri != null && !useThumbnail) {
                    messageDetail.attachmentUri = uri.toString()
                }

                mThumbnailCallbackMap[messageDetail]?.forEach {
                    it.onResult(success, uri)
                }
                mThumbnailCallbackMap.remove(messageDetail)

            } else {
                AmeDispatcher.mainThread.dispatch {
                    ALog.logForSecret(TAG, "downloadThumbnail localResponse success: $success, uri: $uri")
                    messageDetail.isThumbnailDownloading = false
                    if (uri != null && !useThumbnail) {
                        messageDetail.attachmentUri = uri.toString()
                    }
                    mThumbnailCallbackMap[messageDetail]?.forEach {
                        it.onResult(success, uri)
                    }
                    mThumbnailCallbackMap.remove(messageDetail)
                }
            }
        }

        val resultUri = messageDetail.toAttachmentUri()
        if (resultUri != null) {
            return localResponse(true, resultUri)
        }
        val content = messageDetail.message.content as AmeGroupMessage.ThumbnailContent
        val pathPair = content.getPath()
        val thumbnailPathPair = content.getThumbnailPath()

        var isExist = false
        val thumb = if (content.thumbnail_url.isNullOrEmpty() || MediaUtil.isGif(content.mimeType)) {
            ALog.w(TAG, "downloadThumbnail use content url")
            useThumbnail = false
            isExist = content.isExist()
            content.url
        } else {
            isExist = content.isThumbnailExist()
            content.thumbnail_url
        }
        val destPath = if (useThumbnail) {
            thumbnailPathPair.second + File.separator + content.getThumbnailExtension()
        } else {
            pathPair.second + File.separator + content.getExtension()
        }

        if (isExist) {
            ALog.w(TAG, "downloadThumbnail thumbnail exist, indexId: ${messageDetail.indexId}, gid: ${messageDetail.gid}")
            return localResponse(true, BcmFileUtils.getFileUri(destPath))
        }

        if (thumb.isNullOrEmpty()) {
            ALog.w(TAG, "downloadThumbnail thumb url is error, indexId: ${messageDetail.indexId}, gid: ${messageDetail.gid}")
            return localResponse(false, null)
        }

        messageDetail.isThumbnailDownloading = true
        try {
            AmeFileUploader.downloadFile(AppContextHolder.APP_CONTEXT, thumb,
                    object : FileDownCallback(if (useThumbnail) thumbnailPathPair.first else pathPair.first, if (useThumbnail) content.getThumbnailExtension() else content.getExtension()) {
                        override fun inProgress(progress: Int, total: Long, id: Long) {
                            messageDetail.isThumbnailDownloading = progress != 100
                            if (messageDetail is AmeHistoryMessageDetail) {
                                EventBus.getDefault().post(HistoryGroupAttachmentProgressEvent(thumb, GroupAttachmentProgressEvent.ACTION_THUMBNAIL_DOWNLOADING, progress.toFloat() / 100, total))
                            } else {
                                EventBus.getDefault().post(GroupAttachmentProgressEvent(messageDetail.gid, messageDetail.indexId, GroupAttachmentProgressEvent.ACTION_THUMBNAIL_DOWNLOADING, progress.toFloat() / 100, total))
                            }
                        }

                        override fun onError(call: Call?, e: Exception?, id: Long) {
                            ALog.e(TAG, "downloadThumbnail error", e)
                            localResponse(false, null)
                            if (e is BaseHttp.HttpErrorException && (e.code == 403 || e.code == 404)) {
                                MessageDataManager.updateMessageSendStateByIndex(messageDetail.gid, messageDetail.indexId, GroupMessage.FILE_NOT_FOUND)
                            } else {
                                MessageDataManager.updateMessageSendStateByIndex(messageDetail.gid, messageDetail.indexId, GroupMessage.THUMB_DOWNLOAD_FAIL)
                            }
                        }

                        override fun onResponse(response: File?, id: Long) {
                            if (response == null) {
                                MessageDataManager.updateMessageSendStateByIndex(messageDetail.gid, messageDetail.indexId, GroupMessage.FILE_DOWNLOAD_FAIL)
                                localResponse(false, null)
                            } else {

                                try {
                                    val fileInfo = ChatFileEncryptDecryptUtil.decryptAndSaveFile(
                                            BCMEncryptUtils.getMasterSecret(AppContextHolder.APP_CONTEXT),
                                            response,
                                            messageDetail,
                                            if (useThumbnail) ChatFileEncryptDecryptUtil.FileType.GROUP_THUMB else ChatFileEncryptDecryptUtil.FileType.GROUP)

                                    // Notice: Remove check whether message is history message after GroupMessage entity updated.
                                    // Author: Kin
                                    if (!useThumbnail) {
                                        if (messageDetail !is AmeHistoryMessageDetail) {
                                            MessageDataManager.updateMessageAttachmentUri(messageDetail.gid, messageDetail.indexId, fileInfo)
                                            localResponse(true, PartAuthority.getGroupAttachmentUri(messageDetail.gid, messageDetail.indexId))
                                        } else {
                                            localResponse(true, Uri.fromFile(fileInfo.file))
                                        }
                                    } else {
                                        if (messageDetail !is AmeHistoryMessageDetail) {
                                            MessageDataManager.updateMessageThumbnailUri(messageDetail.gid, messageDetail.indexId, fileInfo)
                                            localResponse(true, PartAuthority.getGroupThumbnailUri(messageDetail.gid, messageDetail.indexId))
                                        } else {
                                            localResponse(true, Uri.fromFile(fileInfo.file))
                                        }
                                    }
                                    MessageDataManager.updateMessageSendStateByIndex(messageDetail.gid, messageDetail.indexId,
                                            if (messageDetail.isSendByMe) GroupMessage.SEND_SUCCESS else GroupMessage.RECEIVE_SUCCESS)
                                } catch (ex: Exception) {
                                    ALog.e(TAG, "downloadThumbnail error", ex)
                                    BcmFileUtils.delete(destPath)
                                    localResponse(false, null)
                                }
                            }
                        }
                    })
        } catch (ex: Throwable) {
            ALog.e(TAG, "downloadThumbnail error", ex)
            localResponse(false, null)
        }
    }


    @SuppressLint("CheckResult")
    fun downloadThumbnail(gid: Long, indexId: Long, content: AmeGroupMessage.ThumbnailContent, keyVersion: Long, callback: MessageFileCallback? = null) {


        fun localResponse(success: Boolean, uri: Uri?) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                callback?.onResult(success, uri)
            } else {
                AmeDispatcher.mainThread.dispatch {
                    callback?.onResult(success, uri)
                }
            }
        }

        val pathPair = content.getPath()
        val thumbnailPathPair = content.getThumbnailPath()
        var useThumbnail = true
        var isExist = false
        val thumb = if (content.thumbnail_url.isNullOrEmpty() || MediaUtil.isGif(content.mimeType)) {
            useThumbnail = false
            isExist = content.isExist()
            content.url
        } else {
            isExist = content.isThumbnailExist()
            content.thumbnail_url
        }
        val destPath = if (useThumbnail) {
            thumbnailPathPair.second + File.separator + content.getThumbnailExtension()
        } else {
            pathPair.second + File.separator + content.getExtension()
        }

        if (isExist) {
            ALog.w(TAG, "downloadThumbnail thumbnail exist, indexId: $indexId, gid: $gid")
            return localResponse(true, BcmFileUtils.getFileUri(destPath))
        }

        if (thumb.isNullOrEmpty()) {
            ALog.w(TAG, "downloadThumbnail thumb url is error, indexId: $indexId, gid: $gid")
            return localResponse(false, null)
        }

        try {
            AmeFileUploader.downloadFile(AppContextHolder.APP_CONTEXT, thumb,
                    object : FileDownCallback(if (useThumbnail) thumbnailPathPair.first else pathPair.first, if (useThumbnail) content.getThumbnailExtension() else content.getExtension()) {

                        override fun inProgress(progress: Int, total: Long, id: Long) {
                            EventBus.getDefault().post(GroupAttachmentProgressEvent(gid, indexId, GroupAttachmentProgressEvent.ACTION_THUMBNAIL_DOWNLOADING, progress.toFloat() / 100, total))
                        }

                        override fun onError(call: Call?, e: Exception?, id: Long) {
                            ALog.e(TAG, "downloadThumbnail error", e)
                            localResponse(false, null)
                            if (e is BaseHttp.HttpErrorException && (e.code == 403 || e.code == 404)) {
                                MessageDataManager.updateMessageSendStateByIndex(gid, indexId, GroupMessage.FILE_NOT_FOUND)
                            } else {
                                MessageDataManager.updateMessageSendStateByIndex(gid, indexId, GroupMessage.THUMB_DOWNLOAD_FAIL)
                            }
                        }

                        override fun onResponse(response: File?, id: Long) {
                            if (response == null) {
                                MessageDataManager.updateMessageSendStateByIndex(gid, indexId, GroupMessage.FILE_DOWNLOAD_FAIL)
                                localResponse(false, null)
                            } else {
                                try {
                                    val fileInfo = ChatFileEncryptDecryptUtil.decryptAndSaveFile(
                                            BCMEncryptUtils.getMasterSecret(AppContextHolder.APP_CONTEXT),
                                            response,
                                            AmeGroupMessageDetail().apply {
                                                message = AmeGroupMessage(
                                                        if (content is AmeGroupMessage.ImageContent) AmeGroupMessage.IMAGE else AmeGroupMessage.VIDEO,
                                                        content
                                                )
                                            },
                                            if (useThumbnail) ChatFileEncryptDecryptUtil.FileType.GROUP_THUMB else ChatFileEncryptDecryptUtil.FileType.GROUP_THUMB
                                    )

                                    if (!useThumbnail) {
                                        MessageDataManager.updateMessageAttachmentUri(gid, indexId, fileInfo)
                                        localResponse(true, PartAuthority.getGroupAttachmentUri(gid, indexId))
                                    } else {
                                        MessageDataManager.updateMessageThumbnailUri(gid, indexId, fileInfo)
                                        localResponse(true, PartAuthority.getGroupThumbnailUri(gid, indexId))
                                    }

                                } catch (ex: Exception) {
                                    ALog.e(TAG, "downloadThumbnail error", ex)
                                    BcmFileUtils.delete(destPath)
                                    localResponse(false, null)
                                }
                            }
                        }
                    })
        } catch (ex: Exception) {
            ALog.e(TAG, "downloadThumbnail error", ex)
            localResponse(false, null)
        }
    }


    @SuppressLint("CheckResult")
    fun downloadAttachment(messageDetail: AmeGroupMessageDetail, callback: MessageFileCallback?) {

        addCallback(mAttachmentCallbackMap, messageDetail, callback)
        if (messageDetail.isAttachmentDownloading) {
            ALog.i(TAG, "message index: ${messageDetail.indexId} gid:${messageDetail.gid} isAttachmentDownloading")
            return
        }


        fun localResponse(success: Boolean, uri: Uri?) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                ALog.i(TAG, "downloadAttachment localResponse success: $success, uri: $uri")
                messageDetail.isAttachmentDownloading = false
                if (uri != null) {
                    messageDetail.attachmentUri = uri.toString()
                }

                mAttachmentCallbackMap[messageDetail]?.forEach {
                    it.onResult(success, uri)
                }
                mAttachmentCallbackMap.remove(messageDetail)
            } else {
                AmeDispatcher.mainThread.dispatch {
                    ALog.i(TAG, "downloadAttachment localResponse success: $success, uri: $uri")
                    messageDetail.isAttachmentDownloading = false
                    if (uri != null) {
                        messageDetail.attachmentUri = uri.toString()
                    }

                    ALog.i(TAG, "downloadAttachment localResponse callback size: ${mAttachmentCallbackMap[messageDetail]?.size}")
                    mAttachmentCallbackMap[messageDetail]?.forEach {
                        it.onResult(success, uri)
                    }
                    mAttachmentCallbackMap.remove(messageDetail)
                }
            }
        }

        val resultUri = messageDetail.toAttachmentUri()
        if (resultUri != null) {
            return localResponse(true, resultUri)
        }
        val content = messageDetail.message.content as AmeGroupMessage.AttachmentContent
        val pathPair = content.getPath()

        val destPath = pathPair.second + File.separator + content.getExtension()
        if (content.isExist()) {
            ALog.w(TAG, "downloadAttachment attachment exist, indexId: ${messageDetail.indexId}, gid: ${messageDetail.gid}")
            return localResponse(true, BcmFileUtils.getFileUri(destPath))
        }

        if (content.url.isNullOrEmpty()) {
            ALog.w(TAG, "downloadAttachment url is error, indexId: ${messageDetail.indexId}, gid: ${messageDetail.gid}")
            return localResponse(false, null)
        }

        try {
            messageDetail.isAttachmentDownloading = true
            AmeFileUploader.downloadFile(AppContextHolder.APP_CONTEXT, content.url, object : FileDownCallback(pathPair.first, content.getExtension()) {
                override fun inProgress(progress: Int, total: Long, id: Long) {
                    messageDetail.isAttachmentDownloading = progress != 100
                    if (messageDetail is AmeHistoryMessageDetail) {
                        EventBus.getDefault().post(HistoryGroupAttachmentProgressEvent(content.url, GroupAttachmentProgressEvent.ACTION_ATTACHMENT_DOWNLOADING, progress.toFloat() / 100, total))
                    } else {
                        EventBus.getDefault().post(GroupAttachmentProgressEvent(messageDetail.gid, messageDetail.indexId, GroupAttachmentProgressEvent.ACTION_ATTACHMENT_DOWNLOADING, progress.toFloat() / 100, total))
                    }
                }

                override fun onError(call: Call?, e: Exception?, id: Long) {
                    ALog.e(TAG, "downloadAttachment error", e)
                    localResponse(false, null)
                    if (e is BaseHttp.HttpErrorException && (e.code == 403 || e.code == 404)) {
                        MessageDataManager.updateMessageSendStateByIndex(messageDetail.gid, messageDetail.indexId, GroupMessage.FILE_NOT_FOUND)
                    } else {
                        MessageDataManager.updateMessageSendStateByIndex(messageDetail.gid, messageDetail.indexId, GroupMessage.THUMB_DOWNLOAD_FAIL)
                    }
                }

                override fun onResponse(response: File?, id: Long) {
                    if (response == null) {
                        MessageDataManager.updateMessageSendStateByIndex(messageDetail.gid, messageDetail.indexId, GroupMessage.FILE_DOWNLOAD_FAIL)
                        return localResponse(false, null)
                    }
                    try {
                        val fileInfo = ChatFileEncryptDecryptUtil.decryptAndSaveFile(
                                BCMEncryptUtils.getMasterSecret(AppContextHolder.APP_CONTEXT),
                                response,
                                messageDetail,
                                ChatFileEncryptDecryptUtil.FileType.GROUP
                        )

                        // Notice: Remove check whether message is history message after GroupMessage entity updated.
                        // Author: Kin
                        if (messageDetail !is AmeHistoryMessageDetail) {
                            MessageDataManager.updateMessageAttachmentUri(messageDetail.gid, messageDetail.indexId, fileInfo)
                            localResponse(true, PartAuthority.getGroupAttachmentUri(messageDetail.gid, messageDetail.indexId))
                        } else {
                            localResponse(true, Uri.fromFile(fileInfo.file))
                        }
                        MessageDataManager.updateMessageSendStateByIndex(messageDetail.gid, messageDetail.indexId,
                                if (messageDetail.isSendByMe) GroupMessage.SEND_SUCCESS else GroupMessage.RECEIVE_SUCCESS)
                    } catch (ex: Exception) {
                        ALog.e(TAG, "downloadAttachment error", ex)
                        BcmFileUtils.delete(destPath)
                        localResponse(false, null)
                    }
                }

            })
        } catch (ex: Exception) {
            ALog.e(TAG, "downloadAttachment error", ex)
            localResponse(false, null)
        }
    }

    fun uploadVideo(masterSecret: MasterSecret, gid: Long, filePath: String, result: (urlResult: UploadResult?, thumbResult: UploadResult?) -> Unit) {
        val pair = EncryptMediaUtils.encryptVideo(masterSecret, gid, filePath)
        val videoResult = pair.first
        val thumbResult = pair.second

        if (videoResult != null && thumbResult != null && videoResult.isValid() && thumbResult.isValid()) {
            val filePaths: MutableList<StreamUploadData> = mutableListOf()
            filePaths.add(StreamUploadData(FileInputStream(videoResult.groupFileInfo!!.file), videoResult.groupFileInfo!!.name, videoResult.groupFileInfo!!.mimeType, videoResult.groupFileInfo!!.size))
            filePaths.add(StreamUploadData(FileInputStream(thumbResult.groupFileInfo!!.file), thumbResult.groupFileInfo!!.name, thumbResult.groupFileInfo!!.mimeType, thumbResult.groupFileInfo!!.size))

            val videoUploadResult = UploadResult("", videoResult.localFileInfo!!, videoResult.groupFileInfo!!.sign, videoResult.width, videoResult.height)
            val thumbUploadResult = UploadResult("", thumbResult.localFileInfo!!, thumbResult.groupFileInfo!!.sign, thumbResult.width, thumbResult.height)

            AmeFileUploader.uploadMultiStreamToAws(AmeFileUploader.AttachmentType.GROUP_MESSAGE, filePaths, object : AmeFileUploader.MultiStreamUploadCallback {
                override fun onFailed(resultMap: MutableMap<StreamUploadData, AmeFileUploader.FileUploadResult>?) {
                    videoResult.localFileInfo?.file?.delete()
                    thumbResult.localFileInfo?.file?.delete()

                    videoResult.groupFileInfo?.file?.delete()
                    thumbResult.groupFileInfo?.file?.delete()

                    result(null, null)
                }

                override fun onSuccess(resultMap: MutableMap<StreamUploadData, AmeFileUploader.FileUploadResult>?) {
                    videoResult.groupFileInfo?.file?.delete()
                    thumbResult.groupFileInfo?.file?.delete()

                    if (resultMap != null) {
                        videoUploadResult.url = resultMap[filePaths[0]]?.location ?: ""
                        thumbUploadResult.url = resultMap[filePaths[1]]?.location ?: ""
                    }

                    result(videoUploadResult, thumbUploadResult)
                }
            })
        } else {
            result(null, null)
        }
    }

    fun uploadImage(masterSecret: MasterSecret, groupId: Long, filePath: String, result: (urlResult: UploadResult?, thumbResult: UploadResult?) -> Unit) {
        val pair = EncryptMediaUtils.encryptImage(masterSecret, groupId, filePath) // Pair<ImageResult, ThumbnailResult>
        val imageResult = pair.first
        val thumbResult = pair.second

        if (imageResult?.isValid() == true && thumbResult?.isValid() == true) {
            try {
                val filePaths = mutableListOf<StreamUploadData>()

                val groupFileInfo = imageResult.groupFileInfo!!
                filePaths.add(StreamUploadData(FileInputStream(groupFileInfo.file), groupFileInfo.name, groupFileInfo.mimeType, groupFileInfo.size))

                val thumbGroupFileInfo = thumbResult.groupFileInfo!!
                filePaths.add(StreamUploadData(FileInputStream(thumbGroupFileInfo.file), thumbGroupFileInfo.name, thumbGroupFileInfo.mimeType, thumbGroupFileInfo.size))

                val imageUploadResult = UploadResult("", imageResult.localFileInfo!!, groupFileInfo.sign, imageResult.width, imageResult.height)
                val thumbUploadResult = UploadResult("", thumbResult.localFileInfo!!, thumbGroupFileInfo.sign, thumbResult.width, thumbResult.height)

                AmeFileUploader.uploadMultiStreamToAws(AmeFileUploader.AttachmentType.GROUP_MESSAGE, filePaths, object : AmeFileUploader.MultiStreamUploadCallback {
                    override fun onFailed(resultMap: MutableMap<StreamUploadData, AmeFileUploader.FileUploadResult>?) {
                        imageResult.localFileInfo?.file?.delete()
                        thumbResult.localFileInfo?.file?.delete()

                        imageResult.groupFileInfo?.file?.delete()
                        thumbResult.groupFileInfo?.file?.delete()

                        result(null, null)
                    }

                    override fun onSuccess(resultMap: MutableMap<StreamUploadData, AmeFileUploader.FileUploadResult>?) {
                        imageResult.groupFileInfo?.file?.delete()
                        thumbResult.groupFileInfo?.file?.delete()

                        if (resultMap != null) {
                            imageUploadResult.url = resultMap[filePaths[0]]?.location ?: ""
                            if (filePaths.size == 2) {
                                thumbUploadResult.url = resultMap[filePaths[1]]?.location ?: ""
                            }
                        }

                        result(imageUploadResult, thumbUploadResult)
                    }
                })
            } catch (tr: Throwable) {
                tr.printStackTrace()
            }
        } else {
            result(null, null)
        }
    }


    fun uploadFile(masterSecret: MasterSecret, groupId: Long, indexId: Long?, filePath: String, result: (urlResult: UploadResult?) -> Unit) {
        val fileResult = EncryptMediaUtils.encryptFile(masterSecret, groupId, filePath)

        if (fileResult != null && fileResult.isValid()) {
            val groupFileInfo = fileResult.groupFileInfo!!
            val fileUploadResult = UploadResult("", fileResult.localFileInfo!!, groupFileInfo.sign, 0, 0)
            val uploadData = StreamUploadData(FileInputStream(groupFileInfo.file), groupFileInfo.name, groupFileInfo.mimeType, groupFileInfo.size)

            AmeFileUploader.uploadStreamToAws(AmeFileUploader.AttachmentType.GROUP_MESSAGE, uploadData, object : AmeFileUploader.StreamUploadCallback() {
                override fun onUploadSuccess(url: String?, id: String?) {
                    fileResult.groupFileInfo?.file?.delete()

                    if (!url.isNullOrEmpty()) {
                        fileUploadResult.url = url
                    }

                    result(fileUploadResult)
                }

                override fun onUploadFailed(data: StreamUploadData?, msg: String?) {
                    fileResult.localFileInfo?.file?.delete()
                    fileResult.groupFileInfo?.file?.delete()

                    result(fileUploadResult)
                }

                override fun onProgressChange(currentProgress: Float) {
                    EventBus.getDefault().post(GroupAttachmentProgressEvent(groupId, indexId, GroupAttachmentProgressEvent.ACTION_ATTACHMENT_UPLOADING, currentProgress / 100, 0))
                }
            }, Callback.THREAD_CURRENT)
        }
    }
}