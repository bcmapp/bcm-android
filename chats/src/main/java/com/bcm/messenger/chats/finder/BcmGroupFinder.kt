package com.bcm.messenger.chats.finder

import com.bcm.messenger.common.core.corebean.AmeGroupInfo
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
class BcmGroupFinder :IBcmFinder {

    private val TAG = "BcmGroupFinder"

    private val mLock = java.lang.Object()
    private var mGroupList: List<AmeGroupInfo>? = null
    private var mFindResult: GroupFindResult? = null


    fun updateSource(groupList: List<AmeGroupInfo>) {
        synchronized(mLock) {
            var times = 0
            do {
                try {
                    mGroupList = groupList.sortedWith(object : Comparator<AmeGroupInfo> {
                        private val map = mutableMapOf<AmeGroupInfo, Int>()
                        override fun compare(o1: AmeGroupInfo, o2: AmeGroupInfo): Int {
                            return sort(map, o1, o2)
                        }
                    })
                    times = 2

                }catch (ex: Exception) {
                    ALog.e(TAG, "topN error", ex)
                    times++
                    if (times >= 2) {
                        mGroupList = null
                    }
                }
            }while (times < 2)
            mLock.notifyAll()
        }
    }

    override fun type(): BcmFinderType {
        return BcmFinderType.GROUP
    }

    override fun find(key: String): IBcmFindResult {
        if (mFindResult == null) {
            mFindResult = GroupFindResult(key)
        }else {
            mFindResult?.mKeyword = key
        }
        return mFindResult!!
    }

    override fun cancel() {
        synchronized(mLock) {
            mGroupList = null
            mLock.notifyAll()
        }
    }

    fun getSourceList(): List<AmeGroupInfo> {
        synchronized(mLock) {
            try {
                if (mGroupList == null) {
                    mLock.wait()
                }
                return mGroupList?.toList() ?: listOf()
            }catch (ex: Exception) {
                ALog.e(TAG, "getSourceList error", ex)
                mLock.notifyAll()
            }
        }
        return listOf()
    }

    private fun sort(map: MutableMap<AmeGroupInfo,Int>, entry1: AmeGroupInfo, entry2: AmeGroupInfo): Int {

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
            one = getCharacterLetterIndex(entry1.displayName)
            map[entry1] = one
        }
        var two = map[entry2]
        if (two == null) {
            two = getCharacterLetterIndex(entry2.displayName)
            map[entry2] = two
        }

        return one.compareTo(two)
    }

    inner class GroupFindResult(key: String) :IBcmFindResult {

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

        override fun get(position: Int): BcmFindData<AmeGroupInfo>? {
            val g = getSourceList().get(position)
            return BcmFindData(g)
        }

        override fun count(): Int {
            return getSourceList().size
        }

        override fun topN(n: Int): List<BcmFindData<AmeGroupInfo>> {
            mChanged = false
            val list = getSourceList()
            val resultList = mutableListOf<BcmFindData<AmeGroupInfo>>()
            if (mKeyword.isNotEmpty()) {
                var i = 0
                for (g in list) {
                    if (mChanged) {
                        return listOf()
                    }
                    if (StringAppearanceUtil.containIgnore(g.displayName, mKeyword)) {
                        resultList.add(BcmFindData(g))
                        i++
                    }
                    if (i >= n) {
                        break
                    }
                }
            }

            return resultList
        }

        override fun toList(): List<BcmFindData<AmeGroupInfo>> {
            mChanged = false
            val list = getSourceList()
            val resultList = mutableListOf<BcmFindData<AmeGroupInfo>>()
            if (mKeyword.isNotEmpty()) {
                for (g in list) {
                    if (mChanged) {
                        return listOf()
                    }
                    if (StringAppearanceUtil.containIgnore(g.displayName, mKeyword)) {
                        resultList.add(BcmFindData(g))
                    }
                }
            }
            return resultList
        }

    }
}