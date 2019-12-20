package com.bcm.messenger.chats.group.core.group

import com.bcm.messenger.utility.proguard.NotGuard
import com.google.gson.annotations.SerializedName

/**
 * bcm.social.01 2019/3/18.
 */
class GroupAckState(val gid: Long,
                    @SerializedName("last_mid") val lastMid: Long,
                    @SerializedName("last_ack_mid") val lastAckMid: Long) : NotGuard {
    /*
     {
    "gid":
    "lastMid":
    "lastAckMid":
    }*/
}