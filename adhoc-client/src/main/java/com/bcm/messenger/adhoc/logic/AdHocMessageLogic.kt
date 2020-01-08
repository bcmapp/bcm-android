package com.bcm.messenger.adhoc.logic

import android.net.Uri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProviders
import com.bcm.messenger.adhoc.sdk.AdHocChatMessage
import com.bcm.messenger.adhoc.sdk.AdHocSDK
import com.bcm.messenger.adhoc.sdk.AdHocSessionSDK
import com.bcm.messenger.adhoc.sdk.AdHocSessionStatus
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.utils.AmePushProcess
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.log

/**
 * adhoc logic class
 */
class AdHocMessageLogic(private val accountContext: AccountContext) : AdHocSessionSDK.IAdHocSessionEventListener, AdHocChannelLogic.IAdHocChannelListener, AdHocMessageModel.OnModelListener {
    companion object {
        private const val TAG = "AdHocMessageLogic"

        private var logic: AdHocMessageLogic? = null
        fun get(accountContext: AccountContext): AdHocMessageLogic {
            synchronized(TAG) {
                var logic = this.logic
                return if (null != logic && logic.accountContext == accountContext) {
                    logic
                } else {
                    logic = AdHocMessageLogic(accountContext)
                    this.logic = logic
                    logic
                }
            }
        }

        fun remove() {
            logic = null
        }
    }

    /**
     * wait for resend queue
     */
    data class WaitQueueData(var ready: AtomicInteger, var queue: Queue<AdHocMessageDetail>, var waitDisposable: Disposable? = null) {}


    private val mMessageStoreQueue: Queue<AdHocMessageDetail> = ConcurrentLinkedQueue()
    private var mWaitQueueMap: ConcurrentHashMap<String, WaitQueueData> = ConcurrentHashMap() //if key is not nullorempty: private chat waitting queue，wait for opposite connect;if null or empty: group chat，need major connected。

    private val mCache: AdHocMessageCache = AdHocMessageCache(accountContext)
    private var mModel: AdHocMessageModel? = null
    private var mAddingDisposable: Disposable? = null
    private val messengerSdk = AdHocSDK.messengerSdk
    /**
     * private chat is base on onSessionStatusChanged，and group chat is base on onScanStateChanged
     */
    private var mCurrentState: Int? = null

    init {
        AdHocChannelLogic.get(accountContext).addListener(this)
        messengerSdk.addEventListener(this)
    }

    override fun onRecycle() {
        ALog.i(TAG, "onRecycle")
        mModel = null
    }

    fun getModel(): AdHocMessageModel? {
        return mModel
    }

    fun initModel(activity: FragmentActivity, session: String) {
        if (mModel == null && !activity.isFinishing && !activity.isDestroyed) {
            mModel = ViewModelProviders.of(activity).get(AdHocMessageModel::class.java)
        }
        mModel?.init(mCache, session, this@AdHocMessageLogic)
    }

    fun myAdHocId(): String? {
        return try {
            Recipient.major().address.serialize()
        } catch (ex: Exception) {
            null
        }
    }

    fun resend(message: AdHocMessageDetail) {
        AmeDispatcher.io.dispatch {
            handleForWaitQueue(message)
        }
    }

    fun sendInvite(session: String, myNick: String, channel: String, password: String) {
        val message = AdHocMessageDetail(0, session, myAdHocId() ?: return).apply {
            sendByMe = true
            nickname = myNick
            isRead = false
            setMessageBody(AmeGroupMessage(AmeGroupMessage.ADHOC_INVITE, AmeGroupMessage.AirChatContent(channel, password)))
        }
        send(session, myNick, message)
    }

    /**
     * send text
     */
    fun send(session: String, myNick: String, text: String, atList: Set<String>) {
        ALog.i(TAG, "send session: $session myNick: $myNick text: $text")
        val message = AdHocMessageDetail(0, session, myAdHocId() ?: return).apply {
            sendByMe = true
            nickname = myNick
            setText(text)
            isRead = false
            this.atList = atList
        }
        send(session, myNick, message)
    }

    /**
     * send attachment
     */
    fun send(session: String, myNick: String, attachmentContent: AmeGroupMessage.AttachmentContent) {
        var type = AmeGroupMessage.IMAGE
        when (attachmentContent) {
            is AmeGroupMessage.ImageContent -> {
                type = AmeGroupMessage.IMAGE
            }
            is AmeGroupMessage.VideoContent -> {
                type = AmeGroupMessage.VIDEO
            }
            is AmeGroupMessage.FileContent -> {
                type = AmeGroupMessage.FILE
            }
            is AmeGroupMessage.AudioContent -> {
                type = AmeGroupMessage.AUDIO
            }
        }
        val message = AdHocMessageDetail(0, session, myAdHocId() ?: return).apply {
            sendByMe = true
            nickname = myNick
            setMessageBody(AmeGroupMessage(type, attachmentContent))
            attachmentUri = attachmentContent.url //attachmentContent url save path
            thumbnailUri = attachmentContent.url //attachmentContent url save path
            ALog.i(TAG, "prepare to send attachment: thumbnail: $thumbnailUri, attachmentUri: $attachmentUri")
            attachmentState = false
            isRead = true
            time = System.currentTimeMillis()
        }
        send(session, myNick, message)
    }

    /**
     * real send logic
     */
    fun send(sessionId: String, myNick: String, message: AdHocMessageDetail, callback: ((result: AdHocMessageDetail?) -> Unit)? = null) {
        val session = AdHocSessionLogic.get(accountContext).getSession(sessionId)
        if (session == null) {
            ALog.w(TAG, "send message fail, session is null: $sessionId")
            callback?.invoke(null)
            return
        }
        ALog.i(TAG, "send sessionId: $sessionId myNick: $myNick text: ${message.getMessageBodyJson()}")
        val key = if (session.isChannel()) "" else sessionId
        val queueData = mWaitQueueMap[key]
        if (queueData?.ready?.get() == AdHocSessionStatus.READY.ordinal) {
            message.isSending = true
            message.isRead = true
            message.sendByMe = true
            message.time = System.currentTimeMillis()
            addStoreForOutgoing(message) { finalMessage ->
                if (finalMessage == null) {
                    ALog.w(TAG, "send message sessionId: $sessionId fail, addStoreFoOutgoing fail")
                    callback?.invoke(null)
                    return@addStoreForOutgoing
                }
                val content = finalMessage.getMessageBody()?.content
                if (content is AmeGroupMessage.AttachmentContent) {
                    val adHocSession = AdHocSessionLogic.get(accountContext).getSession(sessionId)
                    if (adHocSession == null) {
                        ALog.w(TAG, "send attachment fail, sessionInfo is null")
                        callback?.invoke(null)
                        return@addStoreForOutgoing
                    }
                    val file = File(BcmFileUtils.getFileAbsolutePath(AppContextHolder.APP_CONTEXT, Uri.parse(content.url)))
                    ALog.i(TAG, "actual send attachment file: ${file.absolutePath}")
                    if (adHocSession.isChannel()) {
                        messengerSdk.sendChannelFileMessage(sessionId, myNick, file, finalMessage.getMessageBodyJson()) { mid, succeed ->
                            ALog.i(TAG, "send channel file success: $succeed, mid: $mid")
                            finalMessage.mid = mid
                            finalMessage.isSending = true //if opposite received, be complete
                            finalMessage.success = succeed
                            addStoreForOutgoing(finalMessage) {
                                tryHandleWaitingQueue(key)
                                callback?.invoke(it)
                            }
                        }
                    } else {
                        messengerSdk.sendChatFileMessage(sessionId, myNick, file, finalMessage.getMessageBodyJson()) { mid, succeed ->
                            ALog.i(TAG, "send chat file success: $succeed, mid: $mid")
                            finalMessage.mid = mid
                            finalMessage.isSending = true
                            finalMessage.success = succeed
                            addStoreForOutgoing(finalMessage) {
                                tryHandleWaitingQueue(key)
                                callback?.invoke(it)
                            }
                        }
                    }

                } else {
                    messengerSdk.send(finalMessage.sessionId, finalMessage.nickname, finalMessage.atList
                            ?: setOf(), message.getMessageBodyJson()) { mid, succeed ->
                        ALog.i(TAG, "send text success: $succeed, mid: $mid")
                        finalMessage.mid = mid
                        finalMessage.success = succeed
                        finalMessage.isSending = false
                        addStoreForOutgoing(finalMessage) {
                            tryHandleWaitingQueue(key)
                            callback?.invoke(it)
                        }
                    }
                }
            }
        } else {
            ALog.w(TAG, "send message failed, not ready")
            message.success = false
            message.isSending = true
            message.isRead = true
            message.sendByMe = true
            message.time = System.currentTimeMillis()
            addStoreForOutgoing(message, true) {
                callback?.invoke(it)
            }
        }
    }

    override fun onReceiveFileComplete(sessionId: String, mid: String, uri: Uri) {
        ALog.i(TAG, "onReceiveFileComplete session: $sessionId, mid: $mid, uri: $uri")
        onAttachmentHandle(sessionId, mid, 1.0f, uri)
    }

    override fun onReceiveFileFailed(sessionId: String, mid: String, reason: Int) {
        ALog.w(TAG, "onReceiveFileFailed session: $sessionId, mid: $mid, reason: $reason")
        onAttachmentHandle(sessionId, mid, 1.0f, null)
    }

    override fun onReceivingFile(sessionId: String, mid: String, total: Long, progress: Long) {
        val p = progress.toFloat() / total
        if (p < 1.0f) {
            onAttachmentHandle(sessionId, mid, p, null)
        }
    }

    override fun onSendFileComplete(sessionId: String, mid: String) {
        ALog.i(TAG, "onSendFileComplete sessionId: $sessionId, mid: $mid")
        Observable.create<AdHocMessageDetail> {
            val m = mCache.getMessageDetail(sessionId, mid)
                    ?: throw Exception("AdHocMessageDetail is null")
            m.isAttachmentDownloading = false
            m.attachmentState = true
            m.isSending = false
            m.success = true
            mCache.updateMessage(m)
            it.onNext(m)
            it.onComplete()

        }.subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    val model = getModel()
                    if (model?.getSessionId() == sessionId) {
                        model.onUpdateMessage(listOf(it))
                    }

                }, {
                    ALog.e(TAG, "onSendFileComplete error", it)
                })
    }

    override fun onSendFileFailed(sessionId: String, mid: String) {
        ALog.i(TAG, "onSendFileFailed sessionId: $sessionId, mid: $mid")
        Observable.create<AdHocMessageDetail> {
            val m = mCache.getMessageDetail(sessionId, mid)
                    ?: throw Exception("AdHocMessageDetail is null")
            m.isAttachmentDownloading = false
            m.attachmentState = false
            m.isSending = false
            m.success = false
            mCache.updateMessage(m)
            it.onNext(m)
            it.onComplete()
        }.subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    val model = getModel()
                    if (model?.getSessionId() == sessionId) {
                        model.onUpdateMessage(listOf(it))
                    }

                }, {
                    ALog.e(TAG, "onSendFileFailed error", it)
                })
    }

    /**
     * receive message
     */
    override fun onReceiveMessage(sessionId: String, atMe: Boolean, message: AdHocChatMessage) {

        fun gotoReceive(sessionId: String, atMe: Boolean, message: AdHocChatMessage) {
            val adHockMessage = AdHocMessageDetail(0, sessionId, message.fromId).apply {
                mid = message.messageId
                sendByMe = false
                success = true
                time = message.timestamp
                nickname = message.nickname
                setMessageBodyJson(message.text)
                isRead = false
                isAtMe = atMe
                isSending = false
                if (getMessageBody()?.content is AmeGroupMessage.AttachmentContent) {
                    isAttachmentDownloading = true
                }
                attachmentDigest = message.fileDigest
            }
            addStoreQueue(adHockMessage)
        }

        ALog.i(TAG, "onReceiveMessage from:${message.nickname} session: ${message.sessionName}, message: ${message.text}, mid: ${message.messageId}")
        val sessionInfo = AdHocSessionLogic.get(accountContext).getSession(sessionId)
        if (sessionInfo == null) {
            if (!message.isChannel) {
                AdHocSessionLogic.get(accountContext).addChatSession(message.fromId) {
                    if (it == sessionId) {
                        gotoReceive(sessionId, atMe, message)
                    } else {
                        ALog.w(TAG, "onReceiveMessage fail, sessionId not same")
                    }
                }
            }
        } else {
            gotoReceive(sessionId, atMe, message)
        }

    }

    override fun onScanStateChanged(state: AdHocChannelLogic.IAdHocChannelListener.CONNECT_STATE) {
        val ns = if (state == AdHocChannelLogic.IAdHocChannelListener.CONNECT_STATE.CONNECTED) AdHocSessionStatus.READY.ordinal else AdHocSessionStatus.CONNECTING.ordinal
        ALog.i(TAG, "onScanStateChanged state: $state")
        if (mCurrentState == ns) {
            return
        }
        mCurrentState = ns
        handleForWaitQueue("", ns)
    }

    override fun onSessionStatusChanged(sessionId: String, status: AdHocSessionStatus) {
        ALog.i(TAG, "onSessionStatusChanged sessionId: $sessionId, status: $status")
        handleForWaitQueue(sessionId, status.ordinal)
    }


    fun updateSessionUnread(session: String, unread: Int, reset: Boolean = false) {
        val sessionInfo = AdHocSessionLogic.get(accountContext).getSession(session)
        sessionInfo?.let {
            var current = it.unreadCount
            if (reset) {
                current = unread
            } else {
                current += unread
            }
            AdHocSessionLogic.get(accountContext).updateUnreadCount(session, current)
        }
    }

    private fun tryHandleWaitingQueue(queueKey: String) {
        val queueData = mWaitQueueMap[queueKey]
        if (queueData == null) {
            ALog.w(TAG, "tryHandleWaitingQueue no wait queue for key: $queueKey")
            return
        }
        if (queueData.waitDisposable == null || queueData.waitDisposable?.isDisposed == true) {
            queueData.waitDisposable = Observable.create<AdHocMessageDetail> { emiter ->

                val queue = queueData.queue
                var m: AdHocMessageDetail? = null
                var count = 0
                val complete = AtomicInteger(0)
                val finish = AtomicBoolean(false)
                var isWaited = false //need sleep wait
                fun response(complete: Int) {
                    if (complete >= count && finish.get()) {
                        emiter.onComplete()
                    }
                }
                loop@ do {
                    m = queue.poll()
                    if (m != null) {
                        when (queueData.ready.get()) {
                            AdHocSessionStatus.READY.ordinal -> {
                                count++
                                val self = Recipient.major()
                                send(m.sessionId, self.name, m) { new ->
                                    response(complete.addAndGet(1))
                                }
                            }
                            AdHocSessionStatus.CONNECTING.ordinal -> { //can't send，go back queue
                                m.isSending = true
                                if (!queue.offer(m)) {
                                    ALog.w(TAG, "tryHandleWaitingQueue status is connecting, offer m: ${m.sessionId}, index: ${m.indexId}")
                                }
                                break@loop
                            }
                            else -> {
                                count++
                                m.isSending = false
                                m.attachmentState = false
                                m.success = false
                                m.isAttachmentDownloading = false
                                m.isRead = true
                                addStoreForOutgoing(m) {
                                    response(complete.addAndGet(1))
                                }
                            }
                        }
                    } else {
                        if (isWaited) {
                            break
                        } else {
                            isWaited = true
                            Thread.sleep(1000)
                        }
                    }

                } while (true)
                finish.set(true)
                response(complete.get())

            }.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnComplete {
                        queueData.waitDisposable = null
                    }
                    .doOnError {
                        queueData.waitDisposable = null
                    }
                    .subscribe({ result ->
                        ALog.i(TAG, "tryHandleWaitingQueue subscribe: session: ${result.sessionId}, message: ${result.getMessageBodyJson()}")
                    }, {
                        ALog.e(TAG, "tryHandleWaitingQueue fail", it)
                    })

        } else {
            ALog.w(TAG, "tryHandleWaitingQueue do nothing, waitDisposable is exist")
        }
    }

    private fun handleForWaitQueue(message: AdHocMessageDetail) {
        val session = AdHocSessionLogic.get(accountContext).getSession(message.sessionId)
        if (session == null) {
            ALog.w(TAG, "resend fail, session is null: ${message.sessionId}")
            message.success = false
            message.isSending = false
            message.isAttachmentDownloading = false
            message.attachmentState = false
            addStoreForOutgoing(message)
            return
        }

        val key = if (session.isChannel()) "" else session.sessionId
        var data = mWaitQueueMap[key]
        if (data == null) {
            data = WaitQueueData(AtomicInteger(AdHocSessionStatus.CONNECTING.ordinal), ConcurrentLinkedQueue())
            mWaitQueueMap[key] = data
        }
        if (!data.queue.offer(message)) {
            ALog.w(TAG, "handleForWaitQueue fail")
        }
        if (data.ready.get() == AdHocSessionStatus.READY.ordinal) {
            ALog.i(TAG, "handleForWaitQueue, ready, tryHandleWaitingQueue")
            tryHandleWaitingQueue(key)
        } else if (data.ready.get() == AdHocSessionStatus.TIMEOUT.ordinal) {
            if (session.isChat()) {
                AdHocSessionLogic.get(accountContext).addChatSession(session.uid) {
                    ALog.i(TAG, "handleForWaitQueue finish, addChatSession result: $it")
                }
            }
        }
    }

    private fun handleForWaitQueue(key: String, status: Int) {
        var data = mWaitQueueMap[key]
        if (data == null) {
            data = WaitQueueData(AtomicInteger(status), ConcurrentLinkedQueue())
            mWaitQueueMap[key] = data
        } else {
            data.ready.set(status)
        }
        if (status == AdHocSessionStatus.READY.ordinal || status == AdHocSessionStatus.TIMEOUT.ordinal) {
            tryHandleWaitingQueue(key)
        }
    }


    private fun addStoreForOutgoing(message: AdHocMessageDetail, waitToReSend: Boolean = false, callback: ((message: AdHocMessageDetail?) -> Unit)? = null) {
        val add = message.indexId == 0L
        Observable.create<AdHocMessageDetail> {
            it.onNext(mCache.updateMessage(message))
            it.onComplete()
        }.subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext {
                    val model = getModel()
                    if (it.sessionId == model?.getSessionId()) {
                        if (add) {
                            model.onReceiveMessage(listOf(it))
                        } else {
                            model.onUpdateMessage(listOf(it))
                        }
                    }
                    if (waitToReSend) {
                        handleForWaitQueue(it)
                    }
                }
                .observeOn(AmeDispatcher.ioScheduler)
                .subscribe({
                    callback?.invoke(it)
                }, {
                    ALog.e(TAG, "addStoreForOutgoing fail", it)
                    callback?.invoke(null)
                })
    }

    private fun addStoreQueue(message: AdHocMessageDetail) {
        ALog.i(TAG, "addStoreQueue, message session: ${message.sessionId}, message text: ${message.getMessageBodyJson()}, message id: ${message.mid}")
        if (!mMessageStoreQueue.offer(message)) {
            ALog.w(TAG, "addStoreQueue fail")
        }
        if (mAddingDisposable == null || mAddingDisposable?.isDisposed == true) {
            mAddingDisposable = Observable.create<AdHocMessageDetail> {

                var m: AdHocMessageDetail? = null
                var isWaited = false
                do {
                    m = mMessageStoreQueue.poll()
                    if (m != null) {
                        m = mCache.updateMessage(m)
                        if (m.getMessageBody()?.content is AmeGroupMessage.AttachmentContent) {
                            m = mCache.mergeCacheForAttachment(m.sessionId, m.mid, m)
                        }

                        val session = AdHocSessionLogic.get(accountContext).getSession(m.sessionId)
                        if (session != null && session.isChat()) {
                            val recipient = Recipient.from(accountContext, m.fromId, false)
                            if (recipient.profileName != m.nickname) {
                                Repository.getRecipientRepo(accountContext)?.setProfileName(recipient, m.nickname)
                            }
                        }
                        it.onNext(m)

                    } else {
                        if (isWaited) {
                            break
                        } else {
                            isWaited = true
                        }
                        ALog.w(TAG, "addStoreQueue poll message null, wait 1 second")
                        Thread.sleep(1000L)
                    }

                } while (true)
                it.onComplete()

            }.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnComplete {
                        mAddingDisposable = null
                    }
                    .doOnError {
                        mAddingDisposable = null
                    }
                    .subscribe({ result ->
                        val model = getModel()
                        if (result.sessionId == model?.getSessionId()) {
                            model.onReceiveMessage(listOf(result))
                        }
                        updateSessionUnread(result.sessionId, 1)

                        val isAtMe = result.isAtMe
                        AmePushProcess.processPush(accountContext, AmePushProcess.BcmData(AmePushProcess.BcmNotify(AmePushProcess.ADHOC_NOTIFY, 0, null, null, null, AmePushProcess.AdHocNotifyData(result.sessionId, isAtMe))))

                    }, {
                        ALog.e(TAG, "addStoreQueue fail", it)
                    })
        } else {
            ALog.w(TAG, "addStoreQueue nothing, mAddingDisposable is exist")
        }
    }

    /**
     * handle attachment
     */
    private fun onAttachmentHandle(sessionId: String, mid: String, progress: Float, uri: Uri?) {

        Observable.create<AdHocMessageDetail> {
            val m = mCache.mergeCacheForAttachment(sessionId, mid, progress, uri)
            if (m != null) {
                it.onNext(m)
            } else {
                ALog.w(TAG, "onAttachmentHandle getMessage null: session: $sessionId, mid: $mid")
            }
            it.onComplete()

        }.subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    val model = getModel()
                    if (model?.getSessionId() == sessionId) {
                        if (progress < 1.0f) {
                            model.onAttachmentProgress(it, progress)
                        } else {
                            model.onUpdateMessage(listOf(it)) //notify chat window
                        }
                    }
                }, {
                    ALog.e(TAG, "onAttachmentHandle error", it)
                })
    }
}