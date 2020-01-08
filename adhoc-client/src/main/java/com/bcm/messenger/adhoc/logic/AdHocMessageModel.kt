package com.bcm.messenger.adhoc.logic

import androidx.lifecycle.ViewModel
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 * adhoc message model
 * Created by wjh on 2019/7/27
 */
class AdHocMessageModel(private val accountContext: AccountContext) : ViewModel() {

    interface OnModelListener {
        fun onRecycle()
    }

    interface OnMessageListener{
        fun onClearHistory()
        fun onAddMessage(messageList: List<AdHocMessageDetail>)
        fun onUpdateMessage(messageList: List<AdHocMessageDetail>)
        fun onDeleteMessage(messageList: List<AdHocMessageDetail>)
        fun onAddAt(atId: String, atNick: String)
        fun onForward(source: String, text: String)
        fun onProgress(message: AdHocMessageDetail, progress: Float)
    }

    open class DefaultOnMessageListener(val tag: String = System.currentTimeMillis().toString()) : OnMessageListener {

        override fun onClearHistory() {
        }

        override fun onAddMessage(messageList: List<AdHocMessageDetail>) {
        }

        override fun onUpdateMessage(messageList: List<AdHocMessageDetail>) {
        }

        override fun onDeleteMessage(messageList: List<AdHocMessageDetail>) {
        }

        override fun onAddAt(atId: String, atNick: String) {
        }

        override fun onForward(source: String, text: String) {
        }

        override fun onProgress(message: AdHocMessageDetail, progress: Float) {
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as DefaultOnMessageListener

            if (tag != other.tag) return false

            return true
        }

        override fun hashCode(): Int {
            return tag.hashCode()
        }

    }

    companion object {
        private const val TAG = "AdHocMessageModel"
    }

    private var mSessionId: String = ""
    private var mOnModelListener: OnModelListener? = null
    private var mOnMessageListenerSet: MutableSet<OnMessageListener>? = null
    private var mCache: AdHocMessageCache? = null

    override fun onCleared() {
        super.onCleared()

        mOnModelListener?.onRecycle()
        mOnModelListener = null
        mOnMessageListenerSet?.clear()
        mOnMessageListenerSet = null
    }

    internal fun init(cache: AdHocMessageCache, sessionId: String, onModelListener: OnModelListener) {
        mCache = cache
        mSessionId = sessionId
        mOnModelListener = onModelListener
    }

    fun getSessionId(): String {
        return mSessionId
    }

    fun addOnMessageListener(listener: OnMessageListener) {
        if (mOnMessageListenerSet == null) {
            mOnMessageListenerSet = mutableSetOf()
        }
        mOnMessageListenerSet?.add(listener)
    }

    fun removeOnMessageListener(listener: OnMessageListener) {
        mOnMessageListenerSet?.remove(listener)
    }


    fun addAt(atId: String, atNick: String) {
        mOnMessageListenerSet?.forEach {
            it.onAddAt(atId, atNick)
        }
    }


    fun forward(source: String, text: String) {
        mOnMessageListenerSet?.forEach {
            it.onForward(source, text)
        }
    }

    fun forward(message: AdHocMessageDetail) {

        if (!message.sendByMe) { //if transfer attachment message，need uri set to url
            val content = message.getMessageBody()?.content
            if (content is AmeGroupMessage.AttachmentContent) {
                content.url = message.attachmentUri ?: ""
                message.setMessageBody(AmeGroupMessage(message.getMessageBodyType(), content))
            }
        }
        forward(message.fromId, message.getMessageBodyJson())

    }


    fun findMessage(index: Long, callback: (result: AdHocMessageDetail?) -> Unit) {
        Observable.create<AdHocMessageDetail> {
            val cache = mCache
            if (cache == null) {
                it.onError(Exception("AdHocMessageCache is null"))
            }else {
                val result = cache.getMessageDetail(mSessionId, index)
                if (result == null) {
                    it.onError(Exception("lastSeen is null"))
                }else {
                    it.onNext(result)
                }
            }
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback.invoke(it)
                }, {
                    ALog.e(TAG, "readMessage fail", it)
                    callback.invoke(null)
                })
    }


    fun findLastSeen(callback: (result: AdHocMessageDetail?) -> Unit) {
        Observable.create<AdHocMessageDetail> {
            val cache = mCache
            if (cache == null) {
                it.onError(Exception("AdHocMessageCache is null"))
            }else {
                val result = cache.findLastSeen(mSessionId)
                if (result == null) {
                    it.onError(Exception("lastSeen is null"))
                }else {
                    it.onNext(result)
                }
            }
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback.invoke(it)
                }, {
                    ALog.e(TAG, "readMessage fail", it)
                    callback.invoke(null)
                })
    }


    fun deleteMessage(messageList: List<AdHocMessageDetail>, callback: (result: Boolean) -> Unit) {
        Observable.create<Boolean> {
            val cache = mCache
            if (cache == null) {
                it.onError(Exception("AdHocMessageCache is null"))
            }else {
                cache.deleteMessage(mSessionId, messageList)
                it.onNext(true)
            }
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback.invoke(it)
                    if (it) {
                        mOnMessageListenerSet?.forEach {
                            it.onDeleteMessage(messageList)
                        }
                    }
                }, {
                    ALog.e(TAG, "readMessage fail", it)
                    callback.invoke(false)
                })
    }


    fun readMessage(messageList: List<AdHocMessageDetail>, callback: (result: Boolean) -> Unit) {
        Observable.create<Boolean> {
            val cache = mCache
            if (cache == null) {
                it.onError(Exception("AdHocMessageCache is null"))
            }else {
                cache.readMessage(messageList)
                it.onNext(true)
            }
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback.invoke(it)
                    if (it) {
                        mOnMessageListenerSet?.forEach {
                            it.onUpdateMessage(messageList)
                        }
                    }
                }, {
                    ALog.e(TAG, "readMessage fail", it)
                    callback.invoke(false)
                })
    }


    fun fetchMessage(fromIndex: Long, count: Int, callback: (result: List<AdHocMessageDetail>) -> Unit) {
        Observable.create<List<AdHocMessageDetail>> {
            val cache = mCache
            if (cache == null) {
                it.onError(Exception("AdHocMessageCache is null"))
            }else {
                it.onNext(cache.fetchMessageList(mSessionId, fromIndex, count))
            }
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback.invoke(it)
                }, {
                    ALog.e(TAG, "fetchMessage fail", it)
                    callback.invoke(listOf())
                })
    }


    fun fetchMessageWithUnRead(fromIndex: Long, count: Int, callback: (result: List<AdHocMessageDetail>, unread: Int) -> Unit) {
        Observable.create<Pair<List<AdHocMessageDetail>, Int>> {
            val cache = mCache
            if (cache == null) {
                it.onError(Exception("AdHocMessageCache is null"))
            }else {
                val list = cache.fetchMessageList(mSessionId, fromIndex, count)
                val unread = cache.findUnreadCount(mSessionId)
                it.onNext(Pair(list, unread))
            }
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback.invoke(it.first, it.second)
                }, {
                    ALog.e(TAG, "fetchMessage fail", it)
                    callback.invoke(listOf(), 0)
                })
    }


    fun clearHistory(callback: (result: Boolean) -> Unit) {
        Observable.create<Boolean> {
            val cache = mCache
            if (cache == null) {
                it.onError(Exception("AdHocMessageCache is null"))
            }else {
                cache.clearHistory(mSessionId)
                it.onNext(true)
            }
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback.invoke(it)
                    if (it) {
                        mOnMessageListenerSet?.forEach {
                            it.onClearHistory()
                        }
                    }
                }, {
                    ALog.e(TAG, "clearAllMessage fail", it)
                    callback.invoke(false)
                })
    }


    fun readAll(callback: (result: Boolean) -> Unit) {
        Observable.create<Boolean> {
            val cache = mCache
            if (cache == null) {
                it.onError(Exception("AdHocMessageCache is null"))
            }else {
                cache.readAll(mSessionId)
                it.onNext(true)
            }
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback.invoke(it)
                }, {
                    ALog.e(TAG, "clearAllMessage fail", it)
                    callback.invoke(false)
                })
    }


    fun leaveChannel(callback: (result: Boolean) -> Unit) {
        AdHocSessionLogic.get(accountContext).deleteSession(mSessionId)
        val session = AdHocSessionLogic.get(accountContext).getSession(mSessionId)
        if (session == null) {
            callback.invoke(false)
            return
        }
        if (session.cid.isNotEmpty()) {
            AdHocChannelLogic.get(accountContext).removeChannel(session.sessionId, session.cid)
        } else {
            AdHocChannelLogic.get(accountContext).removeChat(session.sessionId)
        }
        callback.invoke(true)
    }


    fun saveDraft(draft: CharSequence, callback: (result: Boolean) -> Unit) {
        AdHocSessionLogic.get(accountContext).updateDraft(mSessionId, draft.toString())
        callback.invoke(true)
    }

    fun onReceiveMessage(messageList: List<AdHocMessageDetail>) {
        mOnMessageListenerSet?.forEach {
            it.onAddMessage(messageList)
        }
    }

    fun onUpdateMessage(messageList: List<AdHocMessageDetail>) {
        mOnMessageListenerSet?.forEach {
            it.onUpdateMessage(messageList)
        }
    }

    fun onAttachmentProgress(message: AdHocMessageDetail, progress: Float) {
        mOnMessageListenerSet?.forEach {
            it.onProgress(message, progress)
        }
    }
}