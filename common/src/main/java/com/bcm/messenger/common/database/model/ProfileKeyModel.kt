package com.bcm.messenger.common.database.model

import android.text.TextUtils
import com.bcm.messenger.utility.proguard.NotGuard
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.utility.GsonUtils


/**
 * Created by Kin on 2019/4/22
 */
data class ProfileKeyModel(val nickNameKey: String, val avatarKey: String, val version: Int) : NotGuard {
    companion object {
        fun fromKeyConfig(keyConfig:AmeGroupMemberInfo.KeyConfig?) :ProfileKeyModel? {
            if (null == keyConfig || TextUtils.isEmpty(keyConfig.avatarKey)) {
                return null
            }

            return ProfileKeyModel("", keyConfig.avatarKey,  keyConfig.version)
        }
    }

    override fun toString(): String {
        return GsonUtils.toJson(this)
    }
}