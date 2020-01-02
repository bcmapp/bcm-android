package com.bcm.messenger.common.provider

import com.bcm.messenger.common.AccountContext


/**
 *
 */
object AMELogin {
    val majorUid get() = AmeModuleCenter.login().majorUid()
    val isLogin get() = AmeModuleCenter.login().isLogin()
    val majorContext = AmeModuleCenter.login().getAccountContext(majorUid)

    fun accountContext(uid:String): AccountContext {
        return AmeModuleCenter.login().getAccountContext(uid)
    }

    val mySupport get() = AmeModuleCenter.login().mySupport()
    var gcmTokenLastSetTime: Long
        set(value) {
            AmeModuleCenter.login().setGcmTokenLastSetTime(value)
        }
        get() {
            return AmeModuleCenter.login().gcmTokenLastSetTime()
        }
    var gcmToken: String?
        set(value) {
            AmeModuleCenter.login().setGcmToken(value)
        }
        get() {
            return AmeModuleCenter.login().gcmToken()
        }
    val isGcmDisabled: Boolean get() = AmeModuleCenter.login().isGcmDisabled()
}