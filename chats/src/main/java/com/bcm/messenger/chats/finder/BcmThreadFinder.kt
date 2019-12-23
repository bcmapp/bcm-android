package com.bcm.messenger.chats.finder

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
class BcmThreadFinder: IBcmFinder {

    private val TAG = "BcmThreadFinder"
    private val mLock = AtomicReference(CountDownLatch(1))
    private var mRecipientList: List<Recipient>? = null
    private var mFindResult: ThreadFindResult? = null

    fun updateSource(recipientList: List<Recipient>, comparator: Comparator<Recipient>?) {
        ALog.d(TAG, "updateSource: ${recipientList.size}")
        if (mLock.get().count <= 0) {
            mLock.set(CountDownLatch(1))
        }
        mRecipientList = recipientList.sortedWith(comparator ?: Recipient.getRecipientComparator())
        mLock.get().countDown()
    }

    override fun type(): BcmFinderType {
        return BcmFinderType.THREAD
    }

    override fun find(key: String): IBcmFindResult {
        ALog.d(TAG, "find key: $key")
        if (mFindResult == null) {
            mFindResult = ThreadFindResult(key)
        }else {
            mFindResult?.mKeyword = key
        }
        return mFindResult!!
    }

    override fun cancel() {
        mLock.get().countDown()
    }

    fun getSourceList(): List<Recipient> {
        try {
            mLock.get().await()
        }catch (ex: Exception) {}
        return mRecipientList ?: listOf()
    }

    inner class ThreadFindResult(key: String) : IBcmFindResult {

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