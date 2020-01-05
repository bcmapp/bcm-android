package com.bcm.messenger.common.grouprepository.events

import com.bcm.messenger.common.AccountContext

class GroupMessageMissedEvent(val accountContext: AccountContext, val gid:Long, val fromMid:Long, val toMid:Long) {
}