package com.bcm.messenger.common.finder

import com.bcm.messenger.common.core.Address
import com.google.gson.reflect.TypeToken

/**
 * Created by bcm.social.01 on 2019/4/8.
 * 
 */
interface IBcmFinder {
    /**
     * 
     * return finder type #BcmFinderType
     */
    fun type(): BcmFinderType

    /**
     * 
     */
    fun find(key:String): IBcmFindResult

    /**
     * address
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
     * 
     */
    fun cancel()

}