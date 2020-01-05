package com.bcm.messenger.common.ui.grouprepository.events

import com.bcm.messenger.common.AccountContext

class GroupKeyRefreshCompleteEvent(val accountContext: AccountContext, val gid:Long, val mid:Long, val from:String, val version:Long) {
}