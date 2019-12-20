package com.bcm.messenger.chats.group.core.group

import com.bcm.messenger.utility.proguard.NotGuard
import com.google.gson.annotations.SerializedName

class RefreshKeyResEntity: NotGuard {
    @SerializedName("success")
    var succeed:List<Long>? = null
    @SerializedName("fail")
    var failed:List<Long>? = null

}