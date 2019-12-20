package com.bcm.messenger.chats.group.core.group

import com.bcm.messenger.chats.group.logic.secure.GroupKeysContent
import com.bcm.messenger.utility.proguard.NotGuard
import com.google.gson.annotations.SerializedName

class LatestGroupKeyResEntity:NotGuard {
    @SerializedName("keys")
    var keys:List<GroupKeysContent.GroupKeyContent>? = null
}