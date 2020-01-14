package com.bcm.messenger.common.finder

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.R
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.utils.AccountContextMap
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.Base64
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * Created by bcm.social.01 on 2019/4/8.
 * 
 * eg
 *  
 *  val resultMap = BcmFinderManager.get().find("abc", listOf(BcmFinderType.ADDRESS_BOOK, BcmFinderType.GROUP))
 *
 *  for( (type, result) in resultMap ) {
 *      val top10List = result.topN(10) //type10
 *
 *      val allList = result.toList() //type
 *
 *      if(result.count > 0) {
 *          val top1 = result.get(0)  //type
 *      }
 *  }
 */
object BcmFinderManager: AccountContextMap<BcmFinderManager.BcmFinderManagerImpl>({
    BcmFinderManagerImpl(it)
}) {
    private const val TAG = "BcmFinderManager"
    private const val SEARCH_TABLE = "table_search_whole_"
    private const val RECORD_KEY = "key_record"
    private const val mSearchLimit: Int = 3
    private const val mRecentLimit: Int = 10

    interface SearchRecordChecker {
        fun isValid(record: SearchRecordDetail): Boolean
    }

    class BcmFinderManagerImpl(private val accountContext: AccountContext) {
        private val finderMap = HashMap<BcmFinderType, IBcmFinder>()
        private var mRecordMap: MutableMap<String, SearchRecord>? = null

        /**
         *
         * @param finder
         */
        fun registerFinder(finder: IBcmFinder) {
            ALog.d(TAG, "registerFinder: ${finder.type()}")
            finderMap[finder.type()] = finder
        }

        /**
         *
         */
        fun unRegisterFinder(finder: IBcmFinder) {
            if (finder == finderMap[finder.type()]) {
                finderMap[finder.type()]?.cancel()
                finderMap.remove(finder.type())
            }
        }

        fun getFinder(finderType: BcmFinderType): IBcmFinder? {
            return finderMap[finderType]
        }

        /**
         * @param key
         * @param fromTypes Finder
         * @return
         */
        fun find(key:String, fromTypes: Array<BcmFinderType>): Map<BcmFinderType, IBcmFindResult> {
            val resultMap = HashMap<BcmFinderType, IBcmFindResult>()

            for (type in fromTypes) {
                val finder = finderMap[type]
                if (null != finder) {
                    resultMap[type] =  finder.find(key)
                } else {
                    ALog.e(TAG, "no finder $type")
                }
            }

            return resultMap
        }

        /**
         * @param key
         * @param fromTypes Finder
         * @return
         */
        fun findWithTarget(key:String, targetAddress:Address, fromTypes:Array<BcmFinderType>) :Map<BcmFinderType, IBcmFindResult> {
            val resultMap = HashMap<BcmFinderType, IBcmFindResult>()

            for (type in fromTypes) {
                val finder = finderMap[type]
                if (null != finder) {
                    resultMap[type] =  finder.findWithTarget(key, targetAddress)
                } else {
                    ALog.e(TAG, "no finder $type")
                }
            }

            return resultMap
        }

        fun cancel() {
            val list = finderMap.values
            for (finder in list) {
                finder.cancel()
            }
        }

        /**
         *
         */
        fun clearRecord() {
            getAccountPreferences(accountContext).edit().clear().apply()
        }

        /**
         *
         */
        @SuppressLint("CheckResult")
        fun querySearchRecord(checker: SearchRecordChecker, callback: (result: List<SearchRecordDetail>) -> Unit) {
            ALog.d(TAG, "querySearchRecord")
            Observable.create(ObservableOnSubscribe<List<SearchRecordDetail>> {

                val map = getRecordMap()
                val resultList = map.mapNotNull {
                    var r = transform(it.key, it.value)
                    if (r != null) {
                        if (!checker.isValid(r)) {
                            r = null
                        }
                    }
                    r
                }.sortedWith(Comparator<SearchRecordDetail> { o1, o2 ->
                    when {
                        //
                        o2.date < o1.date -> -1
                        o2.date > o1.date -> 1
                        else -> 0
                    }
                })
                if (resultList.size >= mRecentLimit) {
                    it.onNext(resultList.subList(0, mRecentLimit))
                }else {
                    it.onNext(resultList)
                }
                it.onComplete()

            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({resultList ->
                        callback.invoke(resultList)
                    }, {
                        ALog.e(TAG, "querySearchRecord error", it)
                        callback.invoke(listOf())
                    })

        }

        /**
         *
         */
        @SuppressLint("CheckResult")
        fun querySearchResultLimit(keyword: String, types: Array<BcmFinderType>, callback: (result: List<SearchItemData>) -> Unit) {
            ALog.d(TAG, "querySearchResultLimit keyword: $keyword types: ${types.joinToString()}")
            Observable.create(ObservableOnSubscribe<List<SearchItemData>> {
                val resultList = mutableListOf<SearchItemData>()
                val hasTop = types.isNotEmpty()
                val bcmFindResultMap = find(keyword, types)
                for (type in types) {
                    val l = findSearchResult(true, type, bcmFindResultMap[type]
                            ?: continue)
                    if (hasTop && l.isNotEmpty()) {
                        l.first().isTop = true
                    }
                    resultList.addAll(l)
                }
                it.onNext(resultList)
                it.onComplete()

            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        callback.invoke(it)
                    }, {
                        ALog.e(TAG, "querySearchResultLimit error", it)
                        callback.invoke(listOf())
                    })

        }

        /**
         *
         */
        @SuppressLint("CheckResult")
        fun querySearchResult(keyword: String, types: Array<BcmFinderType>, callback: (result: List<SearchItemData>) -> Unit) {
            ALog.d(TAG, "querySearchResult keyword: $keyword, types: ${types.joinToString()}")

            Observable.create(ObservableOnSubscribe<List<SearchItemData>> {
                val resultList = mutableListOf<SearchItemData>()
                val hasTop = types.isNotEmpty()
                val bcmFindResultMap = find(keyword, types)
                for (type in types) {
                    val l = findSearchResult(false, type, bcmFindResultMap[type]
                            ?: continue)
                    if (hasTop && l.isNotEmpty()) {
                        l.first().isTop = true
                    }
                    resultList.addAll(l)
                }
                it.onNext(resultList)
                it.onComplete()

            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        callback.invoke(it)
                    }, {
                        ALog.e(TAG, "querySearchResult error", it)
                        callback.invoke(listOf())
                    })

        }

        /**
         *
         */
        private fun findSearchResult(isLimit: Boolean, type: BcmFinderType, findResult: IBcmFindResult): List<SearchItemData> {
            ALog.d(TAG, "findSearchResult isLimit: $isLimit type: $type")
            var hasMore = false
            val resultList = if (isLimit) {
                val list = findResult.topN(mSearchLimit + 1)
                if (list.size > mSearchLimit) {
                    hasMore = true
                    list.subList(0, mSearchLimit)
                }else {
                    list
                }
            }else {
                findResult.toList()

            }.mapNotNull {
                transform(type, it)
            }

            if (hasMore && resultList.isNotEmpty()) {
                resultList.last().hasMore = true
            }
            ALog.d(TAG, "findSearchResult isLimit: $isLimit type: $type result: ${resultList.size}")
            return resultList
        }

        /**
         * bcmFindDataSearchData
         */
        private fun transform(type: BcmFinderType, data: BcmFindData<*>): SearchItemData? {
            return when(type) {
                BcmFinderType.ADDRESS_BOOK -> {
                    val result = SearchItemData()
                    result.tag = data.source
                    result.type = type
                    result.moreDescription = AppContextHolder.APP_CONTEXT.getString(R.string.common_current_search_contact_more)
                    result.title = AppContextHolder.APP_CONTEXT.getString(R.string.common_current_search_contact_title)
                    result
                }
                BcmFinderType.GROUP -> {
                    val result = SearchItemData()
                    result.tag = data.source
                    result.type = type
                    result.moreDescription = AppContextHolder.APP_CONTEXT.getString(R.string.common_current_search_group_more)
                    result.title = AppContextHolder.APP_CONTEXT.getString(R.string.common_current_search_group_title)
                    result
                }
                BcmFinderType.AIR_CHAT -> {
                    val result = SearchItemData()
                    result.tag = data.source
                    result.type = type
                    result.moreDescription = AppContextHolder.APP_CONTEXT.getString(R.string.common_current_search_adhoc_more)
                    result.title = AppContextHolder.APP_CONTEXT.getString(R.string.common_current_search_adhoc_title)
                    result
                }
                BcmFinderType.USER_ID -> {
                    val result = SearchItemData()
                    result.tag = data.source
                    result.type = type
                    result.moreDescription = ""
                    result.title = "BCM ID"
                    result
                }
                else -> null
            }
        }

        /**
         *
         */
        @SuppressLint("CheckResult")
        fun saveRecord(type: BcmFinderType, key: String) {

            Observable.create(ObservableOnSubscribe<Boolean> {
                val map = getRecordMap()
                val nk = type.name + "_" + key
                var record = map[nk]
                if (record == null) {
                    record = SearchRecord()
                    record.type = type
                    record.times++
                    record.date = System.currentTimeMillis()
                    map[nk] = record
                }else {
                    // ，
                    record.date = System.currentTimeMillis()
                    record.times++
                }
                try {
                    val bios = ByteArrayOutputStream()
                    ObjectOutputStream(bios).use {
                        it.writeObject(map)
                    }
                    val body = Base64.encodeBytes(bios.toByteArray())
                    getAccountPreferences(accountContext).edit().putString(RECORD_KEY, body).apply()

                }catch (ex: Exception) {
                    ALog.e(TAG, "saveRecord error", ex)
                }
                it.onNext(true)
                it.onComplete()

            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({

                    }, {
                        ALog.e(TAG, "saveRecord type: $type key: $key error", it)
                    })

        }

        private fun transform(key: String, innerRecord: SearchRecord): SearchRecordDetail? {
            val record = SearchRecordDetail(innerRecord)
            val tag = key.removePrefix(record.type.name + "_")
            if (tag == key || tag.isNullOrEmpty()) {
                return null
            }
            record.tag = tag
            return record
        }

        @Synchronized
        private fun getRecordMap(): MutableMap<String, SearchRecord> {
            var map: MutableMap<String, SearchRecord>? = mRecordMap
            if (map == null) {
                val pref = getAccountPreferences(accountContext)
                val body = pref.getString(RECORD_KEY, "")
                if (body.isEmpty()) {

                } else {
                    try {
                        ObjectInputStream(ByteArrayInputStream(Base64.decode(body))).use {
                            map = it.readObject() as HashMap<String, SearchRecord>
                        }
                    }catch (ex: Exception) {
                        ALog.e(TAG, "getRecordMap error", ex)
                        pref.edit().putString(RECORD_KEY, "").apply()
                    }
                }
            }
            if (map == null) {
                map = HashMap<String, SearchRecord>()
            }
            mRecordMap = map
            return map!!

        }

        /**
         * pref
         */
        private fun getAccountPreferences(accountContext: AccountContext): SharedPreferences {
            ALog.d(TAG, "getAccountPreferences table: $SEARCH_TABLE${accountContext.uid}")
            return AppContextHolder.APP_CONTEXT.getSharedPreferences("$SEARCH_TABLE${accountContext.uid}", Context.MODE_PRIVATE)
        }
    }


}