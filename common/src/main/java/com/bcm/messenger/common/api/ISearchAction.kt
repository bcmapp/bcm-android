package com.bcm.messenger.common.api

import com.bcm.messenger.common.finder.BcmFinderType

/**
 *
 * Created by wjh on 2019-09-08
 */
interface ISearchAction {
    fun setKeyword(keyword: String, searchLimit: Boolean)
}