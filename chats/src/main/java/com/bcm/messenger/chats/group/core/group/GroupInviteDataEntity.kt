package com.bcm.messenger.chats.group.core.group

import com.bcm.messenger.utility.proguard.NotGuard


class GroupInviteDataEntity(val gid:Long, val uid:String, val timestamp: Long, val name:String?): NotGuard {
}