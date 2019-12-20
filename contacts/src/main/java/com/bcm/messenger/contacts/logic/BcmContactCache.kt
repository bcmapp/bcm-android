package com.bcm.messenger.contacts.logic

import com.bcm.messenger.common.database.records.RecipientSettings
import com.bcm.messenger.common.database.repositories.RecipientRepo
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.manager.BcmFriendManager
import com.bcm.messenger.common.grouprepository.room.entity.BcmFriend
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.common.utils.isReleaseBuild
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Created by bcm.social.01 on 2019/3/12.
 */
class BcmContactCache() {

    companion object {
        private const val TAG = "BcmContactCache"
    }

    private val friendRoom: BcmFriendManager = BcmFriendManager()
    private var myContactList = mutableMapOf<String, MutableSet<RecipientSettings>>()
    private val handlingList = mutableSetOf<BcmFriend>()

    private var mReadyCountDown: AtomicReference<CountDownLatch> = AtomicReference(CountDownLatch(1))
    private var mInitFlag = AtomicBoolean(false)

    internal fun initCache() {

        if (!mInitFlag.getAndSet(true)) {
            val readyFlag = mReadyCountDown.get()
            if (readyFlag.count <= 0) {
                mReadyCountDown.set(CountDownLatch(1))
            }
            if (!AMESelfData.isLogin) {
                ALog.w(TAG, "initCache fail, not login")
                mReadyCountDown.get().countDown()
                return
            }

            @Synchronized
            fun fixUpgradeSituation() {
                val lastVersion = TextSecurePreferences.getIntegerPreference(AppContextHolder.APP_CONTEXT, TextSecurePreferences.CONTACT_SYNC_VERSION, 0)
                if (lastVersion != RecipientSettings.CONTACT_SYNC_VERSION) {
                    ALog.i(TAG, "fixUpgradeSituation")
                    val settingList = Repository.getRecipientRepo()?.getContactsFromOneSide()
                    if (!settingList.isNullOrEmpty()) {
                        ALog.i(TAG, "fixUpgradeSituation currentContacts: ${settingList.size}")
                        settingList.forEach {
                            val key = if (it.contactPartKey.isNullOrEmpty()) {
                                BcmContactLogic.getPartKey(it.uid).apply {
                                    it.contactPartKey = this
                                }
                            } else {
                                it.contactPartKey ?: "0"
                            }
                            val h = if (it.relationship == RecipientRepo.Relationship.STRANGER.type) {
                                BcmFriend(it.uid, key, BcmFriend.DELETING)
                            } else {
                                BcmFriend(it.uid, key, BcmFriend.ADDING)
                            }
                            this.handlingList.remove(h)
                            this.handlingList.add(h)
                        }
                        friendRoom.saveHandlingList(this.handlingList.toList())
                    }
                    TextSecurePreferences.setIntegerPrefrence(AppContextHolder.APP_CONTEXT, TextSecurePreferences.CONTACT_SYNC_VERSION, RecipientSettings.CONTACT_SYNC_VERSION)
                }
            }

            Observable.just(Any())
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .map {
                        ALog.i(TAG, "initCache run")
                        val handlingList = friendRoom.getHandingList()
                        val localList = getLocalContactList()
                        val contactMap = HashMap<String, MutableSet<RecipientSettings>>()
                        for (setting in localList) {
                            val key = BcmContactLogic.getPartKey(setting.uid)
                            setting.contactPartKey = key
                            var set = contactMap[key]
                            if (null == set) {
                                set = mutableSetOf()
                                contactMap[key] = set
                            }
                            set.add(setting)
                        }
                        Pair(contactMap, handlingList)

                    }.observeOn(Schedulers.io())
                    .subscribe({
                        ALog.i(TAG, "initCache end")
                        for ((key, set) in it.first) {
                            updateContact(key, set)
                        }
                        this.handlingList.clear()
                        this.handlingList.addAll(it.second)
                        fixUpgradeSituation()
                        mReadyCountDown.get().countDown()
                    }, {
                        mReadyCountDown.get().countDown()
                        ALog.e(TAG, "initCache error", it)
                    })
        }
    }

    fun clearCache() {
        ALog.i(TAG, "clearCache")
        mInitFlag.set(false)
        clearContact()
    }

    @Synchronized
    private fun clearContact() {
        ALog.i(TAG, "clearContact")
        handlingList.clear()
        myContactList.clear()
    }

    private fun isCacheReady(): Boolean {
        return mInitFlag.get() && mReadyCountDown.get().count <= 0 && AMESelfData.isLogin
    }

    fun waitCacheReady(): Boolean {
        try {
            mReadyCountDown.get().await()
        }catch (ex: Exception) {
            ALog.e(TAG, "waitCacheReady error", ex)
        }
        ALog.i(TAG, "waitCacheReady continue")
        return isCacheReady()
    }

    @Synchronized
    fun getContactSet(partKey: String): Set<RecipientSettings>? {
        return myContactList[partKey]?.toSet()
    }

    @Synchronized
    private fun updateContact(partKey: String, setting: RecipientSettings) {
        setting.contactPartKey = partKey
        var set = myContactList[partKey]
        if (set == null) {
            set = mutableSetOf()
            set.add(setting)
            myContactList[partKey] = set
        }else {
            set.remove(setting)
            set.add(setting)
        }
        if (!isReleaseBuild()) {
            ALog.d(TAG, "updateContact partKey: $partKey, setting: ${GsonUtils.toJson(setting)}, currentMap: ${GsonUtils.toJson(myContactList)}")
        }
    }

    @Synchronized
    private fun updateContact(partKey: String, settingList: Set<RecipientSettings>) {
        settingList.forEach {
            it.contactPartKey = partKey
        }
        var set = myContactList[partKey]
        if (set == null) {
            set = mutableSetOf()
            set.addAll(settingList)
            myContactList[partKey] = set
        }else {
            set.clear()
            set.addAll(settingList)
        }
        if (!isReleaseBuild()) {
            ALog.d(TAG, "updateContact partKey: $partKey, settingList: ${GsonUtils.toJson(settingList)}, currentMap: ${GsonUtils.toJson(myContactList)}")
        }
    }

    @Synchronized
    private fun deleteContact(partKey: String, setting: RecipientSettings) {
        setting.contactPartKey = partKey
        myContactList[partKey]?.remove(setting)
    }

    @Synchronized
    fun getContactMap(): Map<String, Set<RecipientSettings>> {
        val contactMap = mutableMapOf<String, Set<RecipientSettings>>()
        for ((key, set) in myContactList) {
            contactMap[key] = set.toSet()
        }
        return contactMap
    }

    private fun getLocalContactList(): List<RecipientSettings> {
        return Repository.getRecipientRepo()?.getFriendsFromContact() ?: emptyList()
    }


    @Synchronized
    fun getHandlingList(): Set<BcmFriend> {
        return handlingList.toSet()
    }

    @Synchronized
    fun localHandlingMap(handlingList: Set<BcmFriend>): Map<String, RecipientSettings> {
        if (handlingList.isEmpty()) {
            return mapOf()
        }
        val localMap = mutableMapOf<String, RecipientSettings>()
        for (h in handlingList) {
            val l: MutableSet<RecipientSettings> = myContactList[h.tag] ?: continue
            for (s in l) {
                if (s.uid == h.uid) {
                    if (s.contactPartKey.isNullOrEmpty()) {
                        s.contactPartKey = h.tag
                    }
                    localMap[s.uid] = s
                    break
                }
            }
        }
        return localMap
    }

    @Synchronized
    fun removeDoneList(doneList: Set<BcmFriend>) {
        val toDeleteList = mutableListOf<BcmFriend>()
        for (done in doneList) {
            for (cur in handlingList) {
                if (done.uid == cur.uid) {
                    if (done.state == cur.state) {
                        toDeleteList.add(done)
                    }
                    break
                }
            }
        }
        if (toDeleteList.isNotEmpty()) {
            this.handlingList.removeAll(toDeleteList)
            friendRoom.saveHandlingList(handlingList.toList())
        }else {
            ALog.i(TAG, "no need to remove doneList")
        }
    }

    @Synchronized
    fun addHandlingList(handling: RecipientSettings, replace: Boolean = true) {

        val key = if (handling.contactPartKey.isNullOrEmpty()) {
            BcmContactLogic.getPartKey(handling.uid).apply {
                handling.contactPartKey = this
            }
        }else {
            handling.contactPartKey ?: "0"
        }
        val h = if (handling.relationship == RecipientRepo.Relationship.STRANGER.type) {
            BcmFriend(handling.uid, key, BcmFriend.DELETING)
        }else {
            BcmFriend(handling.uid, key, BcmFriend.ADDING)
        }

        if (replace) {
            this.handlingList.remove(h)
        }
        this.handlingList.add(h)
        friendRoom.saveHandlingList(handlingList.toList())

    }

    fun getLastUploadHashMap(): Map<String, Long> {
        val map = friendRoom.loadLastSyncHashMap()
        return if (null == map) {
            val m = HashMap<String, Long>()
            for (i in 0 until BcmContactLogic.CONTACT_PART_MAX) {
                m[i.toString()] = 0
            }
            friendRoom.saveSyncHashMap(m)
            m
        } else {
            map
        }
    }

    fun updateSyncContactMap(syncContactMap: Map<String, Set<RecipientSettings>>, syncHashMap: Map<String, Long>, doneList: Set<BcmFriend>) {

        ALog.d(TAG, "updateSyncContactMap syncContactMap: ${GsonUtils.toJson(syncContactMap)}, \ncurrentMap: ${GsonUtils.toJson(myContactList)}")

        val updateList = mutableListOf<RecipientSettings>()
        for ((key, syncSet) in syncContactMap) {
            updateContact(key, syncSet)
            syncSet.forEach {
                updateList.remove(it)
                updateList.add(it)
            }
        }

        removeDoneList(doneList)

        if (syncContactMap.isNotEmpty()) {

            ALog.d(TAG, "updateSyncContactMap syncHashMap: ${GsonUtils.toJson(syncHashMap)}")

            val saveHashMap = mutableMapOf<String, Long>()
            friendRoom.loadLastSyncHashMap()?.let {
                saveHashMap.putAll(it)
            }
            saveHashMap.putAll(syncHashMap)

            for (i in 0 until BcmContactLogic.CONTACT_PART_MAX) {
                if (saveHashMap[i.toString()] == null) {
                    saveHashMap[i.toString()] = 0
                }
            }
            friendRoom.saveSyncHashMap(saveHashMap)
        }
        Repository.getRecipientRepo()?.updateBcmContacts(updateList, false)

    }

    fun addHandling(setting: RecipientSettings): String {

        addHandlingList(setting)
        Repository.getRecipientRepo()?.updateBcmContacts(listOf(setting), true)
        val key = BcmContactLogic.getPartKey(setting.uid)
        updateContact(key, setting)

        return key

    }

    fun addPropertyChangedHandling(setting: RecipientSettings): String {
        addHandlingList(setting, false)
        val key = BcmContactLogic.getPartKey(setting.uid)
        updateContact(key, setting)
        return key
    }

}