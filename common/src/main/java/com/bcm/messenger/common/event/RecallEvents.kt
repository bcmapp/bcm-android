package com.bcm.messenger.common.event

import com.bcm.messenger.common.core.Address

/**
 * 撤回事件
 * Created by zjl on 2018/9/18.
 */
data class ReEditEvent(val address: Address, val messageId: Long, val content: String)

/**
 * 撤回失败事件
 */
data class RecallFailEvent(val uid: String, val isOffline: Boolean)

