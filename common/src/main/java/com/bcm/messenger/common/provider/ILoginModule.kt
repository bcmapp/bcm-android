package com.bcm.messenger.common.provider

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.config.BcmFeatureSupport

/**
 * bcm.social.01 2018/9/20.
 */
interface ILoginModule : IAmeModule {
    /**
     * return true login, false not login
     */
    fun isLogin(): Boolean

    fun isAccountLogin(uid: String): Boolean
    /**
     * return major login account uid
     */
    fun majorUid(): String

    fun minorUidList(): List<String>

    fun registrationId(uid: String): Int

    fun gcmToken(): String?

    fun setGcmToken(token: String?)

    fun isGcmDisabled(): Boolean

    fun signalingKey(uid: String): String?

    fun isSignedPreKeyRegistered(uid: String): Boolean

    fun setSignedPreKeyRegistered(uid: String, registered: Boolean)

    fun getSignedPreKeyFailureCount(uid: String): Int

    fun setSignedPreKeyFailureCount(uid: String, count: Int)

    fun getSignedPreKeyRotationTime(uid: String): Long

    fun setSignedPreKeyRotationTime(uid: String, time: Long)

    fun genTime(uid: String): Long

    /**
     * param: uid
     * return account file dir
     */
    fun accountDir(uid: String): String

    /**
     * load login state
     */
    fun restoreLastLoginState()

    /**
     * quit account
     */
    fun quit(accountContext: AccountContext, clearHistory: Boolean, withLogOut: Boolean = true)

    /**
     * current client function support flag
     */
    fun mySupport(): BcmFeatureSupport

    /**
     * refresh current client function support flag to server
     */
    fun refreshMySupport2Server(accountContext: AccountContext)

    /**
     * continue login
     */
    fun continueLoginSuccess(accountContext: AccountContext)

    /**
     * refresh one time key
     */
    fun refreshPrekeys(accountContext: AccountContext)

    /**
     * refresh signed prekey
     */
    fun rotateSignedPrekey(accountContext: AccountContext)

    /**
     * update stranger message receive flag
     */
    fun updateAllowReceiveStrangers(accountContext: AccountContext, allow: Boolean, callback: ((succeed: Boolean) -> Unit)?)

    fun getAccountContext(uid: String): AccountContext
    fun refreshOfflineToken()
}