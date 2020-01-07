package com.bcm.messenger.contacts.logic

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.database.records.RecipientSettings
import com.bcm.messenger.common.database.repositories.RecipientRepo
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.manager.BcmFriendManager
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.utils.isReleaseBuild
import com.bcm.messenger.contacts.net.BcmContactCore
import com.bcm.messenger.contacts.net.BcmContactCore.Companion.CONTACT_SYNC_VERSION
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.logger.ALog
import com.google.gson.reflect.TypeToken
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * bcm.social.01 2019/3/12.
 */
class BcmContactCache(private val mAccountContext: AccountContext) {

    companion object {
        private const val TAG = "BcmContactCache"

        private const val KEY_LAST_SYNC_RESULT = "pref_last_sync_result"
        private const val KEY_HANDLING_LIST = "pref_handling_list"

        const val SYNC_FAIL = 0
        const val PATCH_FAIL = 1
        const val CONTACT_SUCCESS = 2
    }

    private val friendRoom: BcmFriendManager = BcmFriendManager()

    private var mReadyCountDown: AtomicReference<CountDownLatch> = AtomicReference(CountDownLatch(1))
    private var mInitFlag = AtomicBoolean(false)

    private var mMyContactMap = mutableMapOf<String, BcmContactCore.ContactItem>()
    private var mHandlingMap = mutableMapOf<String, BcmContactCore.ContactItem>()

    private var mLastSyncResult = AtomicInteger(0) //0：同步失败，1：上报失败，2：成功


    internal fun initCache() {

        if (!mInitFlag.getAndSet(true)) {
            val readyFlag = mReadyCountDown.get()
            if (readyFlag.count <= 0) {
                mReadyCountDown.set(CountDownLatch(1))
            }
            if (!AMELogin.isLogin) {
                ALog.w(TAG, "initCache fail, not login")
                mReadyCountDown.get().countDown()
                return
            }

            fun initPreference() {
                var lastHandlingList: List<BcmContactCore.ContactItem>? = null
                try {
                    mLastSyncResult.set(TextSecurePreferences.getIntegerPreference(mAccountContext, KEY_LAST_SYNC_RESULT, SYNC_FAIL))

                    val handlingListString = TextSecurePreferences.getStringPreference(mAccountContext, KEY_HANDLING_LIST, "")
                    if (!handlingListString.isNullOrEmpty()) {
                        lastHandlingList = GsonUtils.fromJson<List<BcmContactCore.ContactItem>>(handlingListString, object : TypeToken<List<BcmContactCore.ContactItem>>(){}.type)
                    }
                }catch (ex: Exception) {}

                val oldVersionHandlingList = friendRoom.getHandingList(mAccountContext)
                friendRoom.clearHandlingList(mAccountContext)
                val localList = Repository.getRecipientRepo(mAccountContext)?.getAllContacts() ?: emptyList()
                for (setting in localList) {
                    mMyContactMap[setting.uid] = BcmContactCore.ContactItem.from(setting)
                }
                var item: BcmContactCore.ContactItem? = null
                oldVersionHandlingList.forEach { oldItem ->
                    item = mMyContactMap[oldItem.uid]
                    item?.let {
                        mHandlingMap[it.uid] = it
                    }
                }

                if (lastHandlingList != null) {
                    lastHandlingList.forEach {
                        item = mMyContactMap[it.uid]
                        if (item != null || it.relationship == RecipientRepo.Relationship.STRANGER.type) {
                            mHandlingMap[it.uid] = it
                        }
                    }
                }
            }

            @Synchronized
            fun fixUpgradeSituation() {
                val lastVersion = TextSecurePreferences.getIntegerPreference(mAccountContext, TextSecurePreferences.CONTACT_SYNC_VERSION, 0)
                if (lastVersion < CONTACT_SYNC_VERSION) {
                    ALog.i(TAG, "fixUpgradeSituation")
                    for ((k, v) in mMyContactMap) {
                        mHandlingMap[k] = v
                    }
                    TextSecurePreferences.setIntegerPrefrence(mAccountContext, TextSecurePreferences.CONTACT_SYNC_VERSION, CONTACT_SYNC_VERSION)
                }
            }

            Observable.just(Any())
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .map {
                        ALog.i(TAG, "initCache run")
                        initPreference()
                        true

                    }.observeOn(Schedulers.io())
                    .subscribe({
                        ALog.i(TAG, "initCache end localMap: ${mMyContactMap.size}")
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
        mHandlingMap.clear()
        mMyContactMap.clear()

        TextSecurePreferences.setIntegerPrefrence(mAccountContext, KEY_LAST_SYNC_RESULT, SYNC_FAIL)
        TextSecurePreferences.setStringPreference(mAccountContext, KEY_HANDLING_LIST, "")
    }

    private fun isCacheReady(): Boolean {
        return mInitFlag.get() && mReadyCountDown.get().count <= 0 && AMELogin.isLogin
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

    fun setSyncResult(result: Int) {
        mLastSyncResult.set(result)
        TextSecurePreferences.setIntegerPrefrence(mAccountContext, KEY_LAST_SYNC_RESULT , result)
        try {
            val handlingListString = GsonUtils.toJson(getHandlingList())
            TextSecurePreferences.setStringPreference(mAccountContext, KEY_HANDLING_LIST, handlingListString)

        }catch (ex: Exception) {}
    }

    fun getLastSyncResult(): Int {
        return mLastSyncResult.get()
    }

    @Synchronized
    private fun getHandlingList(): List<BcmContactCore.ContactItem> {
        return mHandlingMap.map { it.value }
    }

    @Synchronized
    fun getHandlingUploadMap(): Map<String, List<BcmContactCore.ContactItem>> {
        val uploadMap = mutableMapOf<String, MutableList<BcmContactCore.ContactItem>>()
        convertToContactPartMap(mHandlingMap, uploadMap)

        for ((uid, v) in mMyContactMap) {
            val key = BcmContactCore.getPartKey(uid)
            val set = uploadMap[key]
            if (set != null) {
                if (!set.contains(v)) {
                    set.add(v)
                }
            }
        }
        return uploadMap
    }


    @Synchronized
    fun getUploadContactMap(withHandling: Boolean): Map<String, List<BcmContactCore.ContactItem>> {

        val uploadMap = mutableMapOf<String, MutableList<BcmContactCore.ContactItem>>()
        convertToContactPartMap(mMyContactMap, uploadMap)
        if (withHandling) {
            convertToContactPartMap(mHandlingMap, uploadMap)
        }

        ALog.i(TAG, "getUploadContactMap myContactMap: ${mMyContactMap.size}, uploadMap: ${uploadMap.size}")
        return uploadMap
    }

    private fun convertToContactPartMap(inputMap: Map<String, BcmContactCore.ContactItem>, outputMap: MutableMap<String, MutableList<BcmContactCore.ContactItem>> ) {
        var key: String
        var set: MutableList<BcmContactCore.ContactItem>?
        for ((uid, v) in inputMap) {
            key = BcmContactCore.getPartKey(uid)
            set = outputMap[key]
            if (set == null) {
                set = mutableListOf()
                outputMap[key] = set
            }
            set.remove(v)
            set.add(v)
        }
    }

    @Synchronized
    fun updateSyncContactMap(syncContactMap: Map<String, List<BcmContactCore.ContactItem>>, replace: Boolean) {

        if (!isReleaseBuild()) {
            ALog.i(TAG, "updateSyncContactMap syncContactMap: ${GsonUtils.toJson(syncContactMap)}, \ncurrentMap: ${GsonUtils.toJson(mMyContactMap)}")
        }
        val updateList = mutableListOf<RecipientSettings>()
        val localMap = mutableMapOf<String, MutableList<BcmContactCore.ContactItem>>()
        convertToContactPartMap(mMyContactMap, localMap)
        var localList: MutableList<BcmContactCore.ContactItem>?
        for ((key, syncSet) in syncContactMap) {
            localList = localMap[key]

            for (new in syncSet) {
                var old = mMyContactMap[new.uid]
                if (old == null) {
                    old = new
                    updateList.add(new.toSetting())
                } else {
                    localList?.remove(old)
                    var update = false
                    if (old.relationship != new.relationship) {
                        old.relationship = new.relationship
                        update = true
                    }

                    if (replace) {
                        if (old.profileName != new.profileName) {
                            old.profileName = new.profileName
                            update = true
                        }
                        if (old.localName != new.localName) {
                            old.localName = new.localName
                            update = true
                        }
                        if (old.nameKey != new.nameKey) {
                            old.nameKey = new.nameKey
                            update = true
                        }
                        if (old.avatarKey != new.avatarKey) {
                            old.avatarKey = new.avatarKey
                            update = true
                        }
                    }
                    if (update) {
                        updateList.add(old.toSetting())
                    }
                }
                mMyContactMap[old.uid] = old
            }

            if (!localList.isNullOrEmpty()) { //最终localList不为空，表明云端已经没有该key对应的通讯录列表，则要遍历设置为陌生人并删除
                for (old in localList) {
                    old.relationship = RecipientRepo.Relationship.STRANGER.type
                    updateList.add(old.toSetting())
                    mMyContactMap.remove(old.uid)
                }
            }
        }

        // 更新本地数据库
        Repository.getRecipientRepo(mAccountContext)?.updateBcmContacts(updateList)

    }

    @Synchronized
    fun addRelationHandling(target: Recipient, relationship: RecipientRepo.Relationship,
                            callback: (bloomList: List<BcmContactCore.ContactItem>, doAfter: (success: Boolean) -> Unit) -> Unit) {
        ALog.i(TAG, "addRelationHandling begin, relationship: $relationship")
        var handling = mHandlingMap[target.address.serialize()]
        if (handling == null) {
            handling = BcmContactCore.ContactItem.from(target.settings)
            mHandlingMap[target.address.serialize()] = handling
        }
        handling.relationship = relationship.type
        if (relationship == RecipientRepo.Relationship.STRANGER) {
            handling.localName = ""
            handling.profileName = ""
            handling.nameKey = ""
            handling.avatarKey = ""
        }

        val bloomList = mutableListOf<BcmContactCore.ContactItem>()
        for ((k, c) in mMyContactMap) {
            bloomList.remove(c)
            bloomList.add(c)
        }
        for ((k, c) in mHandlingMap) {
            bloomList.remove(c)
            bloomList.add(c)
        }
        callback(bloomList) {
            if (it) {
                target.relationship = relationship
                ALog.i(TAG, "addRelationHandling result: $it, relationship: $relationship")
            }
        }
    }

    @Synchronized
    fun doneHandling(doneMap: Map<String, List<BcmContactCore.ContactItem>>) {
        for ((key, list) in doneMap) {
            for (item in list) {
                mHandlingMap.remove(item.uid)
            }
        }
    }

    @Synchronized
    fun addPropertyChangedHandling(target: Recipient, callback: (doAfter: (success: Boolean) -> Unit) -> Unit) {
        var handling = mHandlingMap[target.address.serialize()]
        if (handling == null) {
            handling = BcmContactCore.ContactItem.from(target.settings)
            mHandlingMap[target.address.serialize()] = handling
        }else {
            handling.profileName = target.profileName ?: ""
            handling.localName = target.localName ?: ""
            handling.nameKey = target.privacyProfile.nameKey ?: ""
            handling.avatarKey = target.privacyProfile.avatarKey ?: ""
        }
        callback {
            ALog.i(TAG, "addPropertyChangedHandling result: $it")
        }
    }

}