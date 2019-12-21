package com.bcm.messenger.common.finder

/**
 * Created by bcm.social.01 on 2019/4/8.
 * 
 */
interface IBcmFindResult {
    /**
     * position
     * @param position 
     */
    fun get(position:Int) : BcmFindData<*>?

    /**
     * @return 
     */
    fun count() :Int

    /**
     * @ n 
     */
    fun topN(n: Int) : List<BcmFindData<*>>

    /**
     * 
     * @return 
     */
    fun toList() : List<BcmFindData<*>>

}