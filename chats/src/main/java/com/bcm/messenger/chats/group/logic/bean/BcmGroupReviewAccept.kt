package com.bcm.messenger.chats.group.logic.bean

import com.bcm.messenger.utility.proguard.NotGuard


class BcmGroupReviewAccept(val uid:String, val accepted:Boolean, val group_info_secret:String, val inviter:String, val pwd:String?, val proof:String?): NotGuard {
}