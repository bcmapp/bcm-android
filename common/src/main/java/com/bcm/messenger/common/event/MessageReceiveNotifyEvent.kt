package com.bcm.messenger.common.event

import com.bcm.messenger.common.AccountContext

/**
 * 
 * Created by wjh on 2019-08-22
 */
class MessageReceiveNotifyEvent(val accountContext: AccountContext, val source: String, val threadId: Long) {
}