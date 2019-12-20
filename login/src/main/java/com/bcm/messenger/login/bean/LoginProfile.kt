package com.bcm.messenger.login.bean

import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.proguard.NotGuard
import org.whispersystems.signalservice.api.profiles.ISignalProfile
import org.whispersystems.signalservice.internal.util.Base64

class LoginProfile : ISignalProfile, NotGuard {

    override fun getEncryptPhone(): String? {
        return null
    }

    override fun getEncryptPubkey(): String? {
        return null
    }

    override fun getEncryptName(): String? {
        return null
    }

    override fun getEncryptAvatarLD(): String? {
        return null
    }

    override fun getEncryptAvatarHD(): String? {
        return null
    }

    override fun getSupportFeatures(): String? {
        return null
    }

    override fun getName(): String? {
        return nickname
    }

    override fun getAvatar(): String? {
        return loginAvatar
    }

    override fun getIdentityKey(): String? {
        return null
    }

    override fun getProfileKey(): String? {
        return loginProfileKey
    }

    override fun getPhone(): String? {
        return e164number
    }

    override fun getProfileKeyArray(): ByteArray? {
        try {
            return Base64.decode(loginProfileKey ?: return null)
        } catch (ex: Exception) {
            ALog.e("LoginProfile", "getProfileKeyArray error", ex)
        }
        return null
    }

    override fun getProfileBackupTime(): Long {
        return backupTime
    }

    override fun isAllowStrangerMessages(): Boolean {
        return false
    }

    var countryCode: String? = null
    var countryName: String? = null
    var e164number: String? = null

    var nickname: String? = null
    var openId: String? = null
    var privateKey: String? = null
    var loginAvatar: String? = null
    var loginProfileKey: String? = null
    var publicKey: String? = null
    var sign: String? = null
    var canEditOpenId: Boolean = false
    var version: Int = 1
    var keyGenTime:Long = 0
    var backupTime:Long = 0
    var loginMode: Int = AmeAccountData.ACCOUNT_MODE_NORMAL

    override fun toString(): String {
        return "LoginProfile(countryCode=$countryCode, countryName=$countryName, e164number=$e164number, nickname=$nickname, openId=$openId, privateKey=$privateKey, loginAvatar=$loginAvatar, loginProfileKey=$loginProfileKey, publicKey=$publicKey, sign=$sign, canEditOpenId=$canEditOpenId, version=$version, keyGenTime=$keyGenTime, backupTime=$backupTime, loginMode=$loginMode)"
    }

}