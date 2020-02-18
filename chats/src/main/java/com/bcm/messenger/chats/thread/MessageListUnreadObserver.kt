package com.bcm.messenger.chats.thread

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.database.records.ThreadRecord
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.database.repositories.ThreadRepo
import com.bcm.messenger.common.event.AccountLoginStateChangedEvent
import com.bcm.messenger.common.event.FriendRequestEvent
import com.bcm.messenger.common.event.HomeTabEvent
import com.bcm.messenger.common.event.HomeTabEvent.Companion.TAB_ADHOC
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.utils.RxBus
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.concurrent.TimeUnit

/**
 * Created by Kin on 2020/1/10
 */
class MessageListUnreadObserver {
    private val TAG = "MessageListUnreadObserver"

    class UnreadCountEntity(var chatUnread: Int = 0,
                            var friendUnread: Int = 0,
                            var friendUnhandle: Int = 0,
                            var adHocUnread: Int = 0)

    private abstract class UnreadObserver<T>(private val accountContext: AccountContext) : Observer<T>

    interface UnreadCountChangeListener {
        fun onChatUnreadCountChanged(accountContext: AccountContext, unreadCount: Int)
        fun onFriendUnreadCountChanged(accountContext: AccountContext, unreadCount: Int, unhandledCount: Int)
        fun onAdHocUnreadCountChanged(accountContext: AccountContext, unreadCount: Int)
        fun onAccountListChanged()
    }

    private var loginAccounts = listOf<AccountContext>()
    private val observers = mutableMapOf<AccountContext, Pair<LiveData<List<ThreadRecord>>, UnreadObserver<List<ThreadRecord>>>>()

    private var listener: UnreadCountChangeListener? = null

    fun init() {
        EventBus.getDefault().register(this)
        reobserve()
        observeOthers()
    }

    fun unInit() {
        this.listener = null
        EventBus.getDefault().unregister(this)
        RxBus.unSubscribe(TAG)
    }

    fun onResume() {
        checkUnhandledRequest()
    }

    fun setListener(listener: UnreadCountChangeListener) {
        this.listener = listener
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: AccountLoginStateChangedEvent) {
        reobserve()
    }

    private fun reobserve() {
        observers.values.forEach {
            it.first.removeObserver(it.second)
        }
        observers.clear()
        listener?.onAccountListChanged()

        loginAccounts = AmeLoginLogic.accountHistory.getAllLoginContext()
        loginAccounts.forEach {
            val threadRepo = Repository.getThreadRepo(it)
            threadRepo?.getAllThreadsLiveData()?.also { liveData ->
                val observer = getObserver(it, threadRepo)
                liveData.observeForever(observer)
                observers[it] = Pair(liveData, observer)
            }
        }
        checkUnhandledRequest()
    }

    private fun observeOthers() {
        RxBus.subscribe<HomeTabEvent>(TAG) {
            ALog.i(TAG, "receive HomeTabEvent position: ${it.position}, figure: ${it.showFigure}")
            val adhocMode = AmeModuleCenter.adhoc().isAdHocMode()
            when (it.position) {
                TAB_ADHOC -> {
                    if ((it.showFigure != null || it.showDot != null) && adhocMode) {
                        listener?.onAdHocUnreadCountChanged(it.accountContext, it.showFigure ?: 0)
                    }
                }
            }
        }

        RxBus.subscribe<FriendRequestEvent>(TAG) {
            checkUnhandledRequest(it.unreadCount)
        }
    }

    private fun checkUnhandledRequest(unreadCount: Int = 0) {
        loginAccounts.forEach {
            Observable.create<Pair<Int, Int>> { emitter ->
                val requestDao = Repository.getFriendRequestRepo(it)
                var unread = unreadCount
                if (unread == 0) {
                    unread = requestDao?.queryUnreadCount() ?: 0
                }
                emitter.onNext(Pair(requestDao?.queryUnhandledCount() ?: 0, unread))
                emitter.onComplete()
            }.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { res ->
                        ALog.i(TAG, "checkUnHandledFriendRequest, unHandled: ${res.first}, unread: ${res.second}")
                        listener?.onFriendUnreadCountChanged(it, res.second, res.first)
                    }
        }
    }

    private fun getObserver(accountContext: AccountContext, threadRepo: ThreadRepo): UnreadObserver<List<ThreadRecord>> {
        return object : UnreadObserver<List<ThreadRecord>>(accountContext) {
            private var threadDisposable: Disposable? = null

            override fun onChanged(t: List<ThreadRecord>?) {
                if (threadDisposable?.isDisposed == false) {
                    ALog.i(TAG, "has thread update running")
                    return
                }
                threadDisposable = Observable.create<Int> {
                    ALog.d(TAG, "threadDisposable run")
                    val tl = threadRepo.getAllThreadsWithRecipientReady()
                    var unreadCount = 0
                    tl.forEach { r ->
                        ALog.d(TAG, "uid: ${r.uid}, unread: ${r.unreadCount}")
                        if (!r.getRecipient(accountContext).isMuted && r.unreadCount > 0) {
                            unreadCount += r.unreadCount
                        }
                    }
                    it.onNext(unreadCount)
                    it.onComplete()
                }.delaySubscription(300L, TimeUnit.MILLISECONDS, AmeDispatcher.ioScheduler)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            ALog.i(TAG, "threadDisposable finish")
                            threadDisposable = null
                            listener?.onChatUnreadCountChanged(accountContext, it)
                        }, {
                            ALog.e(TAG, "threadObserve error", it)
                            threadDisposable = null
                            listener?.onChatUnreadCountChanged(accountContext, 0)
                        })
            }
        }
    }
}