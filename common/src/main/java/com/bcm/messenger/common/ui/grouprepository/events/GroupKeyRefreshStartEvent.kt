package com.bcm.messenger.common.ui.grouprepository.events

import com.bcm.messenger.common.AccountContext

class GroupKeyRefreshStartEvent(val accountContext: AccountContext, val gid:Long, val mid:Long, val from:String, val mode:Int) {
}