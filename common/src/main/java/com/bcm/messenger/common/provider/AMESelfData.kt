package com.bcm.messenger.common.provider

import com.bcm.messenger.common.ARouterConstants



/**
 * 
 */
object AMESelfData {

    private val loginProvider: ILoginModule = AmeProvider.get<ILoginModule>(ARouterConstants.Provider.PROVIDER_LOGIN_BASE)!!
    val isLogin get() = loginProvider.isLogin()
    val isPinEnable get() = loginProvider.isPinEnable()
    val uid get() = loginProvider.loginUid()
    val authPassword get() = loginProvider.authPassword()
    val token get() = loginProvider.token()
    val mySupport get() = loginProvider.mySupport()
    val accountDir: String get() = loginProvider.accountDir(uid)
    val ameDir: String get() = loginProvider.accountDir("BCM")
    val genTime get() = loginProvider.genTime()

    val registrationId: Int get() = loginProvider.registrationId()
    var gcmTokenLastSetTime: Long
        set(value) {
            loginProvider.setGcmTokenLastSetTime(value)
        }
        get() {
            return loginProvider.gcmTokenLastSetTime()
        }
    var gcmToken: String?
        set(value) {
            loginProvider.setGcmToken(value)
        }
        get() {
            return loginProvider.gcmToken()
        }
    val isGcmDisabled: Boolean get() = loginProvider.isGcmDisabled()
    val isPushRegistered: Boolean get() = loginProvider.isPushRegistered()
    val signalingKey: String get() = loginProvider.signalingKey()?:""
    var isSignedPreKeyRegistered: Boolean
        set(value) {
            loginProvider.setSignedPreKeyRegistered(value)
        }
        get() {
            return loginProvider.isSignedPreKeyRegistered()
        }

    var signedPreKeyFailureCount: Int
        set(value) {
            loginProvider.setSignedPreKeyFailureCount(value)
        }
        get() {
            return loginProvider.getSignedPreKeyFailureCount()
        }

    var signedPreKeyRotationTime: Long
        set(value) {
            loginProvider.setSignedPreKeyRotationTime(value)
        }
        get() {
            return loginProvider.getSignedPreKeyRotationTime()
        }

}