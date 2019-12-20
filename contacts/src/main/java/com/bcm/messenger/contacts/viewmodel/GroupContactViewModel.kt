package com.bcm.messenger.contacts.viewmodel

import android.annotation.SuppressLint
import android.text.TextUtils
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.core.corebean.AmeGroupInfo
import com.bcm.messenger.common.event.GroupListChangedEvent
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.IGroupModule
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.Recipient.LETTERS
import com.bcm.messenger.utility.StringAppearanceUtil
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import kotlin.collections.ArrayList

/**
 * bcm.social.01 2018/11/27.
 */

/**
 * groupContactReady
 * list:
 */
class GroupContactViewModel(private val groupContactReady: (self: GroupContactViewModel, list: ArrayList<GroupContactViewData>) -> Unit) {

     companion object {
         const val TAG = "GroupContactViewModel"
         val SEARCH_BAR = GroupContactViewData('0', AmeGroupInfo(-1))
         val GROUP_EMPTY = GroupContactViewData('0', AmeGroupInfo(-3))
         val ANY_CHAR = Recipient.UNKNOWN_LETTER[0]
     }

    private var groupInfoList = ArrayList<GroupContactViewData>()

    private var mTrueDataSize: Int = 0

    init {
        EventBus.getDefault().register(this)
    }

    fun destroy(){
        groupInfoList.clear()
        EventBus.getDefault().unregister(this)
    }

    fun getTrueDataSize(): Int {
        return mTrueDataSize
    }

    fun getGroupList(): ArrayList<GroupContactViewData> {
        if (groupInfoList.isEmpty()){
            val groupList = ArrayList<GroupContactViewData>()
            groupList.add(SEARCH_BAR)
            groupList.add(GROUP_EMPTY)
            return groupList
        }
        return groupInfoList
    }

    @SuppressLint("CheckResult")
    fun loadGroupList(){
        Observable.create(ObservableOnSubscribe <ArrayList<GroupContactViewData>>{
            val list = getGroupViewDataList()
            it.onNext(list)
            it.onComplete()
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    this.groupInfoList = it
                    groupContactReady(this, it)
                }, {
                   ALog.e(TAG, it)
                })

    }

    private fun getFirstLetter(groupInfo:AmeGroupInfo): Char {
        val name = groupInfo.displayName
        if (!TextUtils.isEmpty(name)) {
            val letterString = StringAppearanceUtil.getFirstCharacterLetter(name)
            if (!TextUtils.isEmpty(letterString)){
                if (LETTERS[LETTERS.size - 2] >= letterString && LETTERS[0] <= letterString) {
                    return letterString[0]
                }
            }
        }
        return ANY_CHAR
    }

    private fun getGroupViewDataList(): ArrayList<GroupContactViewData>{
        val groupProvider = AmeProvider.get<IGroupModule>(ARouterConstants.Provider.PROVIDER_GROUP_BASE)
        val groupList = groupProvider?.getJoinedListBySort() ?: listOf()
        mTrueDataSize = groupList.size
        val resultList = ArrayList<GroupContactViewData>()
        resultList.add(SEARCH_BAR)
        var firstLetter: Char? = null
        var letter: Char
        for (groupInfo in groupList) {
            letter = getFirstLetter(groupInfo)
            if (firstLetter == null) {
                firstLetter = letter
            }
            resultList.add(GroupContactViewData(letter, groupInfo, true))
        }
        SEARCH_BAR.firstLetter = firstLetter ?: '0'
        if (groupList.isEmpty()) {
            resultList.add(GROUP_EMPTY)
        }
        return resultList
    }

    @Subscribe
    fun onEvent(e: GroupListChangedEvent) {
        ALog.d(TAG, "receive GroupListChangedEvent")
        if (e.leave){
            AmeDispatcher.mainThread.dispatch {
                var found: GroupContactViewData? = null
                for (g in groupInfoList){
                    if (g.groupInfo.gid == e.gid){
                        found = g
                        break
                    }
                }

                if (found != null){
                    groupInfoList.remove(found)
                    var showEmpty = true
                    groupInfoList.forEach {
                        if (it != SEARCH_BAR) {
                            showEmpty = false
                        }
                    }
                    if (showEmpty) {
                        groupInfoList.add(GROUP_EMPTY)
                    }
                }

                groupContactReady(this, groupInfoList)
            }

        } else {
            loadGroupList()
        }

    }

    class GroupContactViewData(var firstLetter: Char, val groupInfo: AmeGroupInfo, val isTrueData: Boolean = false) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as GroupContactViewData

            if (firstLetter != other.firstLetter) return false
            if (groupInfo.gid != other.groupInfo.gid) return false

            return true
        }

        override fun hashCode(): Int {
            var result = firstLetter.hashCode()
            result = 31 * result + groupInfo.gid.hashCode()
            return result
        }
    }
}