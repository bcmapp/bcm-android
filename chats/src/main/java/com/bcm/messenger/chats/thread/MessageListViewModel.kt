package com.bcm.messenger.chats.thread

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.database.records.ThreadRecord
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.provider.IContactModule
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.api.BcmRouter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Created by Kin on 2019/9/17
 */
class MessageListViewModel : ViewModel() {

    class ThreadListData(val data: List<ThreadRecord>, val unread: Int)

    private val TAG = "MessageListViewModel"

    private val threadRepo = Repository.getThreadRepo()
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
            val provider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_CONTACTS_BASE).navigationWithCast<IContactModule>()
            val tl = threadRepo.getAllThreadsWithRecipientReady()
            provider.updateThreadRecipientSource(tl.mapNotNull { record ->
                val r = record.getRecipient()
                if (r.isGroupRecipient) {
                    null
                }else {
                    r
                }
            })
            var unreadCount = 0
            tl.forEach {r ->
                ALog.d(TAG, "uid: ${r.uid}, mute: ${r.getRecipient().mutedUntil}, unread: ${r.unreadCount}")
                if (!r.getRecipient().isMuted && r.unreadCount > 0) {
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

    override fun onCleared() {
        ALog.i(TAG, "onCleared")
        localLiveData?.removeObserver(threadObserver)
        localLiveData = null
        mNewListRef.set(listOf())
        super.onCleared()
    }

}