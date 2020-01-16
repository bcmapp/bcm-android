package com.bcm.messenger.chats.group.logic.secure

import android.annotation.SuppressLint
import com.bcm.messenger.chats.group.core.GroupManagerCore
import com.bcm.messenger.chats.group.core.group.RefreshKeyResEntity
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.utils.log.ACLog
import com.bcm.messenger.common.utils.split
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import java.util.concurrent.TimeUnit

class GroupKeyRotate(private val accountContext: AccountContext) {
    companion object {
        private const val TAG = "GroupKeyRotate"
    }

    @SuppressLint("UseSparseArrays")
    private val rotatingMap = HashMap<Long, Long>()

    fun initRotate() {
        AmeDispatcher.singleScheduler.scheduleDirect {
            rotatingMap.clear()
        }
    }

    fun clearRotate() {
        AmeDispatcher.singleScheduler.scheduleDirect {
            rotatingMap.clear()
        }
    }

    fun rotateGroup(gidList: List<Long>) {
        ACLog.i(accountContext,  TAG, "rotateGroup list invoke")

        AmeDispatcher.singleScheduler.scheduleDirect {
            val syncList = gidList.filter { !rotatingMap.containsKey(it) }
            val syncingLit = gidList.filter { rotatingMap.containsKey(it) }

            if (syncingLit.isNotEmpty()) {
                ACLog.i(accountContext,  TAG, "key rotating ${syncingLit.size}")
            }

            if (syncList.isEmpty()) {
                ACLog.i(accountContext,  TAG, "all group key rotating ${syncingLit.size}")
                return@scheduleDirect
            }

            for (i in syncList) {
                rotatingMap[i] = 0
            }

            if (accountContext.isLogin) {
                rotateGroupImpl(syncList, 0)
            }
        }
    }

    fun rotateGroup(gid: Long): Observable<RefreshKeyResEntity> {
        ACLog.i(accountContext,  TAG, "rotateGroup invoke")
        return Observable.create<RefreshKeyResEntity> {
            val result = RefreshKeyResEntity()
            result.succeed = listOf(gid)
            if (rotatingMap.containsKey(gid)) {
                it.onNext(result)
            } else {
                rotatingMap[gid] = 0
                rotateGroupImpl(listOf(gid), 0)
                it.onNext(result)
            }
            it.onComplete()
        }.subscribeOn(AmeDispatcher.singleScheduler)
    }

    @SuppressLint("UseSparseArrays", "CheckResult")
    private fun rotateGroupImpl(gidList: List<Long>, delay: Long) {
        ACLog.i(accountContext,  TAG, "rotateGroupImpl invoke")

        val syncList = gidList.toMutableList()
        // No more than 10 keys can be refreshed
        val refreshArray = syncList.split(10).map {
            Observable.just(it)
                    .subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AmeDispatcher.ioScheduler)
                    .flatMap { list ->
                        GroupManagerCore.refreshGroupKeys(accountContext, list)
                                .subscribeOn(AmeDispatcher.ioScheduler)
                    }
                    .observeOn(AmeDispatcher.ioScheduler)
        }

        val checkComplete: (syncList: List<Long>) -> Unit = {
            if (syncList.isNotEmpty()) {
                if (delay > 15000) {
                    ACLog.e(accountContext,  TAG, "rotateGroup refresh group key failed ${syncList.size}")
                    for (i in syncList) {
                        rotatingMap.remove(i)
                    }
                } else {
                    ACLog.e(accountContext,  TAG, "rotateGroup refresh group key retry ${syncList.size}, delay:${delay + 3000}")
                    rotateGroupImpl(syncList, delay + 3000)
                }
            } else {
                ACLog.i(accountContext,  TAG, "rotateGroup refresh group key all succeed")
            }
        }

        Observable.concat(refreshArray)
                .delaySubscription(delay, TimeUnit.MILLISECONDS, AmeDispatcher.ioScheduler)
                .subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AmeDispatcher.singleScheduler)
                .doOnError {
                    ACLog.e(accountContext,  TAG, "rotateGroup refresh group key failed", it)
                    if (accountContext.isLogin) {
                        checkComplete(syncList)
                    }

                }
                .doOnComplete {
                    if (accountContext.isLogin) {
                        checkComplete(syncList)
                    }
                }
                .subscribe {
                    if (!accountContext.isLogin) {
                        return@subscribe
                    }

                    ACLog.i(accountContext,  TAG, "rotateGroup refresh group key succeed ${it.succeed?.size}, failed:${it.failed?.size}")

                    syncList.removeAll(it.succeed ?: listOf())

                    val waitMap = HashMap<Long, Long>()
                    if (it.succeed?.isNotEmpty() == true) {
                        for (i in it.succeed!!) {
                            if (rotatingMap.containsKey(i)) {
                                val t = System.currentTimeMillis()
                                rotatingMap[i] = t
                                waitMap[i] = t
                            }
                        }
                    }
                    waitForRotateFinishedCall(waitMap)
                }
    }

    @SuppressLint("CheckResult")
    private fun waitForRotateFinishedCall(rotateMap: HashMap<Long, Long>) {
        if (rotateMap.isEmpty()) {
            return
        }
        ACLog.i(accountContext,  TAG, "waitForRotateFinishedCall invoke")

        Observable.just(rotateMap)
                .delaySubscription(15, TimeUnit.SECONDS, AmeDispatcher.ioScheduler)
                .subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AmeDispatcher.singleScheduler)
                .doOnError {
                    ACLog.e(accountContext,  TAG, "waitForRotateFinishedCall", it)
                }
                .subscribe {
                    if (!accountContext.isLogin) {
                        return@subscribe
                    }

                    val failedRotates = rotateMap.filter { rotatingMap[it.key] == it.value }.keys.toList()
                    if (failedRotates.isNotEmpty()) {
                        for (i in failedRotates) {
                            rotatingMap.remove(i)
                        }

                        rotateGroup(failedRotates)
                    }

                    ACLog.i(accountContext,  TAG, "waitForRotateFinishedCall failed size:${failedRotates.size}")
                }
    }

    fun rotateFinished(gid: Long) {
        ACLog.i(accountContext,  TAG, "rotateFinished invoke")
        AmeDispatcher.singleScheduler.scheduleDirect {
            rotatingMap.remove(gid)
        }
    }
}