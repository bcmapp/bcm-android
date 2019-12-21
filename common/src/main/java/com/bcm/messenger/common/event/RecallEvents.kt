package com.bcm.messenger.common.event

import com.bcm.messenger.common.core.Address

/**
 * 
 * Created by zjl on 2018/9/18.
 */
data class ReEditEvent(val address: Address, val messageId: Long, val content: String)

/**
 * 
 */
data class RecallFailEvent(val uid: String, val isOffline: Boolean)

