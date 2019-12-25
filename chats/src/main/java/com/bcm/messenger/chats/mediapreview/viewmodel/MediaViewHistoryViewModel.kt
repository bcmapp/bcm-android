package com.bcm.messenger.chats.mediapreview.viewmodel

import com.bcm.messenger.chats.mediapreview.BaseMediaViewModel
import com.bcm.messenger.chats.mediapreview.bean.MEDIA_TYPE_IMAGE
import com.bcm.messenger.chats.mediapreview.bean.MEDIA_TYPE_VIDEO
import com.bcm.messenger.chats.mediapreview.bean.MSG_TYPE_HISTORY
import com.bcm.messenger.chats.mediapreview.bean.MediaViewData
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.corebean.HistoryMessageDetail
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.grouprepository.model.AmeHistoryMessageDetail
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.utils.MediaUtil
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 * Created by Kin on 2018/10/25
 */
class MediaViewHistoryViewModel : BaseMediaViewModel() {
    private val TAG = "MediaViewHistoryViewModel"

    override fun getCurrentData(threadId: Long, indexId: Long, result: (data: MediaViewData) -> Unit) {}

    override fun getAllMediaData(threadId: Long, indexId: Long, reverse: Boolean, result: (dataList: List<MediaViewData>) -> Unit) {
        Observable.create<List<MediaViewData>> {
            if (threadId == ARouterConstants.PRIVATE_TEXT_CHAT) {
                val message = Repository.getChatRepo().getMessage(indexId)
                if (message != null) {
                    val groupMessage = AmeGroupMessage.messageFromJson(message.body)
                    it.onNext(generateGroupPreviewList(indexId, AmeGroupMessageDetail().apply { this.message = groupMessage }))
                }
            } else {
                val message = MessageDataManager.fetchOneMessageByGidAndIndexId(threadId, indexId)
                if (message != null) {
                    it.onNext(generateGroupPreviewList(indexId, message))
                }
            }
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    result(it)
                }, {
                    it.printStackTrace()
                    result(emptyList())
                })
    }

    override fun deleteData(data: MediaViewData?, result: ((success: Boolean) -> Unit)?) {}

    override fun saveData(data: MediaViewData?, result: ((success: Boolean) -> Unit)?) {
        data?.saveAttachment(null) { success, _ ->
            result?.invoke(success)
        }
    }

    private fun generateGroupPreviewList(indexId: Long, message: AmeGroupMessageDetail): List<MediaViewData> {
        if (message.message.type != AmeGroupMessage.CHAT_HISTORY) {
            return emptyList()
        }
        val msgList = mutableListOf<MediaViewData>()
        val historyContent = message.message.content as AmeGroupMessage.HistoryContent

        historyContent.messageList.forEach {
            val data = historyMessage2GroupMessage(it, indexId)
            if (data != null) {
                msgList.add(data)
            }
        }
        return msgList
    }

    private fun historyMessage2GroupMessage(it:HistoryMessageDetail, indexId:Long): MediaViewData? {
        var isMediaType = false
        val history = AmeHistoryMessageDetail().apply {
            senderId = it.sender
            sendTime = it.sendTime
            isSendByMe = it.sender == AMELogin.uid
            sendState = AmeGroupMessageDetail.SendState.SEND_SUCCESS
            this.message = AmeGroupMessage.messageFromJson(it.messagePayload ?: "")
            thumbPsw = it.thumbPsw
            attachmentPsw = it.attachmentPsw
        }
        val mediaType: Int
        val mimeType: String
        when (history.message.type) {
             AmeGroupMessage.IMAGE -> {
                mediaType = MEDIA_TYPE_IMAGE
                mimeType = (history.message.content as AmeGroupMessage.ImageContent).mimeType
                isMediaType = true
            }
            AmeGroupMessage.VIDEO -> {
                mediaType = MEDIA_TYPE_VIDEO
                mimeType = (history.message.content as AmeGroupMessage.VideoContent).mimeType
                isMediaType = true
            }
            AmeGroupMessage.FILE -> {
                mimeType = (history.message.content as AmeGroupMessage.FileContent).mimeType
                mediaType = when {
                    MediaUtil.isVideoType(mimeType) -> {
                        isMediaType = true
                        MEDIA_TYPE_VIDEO
                    }
                    MediaUtil.isImageType(mimeType) -> {
                        isMediaType = true
                        MEDIA_TYPE_IMAGE
                    }
                    else -> -1
                }
            }
            else -> {
                mediaType = 0
                mimeType = ""
            }
        }
        if (isMediaType) {
            return MediaViewData(indexId, history.filePartUri, mimeType, mediaType, history, MSG_TYPE_HISTORY)
        }
        return null
    }

}