package com.bcm.messenger.chats.mediabrowser

import android.annotation.SuppressLint
import android.net.Uri
import com.bcm.messenger.chats.clean.CleanConversationStorageLogic
import com.bcm.messenger.chats.group.logic.MessageFileHandler
import com.bcm.messenger.chats.mediapreview.bean.MediaDeleteEvent
import com.bcm.messenger.chats.util.AttachmentSaver
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.grouprepository.events.MessageEvent
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.grouprepository.modeltransform.GroupMessageTransform
import com.bcm.messenger.common.grouprepository.room.entity.GroupMessage
import com.bcm.messenger.common.utils.GroupUtil
import com.bcm.messenger.common.utils.MediaUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

/**
 * Created by Kin on 2018/10/15
 */
class GroupMediaBrowserViewModel(accountContext: AccountContext) : BaseMediaBrowserViewModel(accountContext) {
    private val TAG = "GroupMediaBrowserViewModel"

    private var gid = -1L // MUST set before invoking any functions.
    private val dateFormat = SimpleDateFormat(FORMAT_DATE_TITLE, Locale.getDefault())
    private val masterSecret = accountContext.masterSecret
    private var destroyed = false

    init {
        EventBus.getDefault().register(this)
    }

    override fun onCleared() {
        super.onCleared()
        EventBus.getDefault().unregister(this)
    }

    fun init(gid: Long) {
        this.gid = gid
    }

    @SuppressLint("CheckResult")
    override fun loadMedia(browseType: Int, callback: (result: Map<String, MutableList<MediaBrowseData>>) -> Unit) {
        if (gid == -1L) {
            ALog.e(TAG, "Gid has not set!")
            callback(emptyMap())
            return
        }
        Observable.create<Map<String, MutableList<MediaBrowseData>>> {
            // Fetch data from DB
            val browserDataList = when (browseType) {
                BaseMediaBrowserViewModel.TYPE_MEDIA -> transformToBrowserData(MessageDataManager.fetchMediaMessages(accountContext, gid))
                BaseMediaBrowserViewModel.TYPE_FILE -> transformToBrowserData(MessageDataManager.fetchFileMessages(accountContext, gid))
                BaseMediaBrowserViewModel.TYPE_LINK -> transformToBrowserData(MessageDataManager.fetchLinkMessages(accountContext, gid))
                else -> emptyList()
            }

            ALog.d(TAG, "Fetch ${browserDataList.size} data.")

            // Format to a map
            val messageMap = mutableMapOf<String, MutableList<MediaBrowseData>>()
            browserDataList.forEach { data ->
                val key = dateFormat.format(Date(data.time))
                val list = messageMap[key] ?: mutableListOf()
                list.add(data)
                messageMap[key] = list
                ALog.d(TAG, "key is $key")
            }
            it.onNext(messageMap)
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError {
                    it.printStackTrace()
                    callback(emptyMap())
                }
                .subscribe {
                    if (browseType == TYPE_MEDIA) {
                        mediaListLiveData.postValue(it)
                    } else {
                        callback(it)
                    }
                }
    }

    @SuppressLint("CheckResult")
    override fun download(mediaDataList: List<MediaBrowseData>, callback: (success: List<String>, fail: List<MediaBrowseData>) -> Unit) {
        if (masterSecret == null || gid == -1L) {
            callback(emptyList(), mediaDataList)
            ALog.e(TAG, "MasterSecret is null or gid has not set!")
            return
        }
        Observable.create<Pair<List<String>, List<MediaBrowseData>>> {
            val failList = mutableListOf<MediaBrowseData>()
            val successSet = mutableSetOf<String>()
            mediaDataList.forEach { data ->
                if (destroyed) {
                    it.onNext(Pair(emptyList(), failList))
                    it.onComplete()
                    return@forEach
                }
                val msg = data.msgSource as AmeGroupMessageDetail
                val url: String
                val mimeType: String
                var name: String? = null
                when (msg.message.type) {
                    AmeGroupMessage.IMAGE -> {
                        val content = msg.message.content as AmeGroupMessage.ImageContent
                        url = content.url
                        mimeType = content.mimeType
                        ALog.d(TAG, "file is image, url is $url, mimetype is $mimeType")
                    }
                    AmeGroupMessage.VIDEO -> {
                        val content = msg.message.content as AmeGroupMessage.VideoContent
                        url = content.url
                        mimeType = content.mimeType
                        ALog.d(TAG, "file is video, url is $url, mimetype is $mimeType")
                    }
                    AmeGroupMessage.FILE -> {
                        val content = msg.message.content as AmeGroupMessage.FileContent
                        url = content.url
                        mimeType = content.mimeType
                        name = content.fileName
                        ALog.d(TAG, "file is normal file, url is $url, mimetype is $mimeType")
                    }
                    else -> {
                        url = ""
                        mimeType = ""
                        ALog.d(TAG, "file is unknown, url is $url, mimetype is $mimeType")
                    }
                }
                if (msg.attachmentUri.isNullOrBlank()) {
                    MessageFileHandler.downloadAttachment(accountContext, msg, object : MessageFileHandler.MessageFileCallback {
                        override fun onResult(success: Boolean, uri: Uri?) {
                            if (success) {
                                ALog.d(TAG, "download file, uri is $uri")
                                val file = AttachmentSaver.saveAttachment(AppContextHolder.APP_CONTEXT, masterSecret, uri ?: return, mimeType, name)
                                if (file == null) {
                                    ALog.d(TAG, "download file failed, uri is $uri")
                                    failList.add(data)
                                } else {
                                    successSet.add(file.parent)
                                }
                            }
                        }
                    })

                } else {
                    ALog.d(TAG, "download file, uri is ${msg.attachmentUri}")
                    val file = AttachmentSaver.saveAttachment(AppContextHolder.APP_CONTEXT, masterSecret, msg.getFilePartUri(accountContext) ?: return@forEach, mimeType, name)
                    if (file == null) {
                        ALog.d(TAG, "download file failed, uri is ${msg.attachmentUri}")
                        failList.add(data)
                    } else {
                        successSet.add(file.parent)
                    }
                }
            }
            it.onNext(Pair(successSet.toList(), failList))
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback(it.first, it.second)
                }, {
                    it.printStackTrace()
                    callback(emptyList(), mediaDataList)
                })
    }

    @SuppressLint("CheckResult")
    override fun delete(mediaDataList: List<MediaBrowseData>, callback: (fail: List<MediaBrowseData>) -> Unit) {
        if (gid == -1L) {
            ALog.e(TAG, "Gid has not set!")
            return
        }
        Observable.create<Unit> {
            ALog.d(TAG, "delete ${mediaDataList.size} data")
            val groupAddress = GroupUtil.addressFromGid(accountContext, gid)

            val groupData = ArrayList<AmeGroupMessageDetail>()
            mediaDataList.forEach {
                groupData.add(it.msgSource as AmeGroupMessageDetail)
                CleanConversationStorageLogic.messageDeletedForConversation(groupAddress, it.getStorageType(), it.fileSize())
            }

            val entityList = mutableListOf<GroupMessage>()
            val indexList = mutableListOf<Long>()

            groupData.forEach { msg ->
                val groupMsg = GroupMessageTransform.transformToEntity(msg).apply {
                    // Function TransformToEntity has not set id
                    id = msg.indexId
                }
                indexList.add(msg.indexId)
                entityList.add(groupMsg)
            }
            MessageDataManager.deleteMessages(accountContext, gid, entityList)
            EventBus.getDefault().post(MessageEvent(accountContext, gid, indexList))
            it.onNext(Unit)
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError {
                    it.printStackTrace()
                    callback(mediaDataList)
                }
                .subscribe {
                    ALog.d(TAG, "delete success")
                    callback(emptyList())
                }
    }

    private fun transformToBrowserData(groupMessages: List<AmeGroupMessageDetail>): List<MediaBrowseData> {
        val browserDataList = mutableListOf<MediaBrowseData>()
        groupMessages.forEach {
            val data = getMediaBrowseData(it)
            if (data != null) {
                browserDataList.add(data)
            }
        }
        return browserDataList
    }

    private fun getRealMediaType(mimeType: String, messageDetail: AmeGroupMessageDetail, defaultType: String = "image/*"): String {
        val targetType = if (mimeType.isNullOrEmpty()) {
            val uri = messageDetail.toAttachmentUri()
            if (uri != null) {
                MediaUtil.getMimeType(AppContextHolder.APP_CONTEXT, uri)
            } else {
                ""
            }

        } else {
            mimeType
        }
        return if (targetType == null || targetType.isEmpty()) defaultType else targetType
    }

    private fun getMediaBrowseData(messageDetail: AmeGroupMessageDetail): MediaBrowseData? {

        return when (messageDetail.message.type) {
            AmeGroupMessage.IMAGE -> {
                val content = messageDetail.message.content as AmeGroupMessage.ImageContent
                MediaBrowseData("", getRealMediaType(content.mimeType, messageDetail, "image/jpeg"), messageDetail.sendTime, messageDetail, true)
            }
            AmeGroupMessage.VIDEO -> {
                val content = messageDetail.message.content as AmeGroupMessage.VideoContent
                MediaBrowseData("", getRealMediaType(content.mimeType, messageDetail, "video/*"), messageDetail.sendTime, messageDetail, true)
            }
            AmeGroupMessage.FILE -> {
                val content = messageDetail.message.content as AmeGroupMessage.FileContent
                MediaBrowseData(content.fileName
                        ?: "Unknown file", getRealMediaType(content.mimeType, messageDetail, "DAT/*"), messageDetail.sendTime, messageDetail, true)
            }
            AmeGroupMessage.LINK -> {
                val content = messageDetail.message.content as? AmeGroupMessage.LinkContent
                if (null != content) {
                    MediaBrowseData(content.url, "text/", messageDetail.sendTime, messageDetail, true)
                } else {
                    null
                }
            }
            else -> null
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: MediaDeleteEvent) {
        if (event.gid != gid) return
        val dataList = mediaListLiveData.value
        if (dataList != null) {
            for (subList in dataList.values) {
                for (data in subList) {
                    if ((data.msgSource as? AmeGroupMessageDetail)?.indexId == event.indexId) {
                        CleanConversationStorageLogic.messageDeletedForConversation(data.getUserAddress(accountContext), data.getStorageType(), data.fileSize())
                        subList.remove(data)
                        mediaListLiveData.postValue(dataList)
                        return
                    }
                }
            }
        }
    }
}