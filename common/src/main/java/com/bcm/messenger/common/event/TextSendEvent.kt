package com.bcm.messenger.common.event

import com.bcm.messenger.common.AccountContext

/**
 *
 */
class TextSendEvent(val accountContext: AccountContext, val messageId: Long, val success: Boolean, val details: String)
