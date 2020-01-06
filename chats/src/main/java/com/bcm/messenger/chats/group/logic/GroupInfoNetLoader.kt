package com.bcm.messenger.chats.group.logic

import android.annotation.SuppressLint
import android.util.LongSparseArray
import com.bcm.messenger.chats.group.core.GroupManagerCore
import com.bcm.messenger.chats.group.core.group.GroupInfoEntity
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.ServerResult
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.ObservableOnSubscribe
import io.reactivex.schedulers.Schedulers

class GroupInfoNetLoader(private val accountContext: AccountContext) {
    private val loadMap = LongSparseArray<RequestState<ServerResult<GroupInfoEntity>>>()

    @SuppressLint("CheckResult")
    fun loadGroup(gid: Long): Observable<ServerResult<GroupInfoEntity>> {

        val subscriber = Subscriber<ServerResult<GroupInfoEntity>>()
        val observable = Observable.create<ServerResult<GroupInfoEntity>>(subscriber)

        Observable.create(ObservableOnSubscribe<RequestState<ServerResult<GroupInfoEntity>>> {
            val reqState = loadMap[gid]
            if (reqState != null) {
                reqState.subscriberList.add(subscriber)

                it.onNext(reqState)
                it.onComplete()
            } else {
                val reqSate = RequestState<ServerResult<GroupInfoEntity>>(false)
                reqSate.subscriberList.add(subscriber)
                loadMap.put(gid, reqSate)

                it.onNext(reqSate)
                it.onComplete()
            }

        }).subscribeOn(AmeDispatcher.singleScheduler)
                .flatMap {
                    if (!it.requesting) {
                        it.requesting = true
                        GroupManagerCore.getGroupInfo(accountContext, gid)
                                .subscribeOn(Schedulers.io())
                                .observeOn(AmeDispatcher.singleScheduler)
                    } else {
                        Observable.create { nothing ->
                            nothing.onComplete()
                        }
                    }
                }.observeOn(AmeDispatcher.singleScheduler)
                .subscribe({
                    loadGroupFinished(gid, it, null)
                }, {
                    loadGroupFinished(gid, null, it)
                })

        return observable

    }

    private fun loadGroupFinished(key: Long, data: ServerResult<GroupInfoEntity>?, exception: Throwable?) {
        val requestState = loadMap[key]
        loadMap.remove(key)
        if (requestState == null || requestState.subscriberList.isEmpty()) {
            ALog.w("GroupMemberInfoNetLoader", "error loader state")
            return
        }

        for (i in requestState.subscriberList) {
            if (null != data) {
                i.doNext(data)
            } else if (null != exception) {
                i.doError(exception)
            }
            i.doComplete()
        }
    }

    class Subscriber<T> : ObservableOnSubscribe<T> {
        private var emitter: ObservableEmitter<T>? = null
        override fun subscribe(emitter: ObservableEmitter<T>) {
            this.emitter = emitter
        }

        fun doNext(data: T) {
            emitter?.onNext(data)
        }

        fun doComplete() {
            emitter?.onComplete()
        }

        fun doError(exception: Throwable) {
            emitter?.onError(exception)
        }
    }

    data class RequestState<T>(var requesting: Boolean, var subscriberList: MutableSet<Subscriber<T>> = mutableSetOf())

}