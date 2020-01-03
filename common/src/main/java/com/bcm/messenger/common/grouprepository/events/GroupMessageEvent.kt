package com.bcm.messenger.common.grouprepository.events

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail

class GroupMessageEvent(val accountContext: AccountContext, val message: AmeGroupMessageDetail) {
}