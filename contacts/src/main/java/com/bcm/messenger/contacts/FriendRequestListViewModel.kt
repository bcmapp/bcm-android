package com.bcm.messenger.contacts

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.database.db.UserDatabase
import com.bcm.messenger.common.event.FriendRequestEvent
import com.bcm.messenger.common.grouprepository.room.entity.BcmFriendRequest
import com.bcm.messenger.common.utils.AmePushProcess
import com.bcm.messenger.common.utils.RxBus
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 * Created by Kin on 2019/5/17
 */
const val HANDLED_REQ = "__handled_req"
private const val REQ_CHANGE = "__req_change"

class FriendRequestsListViewModel : ViewModel() {

    private val TAG = "FriendRequestsListViewModel"

    private lateinit var mAccountContext: AccountContext

    val listLiveData = MutableLiveData<List<BcmFriendRequest>>()

    init {
        ALog.i(TAG, "Init subscribe $HANDLED_REQ")
        RxBus.subscribe<Pair<Long, Boolean>>(HANDLED_REQ) {
            ALog.i(TAG, "Receive handle result, id = ${it.first}, approved = ${it.second}")
            val list = listLiveData.value ?: return@subscribe
            val newList = list.toMutableList()
            for (item in newList) {
                if (item.id == it.first) {
                    markHandle(item, it.second)
                    newList.remove(item)
                    break
                }
            }
            listLiveData.postValue(newList)
        }

        RxBus.subscribe<FriendRequestEvent>(REQ_CHANGE) {
            if (it.unreadCount > 0) {
                queryData()
            }
        }
    }

    fun setAccountContext(context: AccountContext) {
        mAccountContext = context
    }

    override fun onCleared() {
        ALog.i(TAG, "On cleared, unsubscribe $HANDLED_REQ")
        RxBus.unSubscribe(HANDLED_REQ)
        RxBus.unSubscribe(REQ_CHANGE)
        super.onCleared()
    }

    fun queryData() {
        AmePushProcess.clearFriendRequestNotification()
        Observable.create<List<BcmFriendRequest>> {
            it.onNext(UserDatabase.getDatabase(mAccountContext).friendRequestDao().queryAll())
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    ALog.i(TAG, "Get request list, size = ${it.size}")
                    listLiveData.postValue(it)
                    markRead(it)
                }, {
                    it.printStackTrace()
                })
    }

    private fun markRead(list: List<BcmFriendRequest>) {
        Observable.create<Unit> {
            val modifyList = mutableListOf<BcmFriendRequest>()
            list.forEach { item ->
                if (item.isUnread()) {
                    val newItem = item.copy()
                    newItem.setRead()
                    modifyList.add(newItem)
                }
            }
            ALog.i(TAG, "Mark read, size = ${modifyList.size}")
            UserDatabase.getDatabase(mAccountContext).friendRequestDao().update(modifyList)
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .subscribe({

                }, {
                    it.printStackTrace()
                })
    }

    private fun markHandle(request: BcmFriendRequest, approved: Boolean) {
        Observable.create<Unit> {
            if (approved) {
                request.accept()
            } else {
                request.reject()
            }
            request.setRead()
            UserDatabase.getDatabase(mAccountContext).friendRequestDao().update(request)
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .subscribe({

                }, {
                    it.printStackTrace()
                })
    }
}