package com.bcm.messenger.adhoc.sdk

import android.net.Uri
import com.bcm.imcore.IAdHocBinder
import com.bcm.imcore.IAdHocMessageListener
import com.bcm.imcore.im.MessageStore
import com.bcm.imcore.im.util.SessionStatus
import com.bcm.messenger.adhoc.logic.AdHocMessageLogic
import com.bcm.messenger.adhoc.util.AdHocUtil
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.GsonUtils
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.HashMap

class AdHocSessionSDK : IAdHocMessageListener.Stub() {
    companion object {
        private const val TAG = "AdHocSessionSDK"
    }

    private var sdkApi: IAdHocBinder? = null
    private val eventListenerSet = Collections.newSetFromMap(WeakHashMap<IAdHocSessionEventListener, Boolean>())
    private val sendingMap = HashMap<String, SendingChat>()
    private var transferFileThread: Scheduler? = null
    private lateinit var accountContext: AccountContext

    fun init(accountContext: AccountContext, sdkApi: IAdHocBinder) {
        this.sdkApi = sdkApi
        this.accountContext = accountContext
        sdkApi.addMessageListener(this)
        transferFileThread = Schedulers.from(Executors.newSingleThreadExecutor())
    }

    fun unInit() {
        this.sdkApi?.removeMessageListener(this)
        this.sdkApi = null
        transferFileThread?.shutdown()
        transferFileThread = null
    }

    fun addEventListener(listener: IAdHocSessionEventListener) {
        AmeDispatcher.singleScheduler.scheduleDirect {
            eventListenerSet.add(listener)
        }
    }

    /**
     * send text
     */
    fun send(sessionId: String, myName: String, atList: Set<String>, message: String, result: (mid: String, succeed: Boolean) -> Unit) {
        AmeDispatcher.singleScheduler.scheduleDirect {
            try {
                val chat = AdHocMessagePack.ChatMessage(atList.toList(), message)
                ALog.i(TAG, "sendChatMessage begin")
                val mid = sdkApi?.sendMessage(sessionId, myName, chat.toString())
                ALog.i(TAG, "sendChatMessage end, mid: $mid")
                if (mid.isNullOrEmpty()) {
                    result("", false)
                } else {
                    result(mid, true)
                }
            } catch (e: Throwable) {
                ALog.e(TAG, "send", e)
            }
        }
    }

    private fun sendAck(sessionId: String, mid: String) {
        AmeDispatcher.singleScheduler.scheduleDirect {
            try {
                val chat = AdHocMessagePack.ACKMessage(mid)
                val id = sdkApi?.sendMessage(sessionId, "", chat.toString())
                if (id.isNullOrEmpty()) {
                    ALog.e(TAG, "send ack failed $sessionId $mid")
                }
            } catch (e: Throwable) {
                ALog.e(TAG, "send", e)
            }
        }
    }

    fun sendChannelFileMessage(sessionId: String, myName: String, file: File, message: String, result: (mid: String, succeed: Boolean) -> Unit) {
        AmeDispatcher.singleScheduler.scheduleDirect {
            try {
                val sdk = this.sdkApi
                if (sdk == null) {
                    ALog.e(TAG, "sendChannelFileMessage failed: sdk not ready")
                    result("", false)
                    return@scheduleDirect
                }

                val digest = AdHocUtil.digest(file)
                if (digest.isEmpty()) {
                    ALog.e(TAG, "sendChannelFileMessage failed: digest calc fail")
                    result("", false)
                    return@scheduleDirect
                }

                val chat = AdHocMessagePack.FileChatMessage(message, file.length(), digest)

                val mid = sdk.sendMessage(sessionId, myName, chat.toString())
                if (mid.isNullOrEmpty()) {
                    ALog.e(TAG, "sendChannelFileMessage failed: mid $mid")
                    result("", false)
                } else {
                    transferFileThread?.scheduleDirect({
                        ALog.i(TAG, "sendChannelFileMessage sending: mid $mid")
                        sdk.sendFile(sessionId, mid, file.absolutePath, 0)
                    }, 500, TimeUnit.MICROSECONDS)
                    result(mid, true)
                }
            } catch (e: Throwable) {
                ALog.e(TAG, "sendChannelFileMessage", e)
                result("", false)
            }
        }
    }

    fun sendChatFileMessage(sessionId: String, myName: String, file: File, message: String, result: (mid: String, succeed: Boolean) -> Unit) {
        AmeDispatcher.singleScheduler.scheduleDirect {
            try {
                val sdk = this.sdkApi
                if (sdk == null) {
                    ALog.e(TAG, "sendChatFileMessage failed: sdk not ready")
                    result("", false)
                    return@scheduleDirect
                }

                val digest = AdHocUtil.digest(file)
                if (digest.isEmpty()) {
                    ALog.e(TAG, "sendChatFileMessage failed: digest calc fail")
                    result("", false)
                    return@scheduleDirect
                }

                val chat = AdHocMessagePack.FileChatMessage(message, file.length(), digest)
                val mid = sdk.sendMessage(sessionId, myName, chat.toString())
                if (mid.isNullOrEmpty()) {
                    ALog.e(TAG, "sendChatFileMessage failed: mid $mid")
                    result("", false)
                } else {
                    transferFileThread?.scheduleDirect({
                        ALog.i(TAG, "sendChatFileMessage sending: mid $mid")
                        sdk.sendFile(sessionId, mid, file.absolutePath, 0)
                    }, 500, TimeUnit.MICROSECONDS)
                    result(mid, true)
                }
            } catch (e: Throwable) {
                ALog.e(TAG, "send", e)
                result("", false)
            }
        }
    }

    fun addChannel(channelName: String, passwd: String, result: (sessionId: String) -> Unit): Boolean {
        if (!AdHocSDK.isReady()) {
            return false
        }
        AmeDispatcher.singleScheduler.scheduleDirect {
            try {
                val sessionId = sdkApi?.addChannel(channelName, passwd)
                result(sessionId ?: "")
            } catch (e: Throwable) {
                ALog.e(TAG, "addChannel", e)

                result("")
            }
        }

        return true
    }

    fun removeChannel(sessionId: String) {
        AmeDispatcher.singleScheduler.scheduleDirect {
            try {
                sdkApi?.removeChannel(sessionId)
            } catch (e: Throwable) {
                ALog.e(TAG, "removeChannel", e)
            }
        }
    }

    fun addChat(uid: String, result: (sessionId: String) -> Unit): Boolean {
        if (!AdHocSDK.isReady()) {
            return false
        }
        AmeDispatcher.singleScheduler.scheduleDirect {
            try {
                val sessionId = sdkApi?.addChat(uid)
                result(sessionId ?: "")
            } catch (e: Throwable) {
                ALog.e(TAG, "send", e)

                result("")
            }
        }
        return true
    }


    fun removeChat(sessionId: String) {
        if (AdHocSDK.isReady()) {
            AmeDispatcher.singleScheduler.scheduleDirect {
                sdkApi?.removeChat(sessionId)
            }
        }
    }


    fun getSessionStatus(sessionId: String): AdHocSessionStatus {
        if (AdHocSDK.isReady()) {
            val status = sdkApi?.getSessionState(sessionId)
            return when (status) {
                SessionStatus.Established.value -> AdHocSessionStatus.READY
                SessionStatus.NoneExist.value -> AdHocSessionStatus.NOT_EXIST
//                SessionStatus.Timeout.value -> AdHocSessionStatus.TIMEOUT
                SessionStatus.Closed.value -> AdHocSessionStatus.CLOSED
                else -> AdHocSessionStatus.CONNECTING
            }
        }
        return AdHocSessionStatus.CONNECTING
    }

    override fun onSessionStateChanged(sessionId: String, status: Int) {
        val sessionStatus = if (status == SessionStatus.Established.value) {
            AdHocSessionStatus.READY
        } else if (status == SessionStatus.Closed.value) {
            AdHocSessionStatus.CLOSED
        } else {
            AdHocSessionStatus.CONNECTING
        }

        AmeDispatcher.singleScheduler.scheduleDirect {
            eventListenerSet.forEach {
                it.onSessionStatusChanged(sessionId, sessionStatus)
            }
        }
    }

    override fun onReceiveMessage(sessionId: String, message: String) {
        ALog.i(TAG, "onReceiveMessage message: $message")
        try {
            val proto = GsonUtils.fromJson<MessageStore.Message>(message, MessageStore.Message::class.java)
            val content = AdHocMessagePack.contentFrom(proto.payload)
            if (null == content) {
                ALog.e(TAG, "unknown adhoc message $sessionId")
                return
            }

            when (content) {
                is AdHocMessagePack.ChatMessage -> {
                    handleChatMessage(proto, sessionId, content)
                }
                is AdHocMessagePack.ACKMessage -> {
                    handleAckMessage(sessionId, content.mid)
                }
                is AdHocMessagePack.FileChatMessage -> {
                    handleFileChatMessage(proto, sessionId, content)
                }
            }
        } catch (e: Throwable) {
            ALog.e(TAG, "onReceiveMessage", e)
        }
    }


    override fun onReceivingMessageFile(sessionId: String, mid: String, total: Long, progress: Long) {
        ALog.e(TAG, "onReceivingMessageFile session:$sessionId mid:$mid")
        AmeDispatcher.singleScheduler.scheduleDirect {
            eventListenerSet.forEach {
                it.onReceivingFile(sessionId, mid, total, progress)
            }
        }
    }

    override fun onReceiveMessageFileComplete(sessionId: String, mid: String, fileUri: Uri) {
        ALog.e(TAG, "onReceiveMessageFileComplete session:$sessionId mid:$mid")
        AmeDispatcher.singleScheduler.scheduleDirect {
            eventListenerSet.forEach {
                it.onReceiveFileComplete(sessionId, mid, fileUri)
            }
        }
    }

    override fun onReceiveMessageFileFailed(sessionId: String, mid: String, reason: Int) {
        ALog.e(TAG, "onReceiveMessageFileFailed session:$sessionId mid:$mid reason:$reason")
        AmeDispatcher.singleScheduler.scheduleDirect {
            eventListenerSet.forEach {
                it.onReceiveFileFailed(sessionId, mid, reason)
            }
        }
    }

    override fun onSendMessageFileFailed(sessionId: String, mid: String, reason: Int) {
        ALog.e(TAG, "onSendMessageFileFailed session:$sessionId mid:$mid reason:$reason")
        AmeDispatcher.singleScheduler.scheduleDirect {
            eventListenerSet.forEach {
                it.onSendFileFailed(sessionId, mid)
            }
        }
    }

    override fun onSendMessageFileComplete(sessionId: String, mid: String) {
        ALog.e(TAG, "onSendMessageFileComplete session:$sessionId mid:$mid")
        AmeDispatcher.singleScheduler.scheduleDirect {
            eventListenerSet.forEach {
                it.onSendFileComplete(sessionId, mid)
            }
        }
    }

    private fun handleFileChatMessage(proto: MessageStore.Message, sessionId: String, content: AdHocMessagePack.FileChatMessage) {
        if (content.message.isNullOrEmpty()) {
            ALog.e(TAG, "adhoc message is empty $sessionId")
            return
        }

        val adHocMessage = AdHocChatMessage(proto.mid,
                proto.senderId,
                proto.senderNickname,
                proto.sessionName,
                content.message,
                proto.receiveTime,
                proto.type == MessageStore.MessageType.ChannelChat,
                content.digest)

        AmeDispatcher.singleScheduler.scheduleDirect {
            eventListenerSet.forEach {
                it.onReceiveMessage(sessionId, false, adHocMessage)
            }
        }
    }

    private fun handleChatMessage(proto: MessageStore.Message, sessionId: String, content: AdHocMessagePack.ChatMessage) {
        if (content.message.isNullOrEmpty()) {
            ALog.e(TAG, "adhoc message is empty $sessionId")
            return
        }
        val adHocMessage = AdHocChatMessage(proto.mid,
                proto.senderId,
                proto.senderNickname,
                proto.sessionName,
                content.message,
                proto.receiveTime,
                proto.type == MessageStore.MessageType.ChannelChat,
                null)

        val atMe = (content.atList?.any { it == AdHocMessageLogic.get(accountContext).myAdHocId() } == true)

        AmeDispatcher.singleScheduler.scheduleDirect {
            eventListenerSet.forEach {
                it.onReceiveMessage(sessionId, atMe, adHocMessage)
                if (atMe) {
                    it.onAtMeNotify(sessionId)
                }
            }
        }
    }

    private fun handleAckMessage(sessionId: String, mid: String) {
        AmeDispatcher.singleScheduler.scheduleDirect {
            val sending = sendingMap.remove(mid)
            if (sending != null && sessionId == sending.sessionId) {
                if (!sending.timeExpire.isDisposed) {
                    sending.timeExpire.dispose()
                }
                sending.callback.invoke(mid, true)

                ALog.d(TAG, "message $mid ack")
            } else {
                ALog.w(TAG, "message $mid ack but no response waiting")
            }
        }
    }


    interface IAdHocSessionEventListener {
        fun onReceiveMessage(sessionId: String, atMe: Boolean, message: AdHocChatMessage) {}
        fun onSessionStatusChanged(sessionId: String, status: AdHocSessionStatus) {}
        fun onAtMeNotify(sessionId: String) {}

        fun onReceivingFile(sessionId: String, mid: String, total: Long, progress: Long) {}
        fun onReceiveFileComplete(sessionId: String, mid: String, uri: Uri) {}
        fun onReceiveFileFailed(sessionId: String, mid: String, reason: Int) {}
        fun onSendFileComplete(sessionId: String, mid: String) {}
        fun onSendFileFailed(sessionId: String, mid: String) {}

    }

    private inner class SendingChat(val sessionId: String,
                                    val timeExpire: Disposable,
                                    val callback: (mid: String, succeed: Boolean) -> Unit)


}