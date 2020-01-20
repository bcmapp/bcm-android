package com.bcm.messenger.chats.mediapreview.bean

import android.net.Uri
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.components.ZoomingImageView
import com.bcm.messenger.chats.group.logic.MessageFileHandler
import com.bcm.messenger.chats.privatechat.jobs.AttachmentDownloadJob
import com.bcm.messenger.chats.util.AttachmentSaver
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.attachments.AttachmentId
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.database.model.AttachmentDbModel
import com.bcm.messenger.common.database.records.AttachmentRecord
import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.grouprepository.model.AmeHistoryMessageDetail
import com.bcm.messenger.common.mms.DecryptableStreamUriLoader
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.video.VideoPlayer
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.lang.ref.WeakReference

/**
 * Created by Kin on 2018/10/31
 */
const val MEDIA_TYPE_IMAGE = 1
const val MEDIA_TYPE_VIDEO = 2

const val MSG_TYPE_PRIVATE = 1
const val MSG_TYPE_GROUP = 2
const val MSG_TYPE_HISTORY = 3
const val MSG_TYPE_SINGLE = 4

data class MediaViewData(val indexId: Long,
                         var mediaUri: Uri?,
                         val mimeType: String,
                         val mediaType: Int,
                         val sourceMsg: Any,
                         val msgType: Int) {

    private var imageViewRef: WeakReference<ZoomingImageView>? = null
    private var videoViewRef: WeakReference<VideoPlayer>? = null

    private val TAG = "MediaViewData"

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is MediaViewData) return false
        return other.indexId == indexId &&
                other.mediaType == mediaType &&
                other.sourceMsg.javaClass == sourceMsg.javaClass &&
                other.msgType == msgType
    }

    override fun hashCode(): Int {
        var result = 17
        result = result * 31 + indexId.hashCode()
        result = result * 31 + (mediaUri?.hashCode() ?: 0)
        result = result * 31 + mimeType.hashCode()
        result = result * 31 + mediaType.hashCode()
        result = result * 31 + sourceMsg.hashCode()
        result = result * 31 + msgType.hashCode()

        return result
    }

    fun isDataNotFound(): Boolean {
        if (msgType == MSG_TYPE_PRIVATE) {
            return (sourceMsg as MessageRecord).isMediaDeleted()
        } else if (msgType == MSG_TYPE_GROUP || msgType == MSG_TYPE_HISTORY) {
            return (sourceMsg as AmeGroupMessageDetail).isFileDeleted
        }
        return false
    }

    fun refreshSlide(attachment: AttachmentRecord) {
        if (sourceMsg is MessageRecord) {
            val videoAttachment = sourceMsg.getVideoAttachment()
            if (videoAttachment != null) {
                videoAttachment.dataUri = attachment.dataUri
                videoAttachment.thumbnailUri = attachment.thumbnailUri
            }
        }
    }

    fun setImage(imageView: ZoomingImageView,
                 glide: GlideRequests,
                 masterSecret: MasterSecret) {
        imageViewRef = WeakReference(imageView)
        if (mediaUri != null) {
            imageView.setImageUri(masterSecret, glide, mediaUri, mimeType)
        } else if (msgType == MSG_TYPE_HISTORY || msgType == MSG_TYPE_GROUP) {
            val messageDetail = sourceMsg as AmeGroupMessageDetail
            if (messageDetail.getThumbnailPartUri(masterSecret.accountContext) != null) {
                imageView.setImageUri(masterSecret, glide, messageDetail.getThumbnailPartUri(masterSecret.accountContext), mimeType)
                downloadGroupAttachment(imageView, glide, messageDetail, masterSecret)
            } else {
                downloadGroupThumbnail(imageView, glide, messageDetail, masterSecret)
            }
        }
    }

    fun setVideo(videoPlayer: VideoPlayer, masterSecret: MasterSecret) {
        videoViewRef = WeakReference(videoPlayer)
        when (msgType) {
            MSG_TYPE_PRIVATE -> {
                if (mediaUri != null) {
                    val messageRecord = sourceMsg as MessageRecord
                    val attachment = messageRecord.getVideoAttachment()
                    attachment?.let {
                        videoPlayer.hideVideoThumbnail()
                        videoPlayer.stopVideo()
                        videoPlayer.setVideoSource(masterSecret, mediaUri!!, false)
                    }
                }
            }
            MSG_TYPE_GROUP, MSG_TYPE_HISTORY -> {
                videoPlayer.hideVideoThumbnail()
                videoPlayer.setVideoSource(masterSecret, mediaUri!!, false)
            }
        }
    }

    fun setVideoThumbnail(accountContext: AccountContext, videoPlayer: VideoPlayer, glide: GlideRequests, masterSecret: MasterSecret) {
        videoViewRef = WeakReference(videoPlayer)
        when (msgType) {
            MSG_TYPE_PRIVATE -> {
                try {
                    videoPlayer.setVideoThumbnail(Uri.parse("android.resource://${AppContextHolder.APP_CONTEXT.packageName}/${R.drawable.common_video_place_img}"), glide)
                } catch (ex: Exception) {
                }
            }
            MSG_TYPE_GROUP, MSG_TYPE_HISTORY -> {
                val messageRecord = sourceMsg as AmeGroupMessageDetail
                if (messageRecord.getThumbnailPartUri(accountContext) != null) {
                    videoPlayer.setVideoThumbnail(DecryptableStreamUriLoader.DecryptableUri(masterSecret, messageRecord.getThumbnailPartUri(accountContext)!!), glide)
                } else {
                    MessageFileHandler.downloadThumbnail(accountContext, messageRecord, object : MessageFileHandler.MessageFileCallback {
                        override fun onResult(success: Boolean, uri: Uri?) {
                            val view = videoViewRef?.get()
                            if (view == videoPlayer) {
                                if (success) {
                                    view.setVideoThumbnail(uri, glide)
                                } else {
                                    try {
                                        view.setVideoThumbnail(Uri.parse("android.resource://${AppContextHolder.APP_CONTEXT.packageName}/${R.drawable.common_video_place_img}"), glide)
                                    } catch (ex: Exception) {
                                    }
                                }
                            }
                        }
                    })
                }
            }
        }
    }

    fun downloadVideo(videoPlayer: VideoPlayer, masterSecret: MasterSecret, callback: () -> Unit) {
        when (msgType) {
            MSG_TYPE_PRIVATE -> downloadPrivateVideo(masterSecret.accountContext, callback)
            MSG_TYPE_GROUP -> downloadGroupVideo(videoPlayer, sourceMsg as AmeGroupMessageDetail, masterSecret, callback)
            MSG_TYPE_HISTORY -> downloadGroupVideo(videoPlayer, sourceMsg as AmeHistoryMessageDetail, masterSecret, callback)
        }
    }

    private fun downloadPrivateVideo(accountContext: AccountContext, callback: () -> Unit) {
        val messageRecord = sourceMsg as MessageRecord
        val attachment = messageRecord.getVideoAttachment() ?: return

        Observable.create(ObservableOnSubscribe<Boolean> {
            try {
                Repository.getAttachmentRepo(accountContext)?.setTransferState(attachment, AttachmentDbModel.TransferState.STARTED)

                AmeModuleCenter.accountJobMgr(accountContext)?.add(AttachmentDownloadJob(AppContextHolder.APP_CONTEXT, accountContext,
                        messageRecord.id, attachment.id, attachment.uniqueId, true))

                it.onNext(true)

            } catch (ex: Exception) {
                it.onError(ex)
            } finally {
                it.onComplete()
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback()
                }, {
                    ALog.e(TAG, "downloadVideo error", it)
                })

    }


    private fun downloadGroupVideo(videoPlayer: VideoPlayer, message: AmeGroupMessageDetail, masterSecret: MasterSecret, callback: () -> Unit) {

        MessageFileHandler.downloadAttachment(masterSecret.accountContext, message, object : MessageFileHandler.MessageFileCallback {
            override fun onResult(success: Boolean, uri: Uri?) {
                if (success) {
                    if (videoPlayer == videoViewRef?.get()) {
                        mediaUri = uri
                        videoPlayer.hideVideoThumbnail()
                        videoPlayer.setVideoSource(masterSecret, mediaUri!!, false)
                    }
                }
                callback()
            }
        })
    }

    private fun downloadGroupThumbnail(imageView: ZoomingImageView, glide: GlideRequests, messageDetail: AmeGroupMessageDetail, masterSecret: MasterSecret) {
        if (messageDetail.isFileDeleted) {
            return
        }
        MessageFileHandler.downloadThumbnail(masterSecret.accountContext, messageDetail, object : MessageFileHandler.MessageFileCallback {
            override fun onResult(thumbSuccess: Boolean, thumbUri: Uri?) {
                val content = messageDetail.message.content as AmeGroupMessage.AttachmentContent

                val view = imageViewRef?.get()
                if (view == imageView) {
                    if (thumbSuccess) {
                        view.setImageUri(masterSecret, glide, thumbUri, content.mimeType)
                    } else {
                        try {
                            view.setImageUri(null, glide, Uri.parse("android.resource://${AppContextHolder.APP_CONTEXT.packageName}/${R.drawable.common_image_place_img}"), content.mimeType)
                        } catch (ex: Exception) {
                        }
                    }
                }

                downloadGroupAttachment(imageView, glide, messageDetail, masterSecret)
            }
        })
    }

    private fun downloadGroupAttachment(imageView: ZoomingImageView, glide: GlideRequests, messageDetail: AmeGroupMessageDetail, masterSecret: MasterSecret) {
        if (messageDetail.isFileDeleted) {
            return
        }
        MessageFileHandler.downloadAttachment(masterSecret.accountContext, messageDetail, object : MessageFileHandler.MessageFileCallback {
            override fun onResult(success: Boolean, uri: Uri?) {
                val content = messageDetail.message.content as AmeGroupMessage.AttachmentContent

                if (success) {
                    val view = imageViewRef?.get()
                    if (view == imageView) {
                        mediaUri = uri
                        view.setImageUri(masterSecret, glide, uri, content.mimeType)
                    }
                }
            }
        })
    }


    fun saveAttachment(accountContext: AccountContext, masterSecret: MasterSecret?, callback: (success: Boolean, uri: String) -> Unit) {
        if (sourceMsg is AmeGroupMessageDetail) {

            fun doSaveAction(finalUri: Uri) {
                val content = sourceMsg.message.content as AmeGroupMessage.AttachmentContent
                val name = if (content is AmeGroupMessage.FileContent) {
                    content.fileName
                } else {
                    null
                }
                Observable.create(ObservableOnSubscribe<File> {
                    val file = if (masterSecret != null) {
                        AttachmentSaver.saveAttachment(AppContextHolder.APP_CONTEXT, masterSecret, finalUri, content.mimeType, name)
                    } else {
                        AttachmentSaver.saveAttachment(accountContext, AppContextHolder.APP_CONTEXT, finalUri.toString(), content.mimeType, name)
                    }
                    if (file != null) {
                        it.onNext(file)
                    } else {
                        it.onError(Exception("saveAttachment error"))
                    }
                    it.onComplete()

                }).subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            callback.invoke(true, Uri.fromFile(it).toString())
                        }, {
                            callback.invoke(false, "")
                        })
            }

            if (mediaUri != null) {
                doSaveAction(mediaUri ?: return)
            } else {
                MessageFileHandler.downloadAttachment(accountContext, sourceMsg, object : MessageFileHandler.MessageFileCallback {
                    override fun onResult(success: Boolean, uri: Uri?) {
                        if (success) {
                            doSaveAction(uri ?: return)
                        } else {
                            callback.invoke(false, "")
                        }
                    }
                })
            }
        }
    }

    fun getPrivateAttachmentId(): AttachmentId? {
        if (sourceMsg is MessageRecord) {
            val attachment = sourceMsg.getMediaAttachment()
            if (attachment != null) {
                return AttachmentId(attachment.id, attachment.uniqueId)
            }
        }
        return null
    }

}