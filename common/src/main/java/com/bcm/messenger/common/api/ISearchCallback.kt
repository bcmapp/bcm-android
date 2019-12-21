package com.bcm.messenger.common.api

import com.bcm.messenger.common.finder.BcmFinderType

/**
 * 
 * Created by wjh on 2019-09-08
 */
interface ISearchCallback {
    fun onSelect(type: BcmFinderType, key: String) //，keyjson
    fun onMore(type: BcmFinderType, key: String) //，keykey
}