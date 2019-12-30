package com.bcm.messenger.common.provider

import com.bcm.messenger.common.config.BcmFeatureSupport
import com.bcm.messenger.common.event.ServiceConnectEvent

/**
 * bcm.social.01 2018/9/20.
 */
interface ILoginModule: IAmeModule {
    /**
     * return true login, false not login
     */
    fun isLogin(): Boolean

    /**
     * return current login account uid
     */
    fun loginUid(): String

    fun registrationId(): Int

    fun gcmToken(): String?

    fun setGcmToken(token: String?)

    fun gcmTokenLastSetTime(): Long

    fun setGcmTokenLastSetTime(time: Long)

    fun isGcmDisabled(): Boolean

    fun isPushRegistered(): Boolean

    fun signalingKey(): String?

    fun isSignedPreKeyRegistered(): Boolean

    fun setSignedPreKeyRegistered(registered: Boolean)

    fun getSignedPreKeyFailureCount(): Int

    fun setSignedPreKeyFailureCount(count: Int)

    fun getSignedPreKeyRotationTime(): Long

    fun setSignedPreKeyRotationTime(time: Long)

    fun genTime(): Long
    /**
     * return account token
     */
    fun token(): String

    /**
     * param: uid
     * return account file dir
     */
    fun accountDir(uid: String): String

    /**
     * load login state
     */
    fun checkLoginAccountState()

    /**
     * quit account
     */
    fun quit(clearHistory: Boolean, withLogOut: Boolean = true)

    /**
     * clear login data
     */
    fun clearAll()

    /**
     * auth password
     */
    fun authPassword(): String

    /**
     * server connection state
     */
    fun serviceConnectedState(): ServiceConnectEvent.STATE

    /**
     * current client function support flag
     */
    fun mySupport(): BcmFeatureSupport

    /**
     * refresh current client function support flag to server
     */
    fun refreshMySupport2Server()

    /**
     * continue login
     */
    fun continueLoginSuccess()

    /**
     * refresh one time key
     */
    fun refreshPrekeys()

    /**
     * refresh signed prekey
     */
    fun rotateSignedPrekey()

    /**
     * update stranger message receive flag
     */
    fun updateAllowReceiveStrangers(allow: Boolean, callback: ((succeed: Boolean) -> Unit)?)


}