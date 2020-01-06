package com.bcm.messenger.chats.mediapreview.viewmodel

import android.annotation.SuppressLint
import android.net.Uri
import com.bcm.messenger.chats.mediapreview.MediaViewActivity
import com.bcm.messenger.chats.mediapreview.bean.*
import com.bcm.messenger.chats.privatechat.jobs.AttachmentDownloadJob
import com.bcm.messenger.chats.util.AttachmentSaver
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.attachments.AttachmentId
import com.bcm.messenger.common.attachments.DatabaseAttachment
import com.bcm.messenger.common.database.model.AttachmentDbModel
import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.mms.Slide
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.utils.MediaUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus

/**
 * Created by Kin on 2018/10/17
 */
class MediaViewPrivateViewModel(accountContext: AccountContext) : BaseMediaViewModel(accountContext) {
    companion object {
        const val TAG = "MediaViewPrivateViewModel"
    }

    private val chatRepo = Repository.getChatRepo(accountContext)

    override fun getCurrentData(threadId: Long, indexId: Long, result: (data: MediaViewData) -> Unit) {
        Observable.create(ObservableOnSubscribe<MessageRecord> {
            try {
                val record = chatRepo?.getMessage(indexId)
                if (record != null) {
                    it.onNext(record)
                }
            } catch (ex: Exception) {
                ALog.e(TAG, "delete error", ex)
            } finally {
                it.onComplete()
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    if (it.isMediaMessage()) {
                        val attachment = it.getMediaAttachment()
                        attachment?.let { a ->
                            val mediaType = if (MediaUtil.isVideo(a.contentType)) {
                                MEDIA_TYPE_VIDEO
                            } else {
                                MEDIA_TYPE_IMAGE
                            }

                            val data = MediaViewData(indexId, a.getPartUri(), a.contentType, mediaType, it, MSG_TYPE_PRIVATE)
                            result.invoke(data)
                        }

                    }
                }
    }

    override fun getAllMediaData(threadId: Long, indexId: Long, reverse: Boolean, result: (dataList: List<MediaViewData>) -> Unit) {
        Observable.create(ObservableOnSubscribe<List<MessageRecord>> {
            try {
                val list = chatRepo?.getMediaMessages(threadId)
                if (list != null) {
                    if (reverse) list.reverse()
                    it.onNext(list)
                }
            } catch (ex: Exception) {
                ALog.e(TAG, "get all media data error", ex)
            } finally {
                it.onComplete()
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    val list: ArrayList<MediaViewData> = ArrayList()
                    it.forEach { record ->
                        val id = record.id
                        record.getMediaAttachment()?.also { s ->
                            if (s.isVideo()) {
                                val data = MediaViewData(id, s.getPartUri(), s.contentType, MEDIA_TYPE_VIDEO, record, MSG_TYPE_PRIVATE)
                                list.add(data)
                            } else if (s.isImage()) {
                                val data = MediaViewData(id, s.getPartUri(), s.contentType, MEDIA_TYPE_IMAGE, record, MSG_TYPE_PRIVATE)
                                list.add(data)
                            }
                        }
                    }
                    result.invoke(list)
                }
    }

    override fun deleteData(data: MediaViewData?, result: ((success: Boolean) -> Unit)?) {
        if (data == null) {
            result?.invoke(false)
            return
        }
        Observable.create(ObservableOnSubscribe<Boolean> {
            try {
                chatRepo?.deleteMessage(data.indexId)
                EventBus.getDefault().post(MediaDeleteEvent((data.sourceMsg as MessageRecord).threadId, 0L, data.indexId))
                it.onNext(true)
            } catch (ex: Exception) {
                ALog.e(TAG, "delete error", ex)
                it.onNext(false)
            } finally {
                it.onComplete()
            }

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError { it.printStackTrace() }
                .subscribe {
                    result?.invoke(it)
                }
    }

    override fun saveData(data: MediaViewData?, result: ((success: Boolean) -> Unit)?) {
        if (data == null) {
            result?.invoke(false)
            return
        }

        if (data.sourceMsg is MessageRecord) {
            val messageRecord = data.sourceMsg
            if (data.mediaType == MEDIA_TYPE_IMAGE) {
                downloadImage(data.mediaUri, messageRecord, result)
            } else {
                downloadVideo(data.mediaUri, messageRecord, result)
            }
        } else {
            result?.invoke(false)
        }
    }

    private fun downloadImage(mediaUri: Uri?, messageRecord: MessageRecord, result: ((success: Boolean) -> Unit)?) {
        val slide = messageRecord.getImageAttachment()
        if (slide?.dataUri == null) {
            result?.invoke(false)
            return
        }

        if (mediaUri != null && masterSecret != null) {
            Observable.create(ObservableOnSubscribe<Boolean> {
                try {
                    AttachmentSaver.saveAttachment(AppContextHolder.APP_CONTEXT, masterSecret, mediaUri, slide.contentType, slide.fileName) ?: throw Exception("saveAttachment fail")
                    it.onNext(true)

                }
                catch (ex: Exception) {
                    it.onError(ex)
                }
                finally {
                    it.onComplete()
                }
            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        result?.invoke(it)
                    }, {
                        ALog.e(TAG, "downloadImage fail", it)
                        result?.invoke(false)
                    })
        } else {
            ALog.e(TAG, "mediaUri is null ,mediaUrl is null")
            result?.invoke(false)
        }
    }

    private fun downloadVideo(mediaUri: Uri?, messageRecord: MessageRecord, result: ((success: Boolean) -> Unit)?) {
        val slide = messageRecord.getVideoAttachment()
        if (slide?.dataUri == null) {
            result?.invoke(false)
            return
        }

        if (mediaUri != null && masterSecret != null) {
            Observable.create(ObservableOnSubscribe<Boolean> {
                try {
                    AttachmentSaver.saveAttachment(AppContextHolder.APP_CONTEXT, masterSecret, mediaUri, slide.contentType, slide.fileName) ?: throw Exception("saveAttachment fail")
                    it.onNext(true)
                }
                catch (ex: Exception) {
                    it.onError(ex)
                }
                finally {
                    it.onComplete()
                }
            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe( {
                        result?.invoke(it)
                    }, {
                        ALog.e(TAG, "downloadVideo fail", it)
                        result?.invoke(false)
                    })
            return
        }

        Observable.create(ObservableOnSubscribe<Boolean> {
            try {
                Repository.getAttachmentRepo(accountContext)?.setTransferState(slide, AttachmentDbModel.TransferState.STARTED)

                AmeModuleCenter.accountJobMgr(accountContext)?.add(AttachmentDownloadJob(AppContextHolder.APP_CONTEXT, accountContext,
                        messageRecord.id, slide.id, slide.uniqueId, true))

                it.onNext(true)

            } catch (ex: Exception) {
                ALog.e(TAG, "downloadVideo error", ex)
                it.onNext(false)
            } finally {
                it.onComplete()
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    result?.invoke(it)
                }
    }

    @SuppressLint("DefaultLocale")
    private fun getMessageMediaAttachmentUri(slide: Slide): Uri? {
        var uri: Uri? = null
        if (MediaViewActivity.isContentTypeSupported(slide.contentType)) {

            if (slide.asAttachment() is DatabaseAttachment) {
                val id = (slide.asAttachment() as DatabaseAttachment).attachmentId
                uri = AttachmentId.toUri(id)
            }

        }
        return uri
    }
}