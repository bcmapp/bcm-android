package com.bcm.messenger.chats.group.logic

import android.annotation.SuppressLint
import com.bcm.messenger.chats.group.core.GroupMemberCore
import com.bcm.messenger.chats.group.core.group.GroupMemberEntity
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.utils.log.ACLog
import com.bcm.messenger.common.utils.md5
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.ObservableOnSubscribe
import io.reactivex.schedulers.Schedulers

class GroupMemberInfoNetLoader(private val accountContext: AccountContext) {
    private val loadMap = HashMap<String, RequestState<GroupMemberEntity>>()
    private val listLoadMap = HashMap<String, RequestState<List<GroupMemberEntity>>>()

    @SuppressLint("CheckResult")
    fun loadMember(gid: Long, uid: String): Observable<GroupMemberEntity> {
        val key = "$gid$uid"

        val subscriber = Subscriber<GroupMemberEntity>()
        val observable = Observable.create<GroupMemberEntity>(subscriber)

        Observable.create(ObservableOnSubscribe<RequestState<GroupMemberEntity>> {
            val reqState = loadMap[key]
            if (reqState != null) {
                reqState.subscriberList.add(subscriber)

                it.onNext(reqState)
                it.onComplete()
            } else {
                val reqSate = RequestState<GroupMemberEntity>(false)
                reqSate.subscriberList.add(subscriber)
                loadMap[key] = reqSate

                it.onNext(reqSate)
                it.onComplete()
            }

        }).subscribeOn(AmeDispatcher.singleScheduler)
                .flatMap {
                    if (!it.requesting) {
                        it.requesting = true
                        GroupMemberCore.getGroupMemberInfo(accountContext, gid, uid)
                                .subscribeOn(Schedulers.io())
                                .observeOn(AmeDispatcher.singleScheduler)
                    } else {
                        Observable.create { nothing ->
                            nothing.onComplete()
                        }
                    }
                }.observeOn(AmeDispatcher.singleScheduler)
                .subscribe({
                    loadMemberFinish(key, it, null)
                }, {
                    loadMemberFinish(key, null, it)
                })

        return observable

    }


    @SuppressLint("CheckResult")
    fun loadMembers(gid: Long, uids: List<String>): Observable<List<GroupMemberEntity>> {
        val key = "$gid${GsonUtils.toJson(uids).md5()}"

        val subscriber = Subscriber<List<GroupMemberEntity>>()
        val observable = Observable.create<List<GroupMemberEntity>>(subscriber)

        Observable.create(ObservableOnSubscribe<RequestState<List<GroupMemberEntity>>> {
            val reqState = listLoadMap[key]
            if (reqState != null) {
                reqState.subscriberList.add(subscriber)

                it.onNext(reqState)
                it.onComplete()
            } else {
                val reqSate = RequestState<List<GroupMemberEntity>>(false)
                reqSate.subscriberList.add(subscriber)
                listLoadMap[key] = reqSate

                it.onNext(reqSate)
                it.onComplete()
            }

        }).subscribeOn(AmeDispatcher.singleScheduler)
                .flatMap {
                    if (!it.requesting) {
                        it.requesting = true
                        GroupMemberCore.getGroupMembers(accountContext, gid, uids)
                                .subscribeOn(Schedulers.io())
                                .observeOn(AmeDispatcher.singleScheduler)
                    } else {
                        Observable.create { nothing ->
                            nothing.onComplete()
                        }
                    }
                }.observeOn(AmeDispatcher.singleScheduler)
                .subscribe({
                    loadListFinish(key, it, null)
                }, {
                    loadListFinish(key, null, it)
                })

        return observable

    }

    private fun loadMemberFinish(key: String, data: GroupMemberEntity?, exception: Throwable?) {
        val requestState = loadMap[key]
        loadMap.remove(key)
        if (requestState == null || requestState.subscriberList.isEmpty()) {
            ACLog.w(accountContext, "GroupMemberInfoNetLoader", "error loader state")
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

    private fun loadListFinish(key: String, data: List<GroupMemberEntity>?, exception: Throwable?) {
        val requestState = listLoadMap[key]
        listLoadMap.remove(key)
        if (requestState == null || requestState.subscriberList.isEmpty()) {
            ACLog.w(accountContext, "GroupMemberInfoNetLoader", "error loader state")
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