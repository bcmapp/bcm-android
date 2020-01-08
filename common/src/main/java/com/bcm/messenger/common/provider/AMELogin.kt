package com.bcm.messenger.common.provider

import com.bcm.messenger.common.AccountContext


/**
 *
 */
object AMELogin {
    private val login = AmeModuleCenter.login()
    val majorUid get() = login.majorUid()
    val isLogin get() = login.isLogin()
    val majorContext get() = login.getAccountContext(majorUid)

    fun accountContext(uid:String): AccountContext {
        return login.getAccountContext(uid)
    }

    val mySupport get() = login.mySupport()
    var gcmToken: String?
        set(value) {
            login.setGcmToken(value)
        }
        get() {
            return login.gcmToken()
        }
    val isGcmDisabled: Boolean get() = login.isGcmDisabled()
}