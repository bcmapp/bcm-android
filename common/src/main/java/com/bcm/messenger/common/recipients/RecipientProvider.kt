package com.bcm.messenger.common.recipients

import android.content.Context
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.database.records.RecipientSettings
import com.bcm.messenger.common.database.repositories.RecipientRepo
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.LRUCache
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import java.util.*
import java.util.concurrent.TimeUnit


internal class RecipientProvider(private val mAccountContext: AccountContext) {

    companion object {

        private val TAG = "RecipientProvider"

        private var mCommonCache: RecipientCache = RecipientCache(WeakHashMap(3000))

        private var mUncommonCache: RecipientCache = RecipientCache(LRUCache(5000))

        @Synchronized
        fun clearCache() {
            mCommonCache.reset()
            mUncommonCache.reset()
        }

        @Synchronized
        fun findCache(address: Address): Recipient? {
            return mCommonCache[address] ?: mUncommonCache[address]
        }

        @Synchronized
        fun updateCache(recipient: Recipient) {
            val r = recipient
            if (r.isContextLogin || (r.relationship != RecipientRepo.Relationship.STRANGER && r.relationship != RecipientRepo.Relationship.REQUEST)) {
                mUncommonCache.remove(r.address)
                mCommonCache[r.address] = r
            }else {
                mCommonCache.remove(r.address)
                mUncommonCache[r.address] = r
            }
        }

        @Synchronized
        fun deleteCache(address: Address) {
            mCommonCache.remove(address)
            mUncommonCache.remove(address)
        }

        /**
         *
         * @param cachedRecipient
         * @param asynchronous
         * @return
         */
        private fun useCache(cachedRecipient: Recipient?, asynchronous: Boolean): Boolean {

            if (cachedRecipient == null) {
                ALog.d(TAG, "useCache fail, cacheRecipient is null")
                return false
            } else if (cachedRecipient.isStale) {
                return false
            } else if (!asynchronous && cachedRecipient.isResolving) {
                return false
            }
            return true

        }
    }

    private var mTaskCounter = false

    @Volatile
    private var mTaskDisposable: Disposable? = null

    private var mTargetList: MutableList<Recipient> = mutableListOf()

    @Synchronized
    fun getRecipient(context: Context, address: Address, details: RecipientDetails?, asynchronous: Boolean): Recipient {
        var current = findCache(address)
        if (!useCache(current, asynchronous)) {
            current = Recipient(address, current)
            if (asynchronous) {
                handleFetchDetailTask(context, current)
            } else {
                val newDetail = details ?: RecipientDetails(address.serialize(), null, null, null, null)
                updateRecipientDetails(context, current, newDetail)
            }

        } else {
            current?.updateRecipientDetails(details, true)
        }
        return current!!
    }

    private fun updateRecipientDetails(context: Context, recipient: Recipient, details: RecipientDetails) {
        if (recipient.isGroupRecipient) {
            updateGroupRecipientDetails(context, recipient.address, details)
        } else {
            updateIndividualRecipientDetails(context, recipient.address, details)
        }
        recipient.updateRecipientDetails(details, false)
    }

    private fun updateIndividualRecipientDetails(context: Context, address: Address, details: RecipientDetails) {
        val recipientRepo = Repository.getRecipientRepo(mAccountContext)
        if (recipientRepo != null && details.settings == null) {
            details.settings = recipientRepo.getRecipient(address.serialize())
        }
    }

    private fun getRecipientDetails(context: Context, addressMap: Map<String, Recipient>) {
        val recipientRepo = Repository.getRecipientRepo(mAccountContext)
        if (recipientRepo != null) {
            val settingList = recipientRepo.getRecipients(addressMap.map {
                ALog.d(TAG, "getRecipientDetails uid: ${it.key}")
                it.key
            })
            settingList.forEach {
                addressMap[it.uid]?.updateRecipientDetails(RecipientDetails(it.uid, null, null, it, null))
            }
        }
        for ((uid, r) in addressMap) {
            if (r.isResolving) {
                r.updateRecipientDetails(RecipientDetails(uid, null, null, null, null))
            }
        }
    }

    private fun updateGroupRecipientDetails(context: Context, groupId: Address, details: RecipientDetails) {
        val recipientRepo = Repository.getRecipientRepo(mAccountContext)
        if (recipientRepo != null && details.settings == null) {
            details.settings = recipientRepo.getRecipient(groupId.serialize())
        }
    }

    @Synchronized
    private fun lockTaskCounter(recipient: Recipient): Boolean {
        mTargetList.add(recipient)
        return if (!mTaskCounter) {
            mTaskCounter = true
            true
        }else {
            false
        }
    }

    @Synchronized
    private fun releaseTaskCounter(): Map<String, Recipient> {
        return if (mTaskCounter) {
            mTaskCounter = false
            val map = mutableMapOf<String, Recipient>()
            mTargetList.forEach {
                map[it.address.serialize()] = it
            }
            mTargetList.clear()
            map
        }else {
            mutableMapOf<String, Recipient>()
        }
    }

    /**
     * handle recipient init task
     */
    private fun handleFetchDetailTask(context: Context, recipient: Recipient) {
        ALog.d(TAG, "handleFetchDetailTask uid: ${recipient.address}")
        updateCache(recipient)
        if (lockTaskCounter(recipient)) {
            mTaskDisposable = Observable.create<Boolean> {
                ALog.d(TAG, "handleFetchDetailTask begin")
                val targetMap = releaseTaskCounter()
                getRecipientDetails(AppContextHolder.APP_CONTEXT, targetMap)
                it.onNext(true)
                it.onComplete()

            }.delaySubscription(500, TimeUnit.MILLISECONDS, AmeDispatcher.ioScheduler)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        ALog.d(TAG, "handleFetchDetailTask end")
                    }, {
                        ALog.e(TAG, "handleFetchDetailTask error", it)
                    })

        }
    }


    class RecipientDetails internal constructor(var uid: String,
                                                var customName: String?,
                                                var customAvatar: String?,
                                                var settings: RecipientSettings?,
                                                var participants: List<Recipient>?)

    private class RecipientCache internal constructor(private val cache: MutableMap<Address, Recipient>) {

        @Synchronized
        operator fun get(address: Address): Recipient? {
            return cache[address]
        }

        @Synchronized
        operator fun set(address: Address, recipient: Recipient?) {
            if (recipient != null) {
                cache[address] = recipient
            } else {
                val temp = cache[address]
                temp?.setStale()
                cache.remove(address)

            }
        }

        @Synchronized
        fun reset() {
            for (recipient in cache.values) {
                recipient.setStale()
            }
            cache.clear()
        }

        @Synchronized
        internal fun remove(address: Address) {
            this.cache.remove(address)
        }

    }

}