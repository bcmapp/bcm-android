package com.bcm.messenger.common.finder

import com.bcm.messenger.common.core.Address
import com.google.gson.reflect.TypeToken

/**
 * Created by bcm.social.01 on 2019/4/8.
 * 检索器接口
 */
interface IBcmFinder {
    /**
     * 查询检索器类型
     * return finder type #BcmFinderType
     */
    fun type(): BcmFinderType

    /**
     * 检索数据
     */
    fun find(key:String): IBcmFindResult

    /**
     * 检索指定address的数据
     */
    fun findWithTarget(key:String, targetAddress:Address) :IBcmFindResult {
        return object :IBcmFindResult {
            override fun get(position: Int): BcmFindData<*> {
                TODO("not support")
            }

            override fun count(): Int {
                return 0
            }

            override fun topN(n: Int): List<BcmFindData<*>> {
                return listOf()
            }

            override fun toList(): List<BcmFindData<*>> {
                return listOf()
            }

        }
    }

    /**
     * 取消检索
     */
    fun cancel()

}