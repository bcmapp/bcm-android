package com.bcm.messenger.chats.forward.viewmodel

import android.annotation.SuppressLint
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.bcm.messenger.chats.forward.ForwardController
import com.bcm.messenger.chats.forward.ForwardMessageEncapsulator
import com.bcm.messenger.chats.forward.ForwardOnceMessage
import com.bcm.messenger.chats.group.logic.GroupMessageLogic
import com.bcm.messenger.chats.group.logic.MessageFileHandler
import com.bcm.messenger.chats.privatechat.logic.MessageSender
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.corebean.HistoryMessageDetail
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.sms.OutgoingLocationMessage
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.common.utils.ConversationUtils
import com.bcm.messenger.common.utils.GroupUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 * Created by Kin on 2018/8/22
 */
class ForwardViewModel : ViewModel() {
    private val TAG = "ForwardViewModel"

    val PRIVATE_MESSAGE = 0
    val GROUP_MESSAGE = 1

    val privateMessageList = mutableListOf<MessageRecord>()
    val groupMessageList = mutableListOf<AmeGroupMessageDetail>()
    val selectRecipients = mutableListOf<Recipient>()
    var messageType = PRIVATE_MESSAGE
    var isContactMessage = false

    var fetchSuccess = MutableLiveData<Boolean>()

    fun getPrivateMessage(): MessageRecord? {
        return if (privateMessageList.isNotEmpty()) privateMessageList[0] else null
    }

    fun getGroupMessage(): AmeGroupMessageDetail? {
        return if (groupMessageList.isNotEmpty()) groupMessageList[0] else null
    }

    fun forwardPrivateMessage(masterSecret: MasterSecret, commentText: String): Boolean {
        if (privateMessageList.isEmpty()) {
            ALog.e(TAG, "Private message has not initialized.")
            return false
        }
        val recipient = selectRecipients[0]
        val message = privateMessageList[0]

        if (!recipient.isGroupRecipient && recipient.isBlocked) {
            return false
        }

        ForwardController.addOnceMessage(ForwardOnceMessage(message, null, recipient, masterSecret, commentText))
        return true
    }

    fun forwardGroupMessage(masterSecret: MasterSecret, commentText: String): Boolean {
        if (groupMessageList.isEmpty()) {
            ALog.e(TAG, "Group message has not initialized.")
            return false
        }

        val recipient = selectRecipients[0]
        val message = groupMessageList[0]

        if (!recipient.isGroupRecipient && recipient.isBlocked) {
            return false
        }

        ForwardController.addOnceMessage(ForwardOnceMessage(null, message, recipient, masterSecret, commentText))
        return true
    }


    fun getPrivateMessage(masterSecret: MasterSecret, indexId: Long) {
        ALog.d(TAG, "Get private message for forward")
        Observable.create<MessageRecord> {
            val message = Repository.getChatRepo().getMessage(indexId)
            if (message != null) {
                ALog.d(TAG, "Get private message success")
                it.onNext(message)
            } else {
                ALog.d(TAG, "Get private message failed")
            }
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    privateMessageList.add(it)
                    messageType = PRIVATE_MESSAGE
                    fetchSuccess.postValue(true)
                }
    }


    @SuppressLint("CheckResult")
    fun getGroupMessage(indexId: Long, gid: Long) {
        ALog.d(TAG, "Get group message for forward")
        Observable.create<AmeGroupMessageDetail> {
            val message = MessageDataManager.fetchOneMessageByGidAndIndexId(gid, indexId)
            if (message != null) {
                it.onNext(message)
            }
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete {}
                .doOnError {
                    it.printStackTrace()
                }
                .subscribe {
                    groupMessageList.add(it)
                }
        messageType = GROUP_MESSAGE
        fetchSuccess.postValue(true)
    }

    @SuppressLint("CheckResult")
    fun getMultiplePrivateMessages(masterSecret: MasterSecret, indexIdList: LongArray) {
        ALog.d(TAG, "Get multiple private messages for forward")
        Observable.create<List<MessageRecord>> {
            val messages = Repository.getChatRepo().getMessages(indexIdList.toList())
            it.onNext(messages)
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError {
                    it.printStackTrace()
                    fetchSuccess.postValue(false)
                }
                .subscribe {
                    privateMessageList.addAll(it)
                    messageType = PRIVATE_MESSAGE
                    fetchSuccess.postValue(true)
                }
    }

    @SuppressLint("CheckResult")
    fun getMultipleGroupMessages(gid: Long, indexIdList: LongArray) {
        ALog.d(TAG, "Get multiple group messages for forward")
        Observable.create<List<AmeGroupMessageDetail>> {
            val list = mutableListOf<AmeGroupMessageDetail>()
            indexIdList.forEach { indexId ->
                val message = MessageDataManager.fetchOneMessageByGidAndIndexId(gid, indexId)
                if (message != null) {
                    list.add(message)
                } else {
                    it.onError(RuntimeException("Group message is null!"))
                    return@create
                }
            }
            it.onNext(list)
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError {
                    it.printStackTrace()
                    fetchSuccess.postValue(false)
                }
                .subscribe {
                    groupMessageList.addAll(it)
                    messageType = GROUP_MESSAGE
                    fetchSuccess.postValue(true)
                }
    }

    @SuppressLint("CheckResult")
    fun forwardMultipleMessages(gid: Long, masterSecret: MasterSecret, enableHistory: Boolean, commentText: String, result: (succeed: Boolean) -> Unit) {

        if (enableHistory) {
            AmeAppLifecycle.showLoading()
            Observable.create<List<HistoryMessageDetail>> {
                if (messageType == GROUP_MESSAGE) {
                    ForwardMessageEncapsulator.uploadMessageFilesIfNeed(masterSecret, groupMessageList) { success ->
                        if (success) {
                            val messageList = ForwardMessageEncapsulator.encapsulateGroupHistoryMessages(gid, groupMessageList)
                            it.onNext(messageList)
                            it.onComplete()
                        } else {
                            it.onError(Exception("upload failed"))
                        }
                        it.onComplete()
                    }
                } else {
                    val emitter = it
                    ForwardMessageEncapsulator.encapsulatePrivateHistoryMessages(AppContextHolder.APP_CONTEXT, masterSecret, privateMessageList) {
                        succeed, messageList ->
                        if (succeed){
                            emitter.onNext(messageList)
                            emitter.onComplete()
                        } else {
                            it.onError(Exception("upload failed"))
                        }
                    }
                }
            }.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnError {
                        ALog.e(TAG, "forwardMultipleMessages error", it)
                        AmeAppLifecycle.hideLoading()
                        result(false)
                    }
                    .subscribe {
                        AmeAppLifecycle.hideLoading()
                        result(true)
                        if (selectRecipients.isNotEmpty()) {
                            val recipient = selectRecipients[0]
                            val content = AmeGroupMessage.HistoryContent(it)
                            if (recipient.isGroupRecipient) {
                                val groupId = GroupUtil.gidFromAddress(selectRecipients[0].address)
                                GroupMessageLogic.messageSender.sendHistoryMessage(groupId, content, null)
                                ForwardController.sendGroupCommentMessage(commentText, groupId)
                            } else {
                                ConversationUtils.getThreadId(recipient) { threadId ->
                                    val contentStr = AmeGroupMessage(AmeGroupMessage.CHAT_HISTORY, content).toString()
                                    val locationMessage = OutgoingLocationMessage(recipient, contentStr,
                                            recipient.expireMessages * 1000L)
                                    Observable.create<Long> {

                                        val id = MessageSender.send(AppContextHolder.APP_CONTEXT,
                                                locationMessage, threadId, null)
                                        ForwardController.sendPrivateCommentMessage(commentText, recipient, masterSecret, threadId)

                                        it.onNext(id)
                                        it.onComplete()

                                    }.subscribeOn(Schedulers.io())
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribe({

                                            }, {
                                                ALog.e(TAG, "forwardMultipleMessages error", it)
                                            })
                                }
                            }
                        }
                    }
        } else {
            privateMessageList.forEachIndexed { index, messageRecord ->
                if (index == privateMessageList.size - 1) {
                    ForwardController.addOnceMessage(ForwardOnceMessage(messageRecord, null, selectRecipients[0], masterSecret, commentText))
                } else {
                    ForwardController.addOnceMessage(ForwardOnceMessage(messageRecord, null, selectRecipients[0], masterSecret))
                }
            }
            groupMessageList.forEachIndexed { index, ameGroupMessageDetail ->
                if (index == groupMessageList.size - 1) {
                    ForwardController.addOnceMessage(ForwardOnceMessage(null, ameGroupMessageDetail, selectRecipients[0], masterSecret, commentText))
                } else {
                    ForwardController.addOnceMessage(ForwardOnceMessage(null, ameGroupMessageDetail, selectRecipients[0], masterSecret))
                }
            }
            result(true)
        }
    }

    @SuppressLint("CheckResult")
    fun downloadAndDecryptThumbnail(callback: (uri: Uri) -> Unit) {
        if (groupMessageList.isEmpty()) {
            callback(Uri.EMPTY)
            return
        }

        val groupMessage = groupMessageList[0]

        MessageFileHandler.downloadThumbnail(groupMessage, object : MessageFileHandler.MessageFileCallback {

            override fun onResult(success: Boolean, uri: Uri?) {
                if(success) {
                    callback(uri ?: Uri.EMPTY)
                }else {
                    callback(Uri.EMPTY)
                }
            }

        })

    }
}