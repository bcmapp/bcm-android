package com.bcm.messenger.common.finder

/**
 * Created by bcm.social.01 on 2019/4/8.
 * 检索结果存储
 */
interface IBcmFindResult {
    /**
     * 取出索引为position的结果
     * @param position 结果索引
     */
    fun get(position:Int) : BcmFindData<*>?

    /**
     * @return 匹配数量
     */
    fun count() :Int

    /**
     * @返回结果的前 n 条记录
     */
    fun topN(n: Int) : List<BcmFindData<*>>

    /**
     * 拿取结果列表
     * @return 结果列表
     */
    fun toList() : List<BcmFindData<*>>

}