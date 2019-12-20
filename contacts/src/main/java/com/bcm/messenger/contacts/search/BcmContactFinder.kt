package com.bcm.messenger.contacts.search

import com.bcm.messenger.common.finder.BcmFindData
import com.bcm.messenger.common.finder.BcmFinderType
import com.bcm.messenger.common.finder.IBcmFindResult
import com.bcm.messenger.common.finder.IBcmFinder
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.logger.ALog

/**
 * Created by bcm.social.01 on 2019/4/8.
 */
class BcmContactFinder : IBcmFinder {

    private val TAG = "BcmContactFinder"
    private val mSearchLock = java.lang.Object()
    private var mSearchList: List<Recipient>? = null
    private var mFindResult: ContactFindResult? = null
    private val mContactLock = java.lang.Object()
    private var mContactList: List<Recipient>? = null

    fun updateSourceWithThread(threadList: List<Recipient>, comparator: Comparator<Recipient>?) {
        ALog.d(TAG, "updateSource: ${threadList.size}")
        val sourceList = mutableSetOf<Recipient>()
        sourceList.addAll(getContactList())
        sourceList.addAll(threadList)
        synchronized(mSearchLock) {
            var times = 0
            do {
                try {
                    mSearchList = sourceList.sortedWith(comparator ?: Recipient.getRecipientComparator())
                    times = 2
                }catch (ex: Exception) {
                    ALog.e(TAG, "updateSource error", ex)
                    times ++
                    if (times >= 2) {
                        mSearchList = null
                    }
                }
            }while (times < 2)
            mSearchLock.notifyAll()
        }
    }

    fun updateContact(contactList: List<Recipient>, comparator: Comparator<Recipient>?) {
        ALog.d(TAG, "updateContact: ${contactList.size}")
        synchronized(mContactLock) {
            var times = 0
            do {
                try {
                    mContactList = contactList.sortedWith(comparator ?: Recipient.getRecipientComparator())
                    times = 2
                }catch (ex: Exception) {
                    ALog.e(TAG, "updateSource error", ex)
                    times ++
                    if (times >= 2) {
                        mContactList = null
                    }
                }
            }while (times < 2)
            mContactLock.notifyAll()
        }
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
        synchronized(mSearchLock) {
            mSearchList = null
            mSearchLock.notifyAll()
        }
        synchronized(mContactLock) {
            mContactList = null
            mContactLock.notifyAll()
        }
    }

    fun getContactList(): List<Recipient> {
        synchronized(mContactLock) {
            try {
                if (mContactList == null) {
                    mContactLock.wait()
                }
                return mContactList?.toList() ?: listOf()

            }catch (ex: Exception) {
                mContactLock.notifyAll()
            }
        }
        return listOf()
    }

    fun getSourceList(): List<Recipient> {
        synchronized(mSearchLock) {
            try {
                if (mSearchList == null) {
                    mSearchLock.wait()
                }
                return mSearchList?.toList() ?: listOf()

            }catch (ex: Exception) {
                mSearchLock.notifyAll()
            }
        }
        return listOf()
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