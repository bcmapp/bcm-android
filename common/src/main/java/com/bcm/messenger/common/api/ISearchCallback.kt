package com.bcm.messenger.common.api

import com.bcm.messenger.common.finder.BcmFinderType

/**
 * 最近搜索结果回调
 * Created by wjh on 2019-09-08
 */
interface ISearchCallback {
    fun onSelect(type: BcmFinderType, key: String) //选择结果，key是结果的json形态
    fun onMore(type: BcmFinderType, key: String) //更多选择，key是搜索key
}