package com.bcm.messenger.chats.thread

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.database.records.ThreadRecord
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Created by Kin on 2019/9/17
 */
class ThreadListViewModel(
        private var mAccountContext: AccountContext
) : ViewModel() {

    class ThreadListData(val data: List<ThreadRecord>, val unread: Int)

    companion object {
        private const val TAG = "ThreadListViewModel"

        /**
         * 缓存
         */
        private val mCache: WeakHashMap<Recipient, Long> = WeakHashMap()

        private fun addCache(key: Recipient, value: Long) {
            ALog.d(TAG, "addCache key: $key value: $value")
            mCache[key] = value
        }

        private fun removeCache(key: Recipient) {
            ALog.d(TAG, "removeCache key: $key")
            mCache.remove(key)
        }

        private fun getCache(key: Recipient): Long? {
            return mCache[key]
        }

        fun clearCache() {
            ALog.d(TAG, "clearCache")
            mCache.clear()
        }

        /**
         * 读取会话ID，如果没有则生成
         */
        fun getThreadId(recipient: Recipient, callback: (threadId: Long) -> Unit) {
            Observable.create(ObservableOnSubscribe<Long> {
                try {
                    it.onNext(Repository.getThreadRepo(recipient.address.context())?.getThreadIdFor(recipient.address.serialize())
                            ?: 0L)
                } finally {
                    it.onComplete()
                }
            }).subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ threadId ->
                        callback.invoke(threadId)
                    }, {
                        ALog.e(TAG, "checkPin error", it)
                        callback.invoke(0)
                    })
        }

        /**
         * 读取现有的会话ID
         */
        fun getExistThreadId(accountContext: AccountContext, recipient: Recipient, callback: (threadId: Long) -> Unit) {

            Observable.create(ObservableOnSubscribe<Long> {
                try {
                    it.onNext(Repository.getThreadRepo(accountContext)?.getThreadIdIfExist(recipient)
                            ?: 0L)
                } catch (ex: Exception) {
                    it.onError(ex)
                } finally {
                    it.onComplete()
                }
            }).subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        ALog.d(TAG, "getExistThreadId: $it")
                        callback.invoke(it)
                    }, {
                        ALog.e(TAG, "getThreadId error", it)
                        callback.invoke(0L)
                    })

        }

    }

    private val TAG = "ThreadListViewModel"

    private var threadRepo = Repository.getThreadRepo(mAccountContext)!!
    private var localLiveData: LiveData<List<ThreadRecord>>? = null

    val threadLiveData = MutableLiveData<ThreadListData>()

    private var threadDisposable: Disposable? = null

    private var mNewListRef = AtomicReference<List<ThreadRecord>>()

    private val threadObserver = Observer<List<ThreadRecord>> { threadList ->
        ALog.i(TAG, "receive threadObserver")
        mNewListRef.set(threadList)
        if (threadDisposable?.isDisposed == false) {
            ALog.i(TAG, "has thread update running")
            return@Observer
        }
        threadDisposable = Observable.create<ThreadListData> {
            ALog.d(TAG, "threadDisposable run")
            val tl = threadRepo.getAllThreadsWithRecipientReady()
            AmeModuleCenter.contact(mAccountContext)?.updateThreadRecipientSource(tl.mapNotNull { record ->
                val r = record.getRecipient(mAccountContext)
                if (r.isGroupRecipient) {
                    null
                } else {
                    r
                }
            })
            var unreadCount = 0
            tl.forEach { r ->
                //ALog.d(TAG, "uid: ${r.uid}, mute: ${r.getRecipient().mutedUntil}, unread: ${r.unreadCount}")
                if (!r.getRecipient(mAccountContext).isMuted && r.unreadCount > 0) {
                    unreadCount++
                }
            }
            it.onNext(ThreadListData(tl, unreadCount))
            it.onComplete()

        }.delaySubscription(300L, TimeUnit.MILLISECONDS, AmeDispatcher.ioScheduler)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    ALog.i(TAG, "threadDisposable finish")
                    threadDisposable = null
                    threadLiveData.postValue(it)
                }, {
                    ALog.e(TAG, "threadObserve error", it)
                    threadDisposable = null
                    threadLiveData.postValue(ThreadListData(mNewListRef.get(), 0))
                })
    }

    init {
        if (localLiveData == null) {
            localLiveData = threadRepo.getAllThreadsLiveData()
            localLiveData?.observeForever(threadObserver)
        }
    }

    fun updateAccountContext(accountContext: AccountContext) {
        this.mAccountContext = accountContext
        threadRepo = Repository.getThreadRepo(mAccountContext)!!
        localLiveData?.removeObserver(threadObserver)
        localLiveData = threadRepo.getAllThreadsLiveData()
        localLiveData?.observeForever(threadObserver)
    }

    override fun onCleared() {
        ALog.i(TAG, "onCleared")
        localLiveData?.removeObserver(threadObserver)
        localLiveData = null
        mNewListRef.set(listOf())
        super.onCleared()
    }

    /**
     * 设置置顶
     */
    fun setPin(threadId: Long, toPin: Boolean, callback: (success: Boolean) -> Unit) {
        Observable.create(ObservableOnSubscribe<Boolean> {
            try {
                if (threadId <= 0L) {
                    it.onNext(false)
                } else {
                    if (toPin) {
                        threadRepo.setPinTime(threadId)
                    } else {
                        threadRepo.removePinTime(threadId)
                    }
                    it.onNext(true)
                }
            } finally {
                it.onComplete()
            }
        }).subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ success ->
                    callback.invoke(success)
                }, {
                    ALog.e(TAG, "setPin error", it)
                    callback.invoke(false)
                })
    }


    /**
     * 删除会话
     */
    fun deleteConversation(recipient: Recipient?, threadId: Long, callback: ((success: Boolean) -> Unit)? = null) {
        Observable.create(ObservableOnSubscribe<Boolean> {
            try {
                val actualThreadId = if (threadId <= 0L) {
                    threadRepo.getThreadIdIfExist(recipient?.address?.serialize().orEmpty())
                } else {
                    threadId
                }
                if (recipient?.isGroupRecipient == true) {
                    threadRepo.cleanConversationContentForGroup(actualThreadId, recipient.groupId)
                } else {
                    threadRepo.deleteConversationContent(actualThreadId)
                }
                it.onNext(true)
            } catch (ex: Exception) {
                it.onError(ex)
            } finally {
                it.onComplete()
            }
        }).subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback?.invoke(true)
                }, {
                    ALog.e(TAG, "deleteConversation error", it)
                    callback?.invoke(false)
                })
    }

    fun setUnreadOrRead(threadId: Long, isUnread: Boolean) {
        Observable.create<Unit> {
            if (isUnread) {
                threadRepo.increaseUnreadCount(threadId, 1)
            } else {
                threadRepo.setRead(threadId, true)
            }
            it.onComplete()
        }.subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                }, {
                    ALog.e(TAG, "setUnreadOrRead error", it)
                })
    }
}