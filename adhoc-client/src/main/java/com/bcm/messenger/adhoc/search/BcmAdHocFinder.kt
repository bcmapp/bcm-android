package com.bcm.messenger.adhoc.search

import com.bcm.messenger.adhoc.logic.AdHocSession
import com.bcm.messenger.common.finder.BcmFindData
import com.bcm.messenger.common.finder.BcmFinderType
import com.bcm.messenger.common.finder.IBcmFindResult
import com.bcm.messenger.common.finder.IBcmFinder
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.logger.ALog

/**
 *
 * Created by wjh on 2019-09-08
 */
class BcmAdHocFinder : IBcmFinder {

    private val TAG = "BcmAdHocFinder"
    private val mSearchLock = java.lang.Object()
    private var mSearchList: List<AdHocSession>? = null
    private var mFindResult: SessionFindResult? = null

    fun updateSource(sessionList: List<AdHocSession>) {
        ALog.d(TAG, "updateSource: ${sessionList.size}")
        synchronized(mSearchLock) {
            var times = 0
            do {
                try {
                    mSearchList = sessionList.sortedWith(object : Comparator<AdHocSession> {
                        private val map = mutableMapOf<AdHocSession, Int>()
                        override fun compare(o1: AdHocSession, o2: AdHocSession): Int {
                            return sort(map, o1, o2)
                        }
                    })
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

    private fun sort(map: MutableMap<AdHocSession,Int>, entry1: AdHocSession, entry2: AdHocSession): Int {

        fun getCharacterLetterIndex(name: String): Int {
            if (name.isNotEmpty()) {
                val n = StringAppearanceUtil.getFirstCharacterLetter(name)
                for (i in Recipient.LETTERS.indices) {
                    if (n == Recipient.LETTERS[i]) {
                        return i
                    }
                }
            }
            return Recipient.LETTERS.size - 1
        }

        var one = map[entry1]
        if (one == null) {
            one = getCharacterLetterIndex(entry1.displayName())
            map[entry1] = one
        }
        var two = map[entry2]
        if (two == null) {
            two = getCharacterLetterIndex(entry2.displayName())
            map[entry2] = two
        }

        return one.compareTo(two)
    }


    override fun type(): BcmFinderType {
        return BcmFinderType.AIR_CHAT
    }

    override fun find(key: String): IBcmFindResult {
        ALog.d(TAG, "find key: $key")
        if (mFindResult == null) {
            mFindResult = SessionFindResult(key)
        }else {
            mFindResult?.mKeyword = key
        }
        return mFindResult!!
    }

    override fun cancel() {
        synchronized(mSearchLock) {
            mSearchList = null
            mSearchLock.notifyAll()
        }
    }

    fun getSourceList(): List<AdHocSession> {
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

    inner class SessionFindResult(key: String) : IBcmFindResult {

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

        override fun get(position: Int): BcmFindData<AdHocSession>? {
            val r = getSourceList()[position]
            return BcmFindData(r)
        }

        override fun count(): Int {
            return getSourceList().size
        }

        override fun topN(n: Int): List<BcmFindData<AdHocSession>> {
            mChanged = false
            val list = getSourceList()
            ALog.d(TAG, "topN begin: ${list.size}")
            val resultList = mutableListOf<BcmFindData<AdHocSession>>()
            if (mKeyword.isNotEmpty()) {
                var i = 0
                for (s in list) {
                    if (mChanged) {
                        return listOf()
                    }
                    if (StringAppearanceUtil.containIgnore(s.displayName(), mKeyword)) {
                        resultList.add(BcmFindData(s))
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

        override fun toList(): List<BcmFindData<AdHocSession>> {
            mChanged = false
            val list = getSourceList()
            ALog.d(TAG, "toList begin: ${list.size}")
            val resultList = mutableListOf<BcmFindData<AdHocSession>>()
            if (mKeyword.isNotEmpty()) {
                for (s in list) {
                    if (mChanged) {
                        return listOf()
                    }
                    if (StringAppearanceUtil.containIgnore(s.displayName(), mKeyword)) {
                        resultList.add(BcmFindData(s))
                    }
                }
            }
            ALog.d(TAG, "toList end: ${resultList.size}")
            return resultList
        }

    }

}