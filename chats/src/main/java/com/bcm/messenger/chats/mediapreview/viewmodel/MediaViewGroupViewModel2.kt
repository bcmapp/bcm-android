package com.bcm.messenger.chats.mediapreview.viewmodel

import com.bcm.messenger.chats.mediapreview.bean.*
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.utils.MediaUtil
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus

/**
 * Created by Kin on 2018/11/1
 */
class MediaViewGroupViewModel2(accountContext: AccountContext) : BaseMediaViewModel(accountContext) {
    override fun getCurrentData(threadId: Long, indexId: Long, result: (data: MediaViewData) -> Unit) {
        Observable.create<AmeGroupMessageDetail> {
            val message = MessageDataManager.fetchOneMessageByGidAndIndexId(accountContext, threadId, indexId)
            if (message != null) {
                it.onNext(message)
                it.onComplete()
            } else {
                it.onError(Exception("Message not found"))
            }
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    val data = generateGroupPreviewData(it)
                    result.invoke(data)
                }, {
                    it.printStackTrace()
                })
    }

    override fun getAllMediaData(threadId: Long, indexId: Long, reverse: Boolean, result: (dataList: List<MediaViewData>) -> Unit) {
        Observable.create<List<MediaViewData>> {
            val messages = MessageDataManager.fetchMediaMessages(accountContext, threadId)
            val mediaDataList = mutableListOf<MediaViewData>()
            if (reverse) {
                messages.forEach { msg ->
                    mediaDataList.add(generateGroupPreviewData(msg))
                }
            } else {
                for (i in messages.lastIndex downTo 0) {
                    mediaDataList.add(generateGroupPreviewData(messages[i]))
                }
            }
            it.onNext(mediaDataList)
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError { it.printStackTrace() }
                .subscribe {
                    result(it)
                }
    }

    override fun deleteData(data: MediaViewData?, result: ((success: Boolean) -> Unit)?) {
        if (data != null)
            Observable.create<Boolean> {
                try {
                    MessageDataManager.deleteOneMessageByIndexId(accountContext, (data.sourceMsg as AmeGroupMessageDetail).gid, data.indexId)
                    EventBus.getDefault().post(MediaDeleteEvent(0L, data.sourceMsg.gid, data.indexId))
                    it.onNext(true)
                } catch (ex: Exception) {
                    it.onNext(false)
                } finally {
                    it.onComplete()
                }
            }.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { success ->
                        result?.invoke(success)
                    }
    }

    override fun saveData(data: MediaViewData?, result: ((success: Boolean, path: String) -> Unit)?) {
        data?.saveAttachment(accountContext, masterSecret) { success, path ->
            result?.invoke(success, path)
        }
    }

    private fun generateGroupPreviewData(message: AmeGroupMessageDetail): MediaViewData {
        var mediaType: Int = MEDIA_TYPE_IMAGE
        val mediaUri = message.getFilePartUri(accountContext)
        var mimeType = ""
        when (message.message.type) {
             AmeGroupMessage.IMAGE -> {
                mediaType = MEDIA_TYPE_IMAGE
                mimeType = (message.message.content as AmeGroupMessage.ImageContent).mimeType
            }
            AmeGroupMessage.VIDEO -> {
                mediaType = MEDIA_TYPE_VIDEO
                mimeType = (message.message.content as AmeGroupMessage.VideoContent).mimeType
            }
            AmeGroupMessage.FILE -> {
                mimeType = (message.message.content as AmeGroupMessage.FileContent).mimeType
                if (MediaUtil.isImageType(mimeType)) {
                    mediaType = MEDIA_TYPE_IMAGE
                } else if (MediaUtil.isVideoType(mimeType)) {
                    mediaType = MEDIA_TYPE_VIDEO
                }
            }
        }
        return MediaViewData(message.indexId, mediaUri, mimeType, mediaType, message, MSG_TYPE_GROUP)
    }
}