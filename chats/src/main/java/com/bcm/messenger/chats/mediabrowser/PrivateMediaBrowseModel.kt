package com.bcm.messenger.chats.mediabrowser

import android.annotation.SuppressLint
import android.content.Context
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.clean.CleanConversationStorageLogic
import com.bcm.messenger.chats.mediapreview.bean.MediaDeleteEvent
import com.bcm.messenger.chats.privatechat.jobs.AttachmentDownloadJob
import com.bcm.messenger.chats.util.AttachmentSaver
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.database.model.AttachmentDbModel
import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.event.PartProgressEvent
import com.bcm.messenger.common.utils.MediaUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.text.SimpleDateFormat
import java.util.*


/**
 *
 * Created by wjh on 2018/10/15
 */
class PrivateMediaBrowseModel(accountContext: AccountContext) : BaseMediaBrowserViewModel(accountContext) {

    private val TAG = PrivateMediaBrowseModel::class.java.simpleName

    private val dateFormat = SimpleDateFormat(FORMAT_DATE_TITLE, Locale.getDefault())

    private var mThreadId: Long = -1
    private lateinit var mMasterSecret: MasterSecret

    private var mCurrentLoaded = PrivateMediaLoaded()

    private var mStop = false

    override fun onCleared() {
        super.onCleared()
        ALog.i(TAG, "onCleared")
        mStop = true
        EventBus.getDefault().unregister(this)
    }

    fun init(threadId: Long, masterSecret: MasterSecret) {
        mThreadId = threadId
        mMasterSecret = masterSecret

        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }

    @SuppressLint("CheckResult")
    override fun loadMedia(browseType: Int, callback: (result: Map<String, MutableList<MediaBrowseData>>) -> Unit) {
        checkValid()

        fun createMediaBrowseData(result: MutableMap<String, MutableList<MediaBrowseData>>, record: MessageRecord) {
            val data = getBrowseData(AppContextHolder.APP_CONTEXT, browseType, record)
                    ?: return
            val key = formatMapKey(data)
            val list = result[key] ?: mutableListOf()
            list.add(data)
            result[key] = list
        }

        Observable.create(ObservableOnSubscribe<Map<String, MutableList<MediaBrowseData>>> {
            ALog.d(TAG, "loadMedia $browseType")
            var result: MutableMap<String, MutableList<MediaBrowseData>>? = null
            val records: List<MessageRecord>
            try {
                val chatRepo = Repository.getChatRepo(accountContext)
                if (chatRepo == null) {
                    it.onNext(mapOf())
                } else {
                    records = when (browseType) {
                        TYPE_MEDIA -> {
                            mCurrentLoaded.mediaMap.clear()
                            result = mCurrentLoaded.mediaMap
                            chatRepo.getMediaMessages(mThreadId)
                        }
                        TYPE_FILE -> {
                            mCurrentLoaded.fileMap.clear()
                            result = mCurrentLoaded.fileMap
                            chatRepo.getFileMessages(mThreadId)
                        }
                        TYPE_LINK -> {
                            result = mutableMapOf()
                            chatRepo.getLinkMessages(mThreadId)
                        }
                        else -> {
                            result = mutableMapOf()
                            emptyList()
                        }
                    }
                    if (records.isNotEmpty()) {
                        records.forEach { record ->
                            createMediaBrowseData(result, record)
                        }
                    }
                }
            } catch (ex: Exception) {
                ALog.e(TAG, "loadMedia error", ex)
            } finally {
                it.onNext(result ?: mutableMapOf())
                it.onComplete()
            }

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { result ->
                    callback.invoke(result)
                }
    }

    @SuppressLint("CheckResult")
    override fun download(mediaDataList: List<MediaBrowseData>, callback: (fail: List<MediaBrowseData>) -> Unit) {
        checkValid()
        Observable.create(ObservableOnSubscribe<List<MediaBrowseData>> {

            val fail = mutableListOf<MediaBrowseData>()
            fail.addAll(mediaDataList)
            try {

                for (data in mediaDataList) {
                    if (mStop) {
                        break
                    }
                    val record = data.msgSource as? MessageRecord ?: continue
                    val attachmentSlide = record.getImageAttachment()
                            ?: record.getDocumentAttachment()
                    if (attachmentSlide != null) {
                        try {
                            if (attachmentSlide.transferState != AttachmentDbModel.TransferState.STARTED.state) {
                                AttachmentDownloadJob(AppContextHolder.APP_CONTEXT, accountContext, record.id,
                                        attachmentSlide.id, attachmentSlide.uniqueId, true).onRun(mMasterSecret)
                            }
                            if (AttachmentSaver.saveAttachment(AppContextHolder.APP_CONTEXT, mMasterSecret, attachmentSlide.dataUri
                                            ?: continue, attachmentSlide.contentType, attachmentSlide.fileName) != null) {
                                fail.remove(data)
                            }
                        } catch (ex: Exception) {
                            ALog.e(TAG, "record ${data.name} download attachment error")
                        }
                    }
                }
            } catch (ex: Exception) {
                ALog.e(TAG, "download error", ex)
            } finally {
                it.onNext(fail)
                it.onComplete()
            }

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { fail ->
                    callback.invoke(fail)
                }
    }

    @SuppressLint("CheckResult")
    override fun delete(mediaDataList: List<MediaBrowseData>, callback: (fail: List<MediaBrowseData>) -> Unit) {
        checkValid()
        Observable.create(ObservableOnSubscribe<List<MediaBrowseData>> {

            val fail = mutableListOf<MediaBrowseData>()
            fail.addAll(mediaDataList)
            try {
                val chatRepo = Repository.getChatRepo(accountContext)
                for (data in mediaDataList) {
                    if (mStop) {
                        break
                    }
                    val record = data.msgSource as? MessageRecord ?: continue
                    try {
                        if (MediaUtil.isTextType(data.mediaType)) {
                            chatRepo?.deleteMessage(record.id)
                        } else {
                            chatRepo?.deleteMessage(record.id)
                            CleanConversationStorageLogic.messageDeletedForConversation(data.getUserAddress(), data.getStorageType(), data.fileSize())
                        }
                        fail.remove(data)
                        val key = formatMapKey(data)
                        if (MediaUtil.isImageType(data.mediaType) || MediaUtil.isVideoType(data.mediaType)) {
                            mCurrentLoaded.mediaMap[key]?.remove(data)
                        } else if (!MediaUtil.isTextType(data.mediaType)) {
                            mCurrentLoaded.fileMap[key]?.remove(data)
                        }

                    } catch (ex: Exception) {
                        ALog.e(TAG, "record ${data.name} download attachment error")
                    }

                }
            } catch (ex: Exception) {
                ALog.e(TAG, "download error", ex)
            } finally {
                it.onNext(fail)
                it.onComplete()
            }

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { fail ->
                    callback.invoke(fail)
                }
    }


    @Throws(Exception::class)
    private fun checkValid() {
        if (!::mMasterSecret.isInitialized) {
            ALog.w(TAG, "not init first")
            throw IllegalStateException("master is not initialized")
        }
    }


    private fun getBrowseData(context: Context, browseType: Int, record: MessageRecord): MediaBrowseData? {
        if (record.isMediaMessage()) {
            val slide = (if (browseType == TYPE_MEDIA) record.getImageAttachment()
                    ?: record.getVideoAttachment() else record.getDocumentAttachment())
                    ?: return null
            val name = slide.fileName ?: context.getString(R.string.chats_unknown_file_name)
            return MediaBrowseData(name, slide.contentType, record.dateReceive, record, false)
        } else {
            return MediaBrowseData(record.body, "text/*", record.dateReceive, record, false)
        }
    }

    private fun formatMapKey(browseData: MediaBrowseData): String {
        return dateFormat.format(Date(browseData.time))
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onEventThumbnailDownload(event: PartProgressEvent) {
        if (event.progress < event.total) {
            return
        }
        ALog.i(TAG, "onEventThumbnailDownload currentLoaded: ${mCurrentLoaded.mediaMap.count()}, ${mCurrentLoaded.fileMap.count()}")
        for ((key, dataList) in mCurrentLoaded.mediaMap) {
            for (data in dataList) {
                val record = data.msgSource as? MessageRecord ?: continue
                val attachment = record.getImageAttachment() ?: continue
                if (event.attachment == attachment) {
                    ALog.i(TAG, "onEventThumbnailDownload setHasData")
//                    attachment.setHasData(true)
//                    if (MediaUtil.isVideoType(event.attachment.contentType)) {
//                        attachment.setHasThumbnail(true)
//                    }
                    AmeDispatcher.mainThread.dispatch({
                        data.notifyChanged()
                    }, 300)
                    return
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onEventDurationUpdate(event: MediaVideoDurationEvent) {
        if (event.duration <= 0) {
            return
        }
        ALog.i(TAG, "onEventDurationUpdate duration: ${event.duration}")
        for ((key, dataList) in mCurrentLoaded.mediaMap) {
            for (data in dataList) {
                val record = data.msgSource as? MessageRecord ?: continue
                val attachment = record.getImageAttachment() ?: continue
                if (event.attachmentId.rowId == attachment.id) {
                    ALog.i(TAG, "onEventDurationUpdate setDuration: ${event.duration}")
                    attachment.duration = event.duration
                    AmeDispatcher.mainThread.dispatch {
                        data.notifyChanged()
                    }
                    return
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: MediaDeleteEvent) {
        if (event.threadId != mThreadId) return
        for (subList in mCurrentLoaded.mediaMap.values) {
            for (data in subList) {
                if ((data.msgSource as? MessageRecord)?.id == event.indexId) {
                    subList.remove(data)
                    CleanConversationStorageLogic.messageDeletedForConversation(data.getUserAddress(), data.getStorageType(), data.fileSize())
                    mediaListLiveData.postValue(mCurrentLoaded.mediaMap)
                    return
                }
            }
        }
    }


    inner class PrivateMediaLoaded {
        val mediaMap = mutableMapOf<String, MutableList<MediaBrowseData>>()
        val fileMap = mutableMapOf<String, MutableList<MediaBrowseData>>()
    }
}