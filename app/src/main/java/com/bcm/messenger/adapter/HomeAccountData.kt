package com.bcm.messenger.adapter

import com.bcm.messenger.login.bean.AmeAccountData

/**
 * Created by Kin on 2019/12/31
 */

const val TYPE_ADD = 0
const val TYPE_ONLINE = 1
const val TYPE_OFFLINE = 2

data class HomeAccountItem(val type: Int, val account: AmeAccountData)