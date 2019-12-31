package com.bcm.messenger.contacts.search

import com.bcm.messenger.common.finder.BcmFindData
import com.bcm.messenger.common.finder.BcmFinderType
import com.bcm.messenger.common.finder.IBcmFindResult
import com.bcm.messenger.common.finder.IBcmFinder
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.logger.ALog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

/**
 * Created by bcm.social.01 on 2019/4/8.
 */
class BcmContactFinder : IBcmFinder {

    private val TAG = "BcmContactFinder"
    private val mSearchLock = AtomicReference(CountDownLatch(1))
    @Volatile
    private var mSearchList: List<Recipient>? = null
    @Volatile
    private var mFindResult: ContactFindResult? = null
    private val mContactLock = AtomicReference(CountDownLatch(1))
    @Volatile
    private var mContactList: List<Recipient>? = null

    fun updateSourceWithThread(threadList: List<Recipient>, comparator: Comparator<Recipient>?) {
        ALog.d(TAG, "updateSource: ${threadList.size}")
        val sourceList = mutableSetOf<Recipient>()
        sourceList.addAll(getContactList())
        sourceList.addAll(threadList)
        if (mSearchLock.get().count <= 0) {
            mSearchLock.set(CountDownLatch(1))
            ALog.d(TAG, "searchLock setCountDown")
        }
        mSearchList = sourceList.sortedWith(comparator ?: Recipient.getRecipientComparator())
        mSearchLock.get().countDown()

    }

    fun updateContact(contactList: List<Recipient>, comparator: Comparator<Recipient>?) {
        ALog.d(TAG, "updateContact: ${contactList.size}")
        if (mContactLock.get().count <= 0) {
            mContactLock.set(CountDownLatch(1))
            ALog.d(TAG, "contactLock setCountDown")

        }
        mContactList = contactList.sortedWith(comparator ?: Recipient.getRecipientComparator())
        mContactLock.get().countDown()
    }

    override fun type(): BcmFinderType {
        return BcmFinderType.ADDRESS_BOOK
    }

    override fun find(key: String): IBcmFindResult {
        ALog.d(TAG, "find key: $key")
        if (mFindResult == null) {
            mFindResult = ContactFindResult(key)
        }else {
            mFindResult?.mKeyword = key
        }
        return mFindResult!!
    }

    override fun cancel() {
        ALog.d(TAG, "cancel")
        mSearchLock.get().countDown()
        mContactLock.get().countDown()
    }

    fun getContactList(): List<Recipient> {
        try {
            mContactLock.get().await()
        }catch (ex: Exception) {
            ALog.e(TAG, "getContactList error", ex)
        }
        return mContactList ?: listOf()
    }

    fun getSourceList(): List<Recipient> {

        try {
            mSearchLock.get().await()
        }catch (ex: Exception) {
            ALog.e(TAG, "getSourceList error", ex)
        }
        return mSearchList ?: listOf()
    }

    inner class ContactFindResult(key: String) : IBcmFindResult {

        private var mChanged = false
            @Synchronized get
            @Synchronized set

        var mKeyword: String = ""
            set(value) {
                if (field != value) {
                    mChanged = true
                    field = value
                }
            }

        init {
            mKeyword = key
        }

        override fun get(position: Int): BcmFindData<Recipient>? {
            val r = getSourceList()[position]
            return BcmFindData(r)
        }

        override fun count(): Int {
            return getSourceList().size
        }

        override fun topN(n: Int): List<BcmFindData<Recipient>> {
            mChanged = false
            val list = getSourceList()
            ALog.d(TAG, "topN begin: ${list.size}")
            val resultList = mutableListOf<BcmFindData<Recipient>>()
            if (mKeyword.isNotEmpty()) {
                var i = 0
                for (r in list) {
                    if (mChanged) {
                        return listOf()
                    }
                    if (StringAppearanceUtil.containIgnore(r.name, mKeyword)) {
                        resultList.add(BcmFindData(r))
                        i++
                    }
                    if (i >= n) {
                        break
                    }
                }
            }
            ALog.d(TAG, "topN end: ${resultList.size}")
            return resultList
        }

        override fun toList(): List<BcmFindData<Recipient>> {
            mChanged = false
            val list = getSourceList()
            ALog.d(TAG, "toList begin: ${list.size}")
            val resultList = mutableListOf<BcmFindData<Recipient>>()
            if (mKeyword.isNotEmpty()) {
                for (r in list) {
                    if (mChanged) {
                        return listOf()
                    }
                    if (StringAppearanceUtil.containIgnore(r.name, mKeyword)) {
                        resultList.add(BcmFindData(r))
                    }
                }
            }
            ALog.d(TAG, "toList end: ${resultList.size}")
            return resultList
        }

    }
}