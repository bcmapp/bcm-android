package com.bcm.messenger.common.profiles

import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.proguard.NotGuard
import com.google.gson.annotations.SerializedName

/**
 * 
 *
 * Created by Kin on 2018/9/27
 */
class ProfilePrivacy : NotGuard {
    @SerializedName("stranger")
    var allowStrangerMessage = false

    override fun toString(): String {
        return GsonUtils.toJson(this)
    }
}