package com.bcm.messenger.chats.privatechat

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.privatechat.logic.MarkReadReceiver
import com.bcm.messenger.chats.privatechat.logic.MessageSender
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.database.identity.IdentityRecordList
import com.bcm.messenger.common.database.model.DecryptFailData
import com.bcm.messenger.common.database.records.IdentityRecord
import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.database.repositories.DraftRepo
import com.bcm.messenger.common.database.repositories.PrivateChatEvent
import com.bcm.messenger.common.database.repositories.PrivateChatRepo
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.event.MessageReceiveNotifyEvent
import com.bcm.messenger.common.event.TextSendEvent
import com.bcm.messenger.common.mms.OutgoingMediaMessage
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.sms.OutgoingLocationMessage
import com.bcm.messenger.common.sms.OutgoingTextMessage
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.centerpopup.AmeCenterPopup
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*

/**
 * private chat view model
 * Created by Kin on 2019/9/18
 */
class AmeConversationViewModel(
        private val mAccountContext: AccountContext
) : ViewModel() {
    class AmeConversationData(val type: ACType, val data: List<MessageRecord>, val unread: Long) {
        enum class ACType {
            RESET,
            MORE,
            NEW,
            UPDATE
        }
    }

    companion object {
        private const val TAG = "AmeConversationViewModel"
        private const val PAGE_COUNT = 100

        private const val NO_NEED_TO_SHOW = 0
        private const val SHOW_RESEND = 1
        private const val SHOW_NOT_FOUND = 2
    }

    private lateinit var repository: Repository

    private var mRecipient: Recipient? = null
    private var mThreadId = 0L
    private var mLastMessageTime = 0L
    private var mHasMore = true

    private var mLoadingMore = false
    private var clearMessageId = -10086L

    @Volatile
    private var isClearHistory = false

    private var hasMessageHandling = false
    private var hasProfileKeyRequest = false
    private var needRequestProfileKey = false

    private val identityRecords = IdentityRecordList()

    private val mMessageList = mutableListOf<MessageRecord>()

    var messageLiveData = MutableLiveData<AmeConversationData>()

    init {
        ALog.i(TAG, "onCreate mThread: $mThreadId")
        EventBus.getDefault().register(this)
    }

    override fun onCleared() {
        super.onCleared()
        ALog.i(TAG, "onCleared mThread: $mThreadId")
        EventBus.getDefault().unregister(this)
        RxBus.unSubscribe(PrivateChatRepo.CHANGED_TAG + mThreadId)
        RxBus.unSubscribe(ARouterConstants.PARAM.PARAM_HAS_REQUEST)
        mRecipient?.let { last ->
            RxBus.unSubscribe(last.address.toString())
        }
    }

    fun init(threadId: Long, recipient: Recipient) {
        ALog.logForSecret(TAG, "init threadId: $threadId, uid: ${recipient.address}")
        this.repository = Repository.getInstance(mAccountContext)!!
        this.mRecipient = recipient
        if (this.mRecipient != recipient) {
            mRecipient?.let { last ->
                RxBus.unSubscribe(last.address.toString())
            }
            RxBus.subscribe<Long>(recipient.address.toString()) { newThreadId ->
                updateThread(newThreadId)
            }
        }
        updateThread(threadId)
    }


    private fun updateThread(threadId: Long) {

        if (threadId != this.mThreadId) {
            ALog.i(TAG, "updateThread last: $mThreadId, new: $threadId")

            RxBus.unSubscribe(PrivateChatRepo.CHANGED_TAG + mThreadId)

            this.mThreadId = threadId

            this.mMessageList.clear()
            isClearHistory = false
            mLoadingMore = false
            clearMessageId = -10086L
            hasMessageHandling = false
            hasProfileKeyRequest = false
            needRequestProfileKey = false
            mHasMore = false

            RxBus.subscribe<PrivateChatEvent>(PrivateChatRepo.CHANGED_TAG + threadId) {
                ALog.i(TAG, "receive private chat event: ${it.threadId}")
                if (it.accountContext == mAccountContext && it.threadId == mThreadId) {
                    when (it.type) {
                        PrivateChatEvent.EventType.INSERT -> it.records.forEach { record -> insertMessage(record) }
                        PrivateChatEvent.EventType.DELETE -> deleteMessages(it.ids)
                        PrivateChatEvent.EventType.DELETE_ALL -> {
                            deleteAll()
                        }
                        PrivateChatEvent.EventType.DELETE_EXCEPT -> deleteExcept(it.ids)
                        PrivateChatEvent.EventType.UPDATE -> it.records.forEach { record -> updateMessage(record) }
                    }
                }
            }
            RxBus.unSubscribe(ARouterConstants.PARAM.PARAM_HAS_REQUEST)
            RxBus.subscribe<Recipient>(ARouterConstants.PARAM.PARAM_HAS_REQUEST) {
                if (it == mRecipient) {
                    hasProfileKeyRequest = true

                    if (hasMessageHandling) {
                        checkExchangeProfileKey(AppContextHolder.APP_CONTEXT)
                    }
                }
            }

            reload()
            checkProfileKeyUpdateToDate(AppContextHolder.APP_CONTEXT)
        }
    }

    fun getThreadId(): Long {
        return mThreadId
    }

    fun getRecipient(): Recipient? {
        return mRecipient
    }

    fun loadMore(callback: ((loading: Boolean) -> Unit)?) {
        if (!mHasMore) {
            callback?.invoke(false)
            return
        }
        if (mLoadingMore) {
            callback?.invoke(mLoadingMore)
            return
        }
        mLoadingMore = true
        callback?.invoke(mLoadingMore)
        val lastMid = if (mMessageList.isEmpty()) {
            0L
        } else {
            mMessageList.last().id
        }
        Observable.create<AmeConversationData> {
            val newMessages = repository.chatRepo.queryMessagesByPage(mThreadId, lastMid, PAGE_COUNT)
            synchronized(this) {
                mMessageList.addAll(newMessages)
                mHasMore = newMessages.size >= PAGE_COUNT
            }
            it.onNext(AmeConversationData(AmeConversationData.ACType.MORE, newMessages.toList(), -1))
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .subscribe({
                    mLoadingMore = false
                    messageLiveData.postValue(it)
                    callback?.invoke(mLoadingMore)
                }, {
                    ALog.logForSecret(TAG, "loadMore error", it)
                    mLoadingMore = false
                    callback?.invoke(mLoadingMore)
                })
    }

    fun reload() {
        if (mThreadId <= 0L) {
            return
        }
        Observable.create<AmeConversationData> {
            val chatRepo = repository.chatRepo
            val newMessages = chatRepo.queryMessagesByPage(mThreadId, Long.MAX_VALUE, PAGE_COUNT)
            val resultList: List<MessageRecord>
            synchronized(this) {
                mMessageList.clear()
                mMessageList.addAll(newMessages)
                resultList = mMessageList.toList()
                mHasMore = newMessages.size >= PAGE_COUNT
                if (mMessageList.isNotEmpty()) {
                    mLastMessageTime = mMessageList.first().dateSent
                }
            }
            it.onNext(AmeConversationData(AmeConversationData.ACType.RESET, resultList, chatRepo.getUnreadCount(mThreadId)))
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .subscribe({
                    messageLiveData.postValue(it)
                }, {
                    ALog.logForSecret(TAG, "reload error", it)
                })
    }


    fun updateThreadSnippet(context: Context, recipient: Recipient, drafts: DraftRepo.Drafts) {
        Observable.create(ObservableOnSubscribe<Long> {
            ALog.d(TAG, "updateThreadSnippet thread: $mThreadId, snippet: ${drafts.getSnippet(context)}, size: ${drafts.size}")
            val threadRepo = repository.threadRepo
            val draftRepo = repository.draftRepo

            if (drafts.size > 0) {
                if (mThreadId <= 0L) {
                    mThreadId = threadRepo.getThreadIdFor(recipient)
                }
                draftRepo.insertDrafts(mThreadId, drafts)
                threadRepo.updateThread(mThreadId, System.currentTimeMillis())
            } else if (mThreadId > 0L) {
                draftRepo.clearDrafts(mThreadId)
                threadRepo.updateThread(mThreadId)
            }

            it.onNext(mThreadId)
            it.onComplete()
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    updateThread(it)
                }, {
                    ALog.e(TAG, "updateThreadSnippet error", it)
                })
    }


    fun markThreadAsRead(context: Context, lastSeen: Boolean) {
        val threadId = mThreadId
        if (threadId > 0L) {
            Observable.create(ObservableOnSubscribe<Boolean> {
                val messageIds = repository.threadRepo.setRead(threadId, lastSeen)
                MarkReadReceiver.process(context, mAccountContext, messageIds)
                it.onNext(true)
                it.onComplete()
            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                    }, {
                        ALog.e(TAG, "markThreadAsRead error", it)
                    })
        }
    }

    fun markLastSeen() {
        val threadId = mThreadId
        if (threadId > 0L) {
            Observable.create(ObservableOnSubscribe<Boolean> {
                repository.threadRepo.setLastSeenTime(threadId)
                it.onNext(true)
                it.onComplete()
            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                    }, {
                        ALog.e(TAG, "markLastSeen error", it)
                    })
        }
    }


    fun loadDraft(callback: ((draft: String) -> Unit)?) {
        ALog.i(TAG, "loadDraft begin threadId: $mThreadId")
        if (mThreadId > 0L) {
            Observable.create(ObservableOnSubscribe<DraftRepo.Drafts> {
                val results = repository.draftRepo.getDrafts(mThreadId)
                it.onNext(results)
                it.onComplete()
            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ drafts ->
                        ALog.i(TAG, "loadDraft end size: ${drafts.size}")
                        val draftText = drafts.getSnippet(AppContextHolder.APP_CONTEXT)
                        if (!draftText.isNullOrEmpty()) {
                            callback?.invoke(draftText)
                        }
                    }, {
                        ALog.logForSecret(TAG, "loadDraft fail", it)
                    })
        }

    }

    fun sendTextMessage(context: Context, message: OutgoingTextMessage, callback: ((success: Boolean) -> Unit)? = null) {
        ALog.i(TAG, "sendTextMessage thread: $mThreadId")
        checkBeforeSendMessage(context) { doAfter ->
            Observable.create(ObservableOnSubscribe<Long> {
                val result = MessageSender.send(context, mAccountContext, message, mThreadId) {
                }
                it.onNext(result)
                it.onComplete()
            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        hasMessageHandling = true
                        updateThread(it)
                        doAfter()
                        callback?.invoke(true)
                    }, {
                        ALog.logForSecret(TAG, "sendTextMessage error", it)
                        doAfter()
                        callback?.invoke(false)
                    })
        }
    }


    fun sendMediaMessage(context: Context, masterSecret: MasterSecret, message: OutgoingMediaMessage, callback: ((success: Boolean) -> Unit)? = null) {
        checkBeforeSendMessage(context) { doAfter ->
            Observable.create(ObservableOnSubscribe<Long> {
                it.onNext(MessageSender.send(context, masterSecret, message, mThreadId, null))
                it.onComplete()
            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        hasMessageHandling = true
                        updateThread(it)
                        doAfter()
                        callback?.invoke(true)
                    }, {
                        ALog.logForSecret(TAG, "sendTextMessage error", it)
                        doAfter()
                        callback?.invoke(false)
                    })
        }
    }


    private fun sendHideMessage(context: Context, message: OutgoingLocationMessage, callback: ((success: Boolean) -> Unit)? = null) {
        Observable.create<Unit> {
            ALog.i("sendHideMessage", message.messageBody)
            it.onNext(MessageSender.sendHideMessage(context, getThreadId(), mAccountContext, message))
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback?.invoke(true)
                }, {
                    ALog.logForSecret(TAG, "sendHideMessage error", it)
                    callback?.invoke(false)
                })
    }


    fun clearConversationHistory(context: Context) {
        mRecipient?.let { recipient ->
            Observable.create<Any> { emitter ->
                val clearMessage = AmeGroupMessage(AmeGroupMessage.CONTROL_MESSAGE, AmeGroupMessage.ControlContent(AmeGroupMessage.ControlContent.ACTION_CLEAR_MESSAGE, Recipient.major().address.serialize(), "", 0L)).toString()
                val message = OutgoingLocationMessage(recipient, clearMessage, (recipient.expireMessages * 1000).toLong())
                MessageSender.send(context, mAccountContext, message, mThreadId) { messageId -> clearMessageId = messageId }
                isClearHistory = true
                emitter.onComplete()
            }.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                    }, {
                        isClearHistory = false
                    })
        }
    }

    fun checkLastDecryptFailTime(activity: FragmentActivity) {

        fun resendLastDecryptFailMessages(lastShowDialogTime: Long) {
            Observable.create<Unit> {
                val messages = repository.chatRepo.getDecryptFailedData(mThreadId, lastShowDialogTime)
                messages.forEach { record ->
                    MessageSender.resend(activity, getThreadId(), mAccountContext, record)
                }
                it.onComplete()
            }.subscribeOn(Schedulers.io())
                    .subscribe({}, {})
        }

        fun setLastShowDialogTime(data: DecryptFailData) {
            Observable.create<Unit> {
                data.lastShowDialogTime = AmeTimeUtil.serverTimeMillis()
                data.firstNotFoundMsgTime = 0L
                data.resetFailCount()
                repository.threadRepo.setDecryptFailData(mThreadId, data.toJson())
                it.onComplete()
            }.subscribeOn(Schedulers.io())
                    .subscribe({}, {})
        }

        if (mThreadId > 0L) {
            Observable.create<Pair<DecryptFailData, Long>> {
                val lastFailTime = repository.chatRepo.getLastCannotDecryptMessage(mThreadId)
                val dataJson = repository.threadRepo.getDecryptFailData(mThreadId)
                val data = if (!dataJson.isNullOrEmpty()) {
                    GsonUtils.fromJson(dataJson, DecryptFailData::class.java)
                } else {
                    DecryptFailData()
                }

                it.onNext(Pair(data, lastFailTime))
                it.onComplete()
            }.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        val lastFailTime = it.second
                        val data = it.first
                        val dialogType = when {
                            lastFailTime > data.lastShowDialogTime -> SHOW_RESEND
                            data.failMessageCount > 0 -> SHOW_NOT_FOUND
                            else -> NO_NEED_TO_SHOW
                        }
                        when (dialogType) {
                            SHOW_RESEND -> {
                                AmePopup.center.show(activity, AmeCenterPopup.PopConfig().apply {
                                    val time = data.lastShowDialogTime
                                    title = getString(R.string.chats_message_decrypt_fail_change_device_title)
                                    content = getString(R.string.chats_message_decrypt_fail_change_device)
                                    okTitle = getString(R.string.chats_resend)
                                    cancelTitle = getString(R.string.chats_cancel)
                                    ok = {
                                        // Resend
                                        resendLastDecryptFailMessages(time)
                                    }
                                })
                                setLastShowDialogTime(data)
                            }
                            SHOW_NOT_FOUND -> {
                                AmePopup.center.show(activity, AmeCenterPopup.PopConfig().apply {
                                    title = getString(R.string.chats_message_decrypt_fail_change_device_title)
                                    content = AppContextHolder.APP_CONTEXT.getString(R.string.chats_message_decrypt_fail_not_found,
                                            data.failMessageCount,
                                            DateUtils.formatHourTime(data.firstNotFoundMsgTime),
                                            DateUtils.formatDayTimeForMillisecond(data.firstNotFoundMsgTime))
                                    okTitle = getString(R.string.common_popup_ok)
                                })
                                setLastShowDialogTime(data)
                            }
                        }
                    }, {
                        ALog.e(TAG, "checkLastDecryptFailTime error", it)
                    })
        }
    }

    fun checkProfileKeyUpdateToDate(context: Context) {
        mRecipient?.let { recipient ->
            Observable.create<Pair<Boolean, Boolean>> {
                val hasRequest = if (mThreadId > 0L) {
                    repository.threadRepo.hasProfileRequest(mThreadId)
                } else {
                    false
                }
                val needProfileKey = recipient.resolve().checkNeedProfileKey()
                it.onNext(Pair(hasRequest, needProfileKey))
                it.onComplete()
            }.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        ALog.i(TAG, "checkProfileKeyUpdateToDate hasRequest: ${it.first}, needProfileKey: ${it.second}")
                        hasProfileKeyRequest = it.first
                        needRequestProfileKey = it.second

                        if (hasMessageHandling) {
                            checkExchangeProfileKey(context)
                        }
                    }, {
                        ALog.e(TAG, "checkProfileKeyUpdateToDate error", it)
                    })
        }
    }


    private fun checkBeforeSendMessage(context: Context, continueSend: (doAfterSend: () -> Unit) -> Unit) {

        fun doAfterSendIfNotFriend() {
            mRecipient?.let { recipient ->
                Observable.create(ObservableOnSubscribe<Long> {

                    val threadId = repository.threadRepo.getThreadIdFor(recipient)

                    val restrictBody = AmeGroupMessage(AmeGroupMessage.SYSTEM_INFO,
                            AmeGroupMessage.SystemContent(AmeGroupMessage.SystemContent.TIP_CHAT_STRANGER_RESTRICTION, mAccountContext.uid, listOf(recipient.address.serialize()), ""))
                    var expiresIn: Long = 0
                    if (recipient.expireMessages > 0) {
                        expiresIn = (recipient.expireMessages * 1000).toLong()
                    }
                    val textMessage = OutgoingLocationMessage(recipient, restrictBody.toString(), expiresIn)
                    val chatRepo = repository.chatRepo
                    val messageId = chatRepo.insertOutgoingTextMessage(threadId, textMessage, ChatTimestamp.getTime(mAccountContext, threadId), null)
                    chatRepo.setMessageSendSuccess(messageId)

                    it.onNext(threadId)
                    it.onComplete()
                }).subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            updateThread(it)

                        }, {
                            ALog.e(TAG, "checkBeforeSendMessage error", it)
                        })
            }
        }

        mRecipient?.let { recipient ->
            if (!recipient.isFriend && !recipient.privacyProfile.allowStranger) {
                ALog.i(TAG, "checkBeforeSendMessage recipient is not friend and not allow stranger, add warning")
                continueSend {
                    doAfterSendIfNotFriend()
                }
            } else {
                continueSend {
                    checkExchangeProfileKey(context)
                }
            }
        }
    }

    private fun checkExchangeProfileKey(context: Context) {

        if (!hasProfileKeyRequest && !needRequestProfileKey) {
            ALog.i(TAG, "checkExchangeProfileKey no need exchange")
            return
        }
        try {
            mRecipient?.let { recipient ->
                ALog.i(TAG, "checkExchangeProfileKey begin")
                val profileData = Recipient.major().privacyProfile
                val hasRequest = hasProfileKeyRequest
                val needProfileKey = needRequestProfileKey
                hasProfileKeyRequest = false
                needRequestProfileKey = false
                val profileContent = when {
                    needProfileKey -> {
                        ALog.i(TAG, "checkExchangeProfileKey Message type is ${AmeGroupMessage.ExchangeProfileContent.REQUEST}")
                        AmeGroupMessage(AmeGroupMessage.EXCHANGE_PROFILE, AmeGroupMessage.ExchangeProfileContent(profileData.nameKey
                                ?: "", profileData.avatarKey ?: "",
                                profileData.version, AmeGroupMessage.ExchangeProfileContent.REQUEST)).toString()
                    }
                    hasRequest -> {
                        ALog.i(TAG, "checkExchangeProfileKey Message type is ${AmeGroupMessage.ExchangeProfileContent.RESPONSE}")
                        AmeGroupMessage(AmeGroupMessage.EXCHANGE_PROFILE, AmeGroupMessage.ExchangeProfileContent(profileData.nameKey
                                ?: "", profileData.avatarKey ?: "",
                                profileData.version, AmeGroupMessage.ExchangeProfileContent.RESPONSE)).toString()
                    }
                    else -> return
                }
                if (!isReleaseBuild()) {
                    ALog.d(TAG, "checkExchangeProfile profileContent: $profileContent")
                }
                val message = OutgoingLocationMessage(recipient, profileContent, (recipient.expireMessages * 1000).toLong())
                sendHideMessage(context, message)
            }
        } catch (ex: Exception) {
            ALog.e(TAG, "checkExchangeProfileKey error", ex)
        }
    }

    private fun initializeIdentityRecords(context: Context) {
        mRecipient?.let { recipient ->
            Observable.create(ObservableOnSubscribe<Pair<IdentityRecordList, String>> {
                val identityRecordList = IdentityRecordList()
                val recipients = LinkedList<Recipient>()

                if (!recipient.isGroupRecipient) {
                    recipients.add(recipient)
                }

                for (r in recipients) {
                    identityRecordList.add(repository.identityRepo.getIdentityForNonBlockingApproval(r.address.serialize()))
                }

                var message = ""
                if (identityRecordList.isUnverified) {
                    message = IdentityUtil.getUnverifiedBannerDescription(context, identityRecordList.getUnverifiedRecipients(mAccountContext))
                            ?: ""
                }

                it.onNext(Pair(identityRecordList, message))
                it.onComplete()

            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        identityRecords.replaceWith(it.first)
                    }, {
                        ALog.logForSecret(TAG, "initializeIdentityRecords error", it)
                    })
        }
    }


    private fun insertMessage(record: MessageRecord) {
        ALog.i(TAG, "insert")
        Observable.create<AmeConversationData> {
            val data: AmeConversationData
            synchronized(this) {
                val chatRepo = repository.chatRepo
                if (record.dateSent < mLastMessageTime) {
                    for ((index, message) in mMessageList.withIndex()) {
                        if (message.dateSent < record.dateSent) {
                            mMessageList.add(index, record)
                            break
                        }
                    }
                    data = AmeConversationData(AmeConversationData.ACType.UPDATE, mMessageList.toList(), chatRepo.getUnreadCount(mThreadId))
                } else {
                    mMessageList.add(0, record)
                    data = AmeConversationData(AmeConversationData.ACType.NEW, listOf(record), chatRepo.getUnreadCount(mThreadId))
                }
                mLastMessageTime = mMessageList.first().dateSent
            }
            it.onNext(data)
            it.onComplete()

        }.subscribeOn(Schedulers.io())
                .subscribe({
                    messageLiveData.postValue(it)
                }, {
                    ALog.e(TAG, "insertMessage error", it)
                })
    }

    private fun deleteMessage(record: MessageRecord) {

        Observable.create<AmeConversationData> {
            val data: AmeConversationData
            synchronized(this) {
                for ((i, r) in mMessageList.withIndex()) {
                    if (r.id == record.id) {
                        mMessageList.remove(r)
                        break
                    }
                }
                if (mMessageList.isNotEmpty()) {
                    mLastMessageTime = mMessageList.first().dateSent
                } else {
                    mLastMessageTime = -1
                }
                data = AmeConversationData(AmeConversationData.ACType.UPDATE, mMessageList.toList(), -1)
            }
            it.onNext(data)
            it.onComplete()

        }.subscribeOn(Schedulers.io())
                .subscribe({
                    messageLiveData.postValue(it)
                }, {
                    ALog.e(TAG, "deleteMessage error", it)
                })
    }

    private fun deleteAll() {
        Observable.create<AmeConversationData> {
            val data: AmeConversationData
            synchronized(this) {
                mMessageList.clear()
                mLastMessageTime = -1
                data = AmeConversationData(AmeConversationData.ACType.UPDATE, mMessageList.toList(), -1)
            }
            it.onNext(data)
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .subscribe({
                    messageLiveData.postValue(it)
                }, {
                    ALog.e(TAG, "deleteAll error", it)
                })
    }

    private fun deleteMessages(records: List<Long>) {
        Observable.create<AmeConversationData> {
            val data: AmeConversationData
            synchronized(this) {
                val willDeleteList = mutableListOf<MessageRecord>()
                mMessageList.forEach { record ->
                    if (records.find { id -> id == record.id } != null) {
                        willDeleteList.add(record)
                    }
                }
                mMessageList.removeAll(willDeleteList)
                if (mMessageList.isNotEmpty()) {
                    mLastMessageTime = mMessageList.first().dateSent
                } else {
                    mLastMessageTime = -1
                }
                data = AmeConversationData(AmeConversationData.ACType.UPDATE, mMessageList.toList(), -1)
            }

            it.onNext(data)
            it.onComplete()

        }.subscribeOn(Schedulers.io())
                .subscribe({
                    messageLiveData.postValue(it)
                }, {
                    ALog.e(TAG, "deleteMessages error", it)
                })
    }

    private fun deleteExcept(ids: List<Long>) {
        Observable.create<AmeConversationData> {
            val data: AmeConversationData
            synchronized(this) {
                val willRemainList = mutableListOf<MessageRecord>()
                mMessageList.forEach { record ->
                    if (ids.find { id -> id == record.id } != null) {
                        willRemainList.add(record)
                    }
                }
                mMessageList.clear()
                mMessageList.addAll(willRemainList)
                if (mMessageList.isNotEmpty()) {
                    mLastMessageTime = mMessageList.first().dateSent
                } else {
                    mLastMessageTime = -1
                }
                data = AmeConversationData(AmeConversationData.ACType.UPDATE, mMessageList.toList(), -1)
            }
            it.onNext(data)
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .subscribe({
                    messageLiveData.postValue(it)
                }, {
                    ALog.e(TAG, "deleteExcept error", it)
                })
    }

    private fun updateMessage(record: MessageRecord) {
        Observable.create<AmeConversationData> {
            val data: AmeConversationData
            synchronized(this) {
                for (i in mMessageList.indices) {
                    val oldRecord = mMessageList[i]
                    if (oldRecord.id == record.id) {
                        mMessageList[i] = record
                        if (oldRecord.dateSent < record.dateSent) {
                            mMessageList.sortByDescending { msg -> msg.dateSent }
                        }
                        break
                    }
                }
                data = AmeConversationData(AmeConversationData.ACType.UPDATE, mMessageList.toList(), -1)
            }
            it.onNext(data)
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .subscribe({
                    messageLiveData.postValue(it)
                }, {
                    ALog.e(TAG, "deleteExcept error", it)
                })
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onIdentityRecordUpdate(event: IdentityRecord) {
        initializeIdentityRecords(AppContextHolder.APP_CONTEXT)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventTextSendState(sendEvent: TextSendEvent) {
        if (mAccountContext != sendEvent.accountContext) {
            return
        }
        if (clearMessageId == -10086L)
            return
        if (clearMessageId == sendEvent.messageId) {
            clearMessageId = -10086L
            if (sendEvent.success) {
                Observable.create(ObservableOnSubscribe<Boolean> { emitter ->
                    try {
                        repository.threadRepo.clearConversationExcept(mThreadId, listOf(sendEvent.messageId))
                        emitter.onNext(true)

                    } catch (t: Throwable) {
                        emitter.onNext(false)
                    } finally {
                        emitter.onComplete()
                    }
                }).subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe { result ->
                            if (result) {
                                AmeAppLifecycle.succeed(getString(R.string.chats_user_clear_success), true)

                            } else {
                                AmeAppLifecycle.failure(getString(R.string.chats_user_clear_fail), true)
                            }
                        }
            } else {
                AmeAppLifecycle.failure(getString(R.string.chats_user_clear_fail), true)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(e: MessageReceiveNotifyEvent) {
        val recipient = mRecipient
        if (mAccountContext == e.accountContext && recipient?.address?.serialize() == e.source) {
            ALog.i(TAG, "receive messageReceiveNotifyEvent")
            updateThread(e.threadId)
        }

    }
}