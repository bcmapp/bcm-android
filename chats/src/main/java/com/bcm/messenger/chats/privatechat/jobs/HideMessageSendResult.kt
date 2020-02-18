package com.bcm.messenger.chats.privatechat.jobs

import com.bcm.messenger.common.core.Address

data class HideMessageSendResult(val messageType:Long, val address: Address, val succeed:Boolean)