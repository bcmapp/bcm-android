package com.bcm.messenger.common

import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.crypto.MasterSecretUtil
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.proguard.NotGuard
import java.io.Serializable

class AccountContext(val uid: String, val token: String, val password: String) : Serializable,  Comparable<AccountContext>, NotGuard {
    companion object {
        private const val serialVersionUID = 1L
    }

    @Transient
    private  var  masterSecretObj:MasterSecret? = null
    val isLogin get() = AmeModuleCenter.login().isAccountLogin(uid)
    val accountDir: String get() = AmeModuleCenter.login().accountDir(uid)
    val genTime get() = AmeModuleCenter.login().genTime(uid)
    val registrationId: Int get() = AmeModuleCenter.login().registrationId(uid)
    val signalingKey: String get() = AmeModuleCenter.login().signalingKey(uid) ?: ""

    val masterSecret:MasterSecret? get()  {
        if (!isLogin) {
            ALog.e("AccountContext", "account not login")
            return null
        }

        val masterSecret = this.masterSecretObj
        if (masterSecret != null) {
            return masterSecret
        }

        synchronized(AccountContext::class) {
            var masterSecret1 = this.masterSecretObj
            if (masterSecret1 != null) {
                return masterSecret1
            }
            masterSecret1 = MasterSecretUtil.getMasterSecret(this, MasterSecretUtil.UNENCRYPTED_PASSPHRASE)
            this.masterSecretObj = masterSecret1
            return masterSecret1
        }
    }

    var isSignedPreKeyRegistered: Boolean
        set(value) {
            AmeModuleCenter.login().setSignedPreKeyRegistered(uid, value)
        }
        get() {
            return AmeModuleCenter.login().isSignedPreKeyRegistered(uid)
        }

    var signedPreKeyFailureCount: Int
        set(value) {
            AmeModuleCenter.login().setSignedPreKeyFailureCount(uid, value)
        }
        get() {
            return AmeModuleCenter.login().getSignedPreKeyFailureCount(uid)
        }

    var signedPreKeyRotationTime: Long
        set(value) {
            AmeModuleCenter.login().setSignedPreKeyRotationTime(uid, value)
        }
        get() {
            return AmeModuleCenter.login().getSignedPreKeyRotationTime(uid)
        }

    override fun compareTo(other: AccountContext): Int {
        val result = uid.compareTo(other.uid)
        if (result == 0) {
            return token.compareTo(other.token)
        }
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AccountContext

        return uid == other.uid && token == other.token
    }

    override fun hashCode(): Int {
        var result = uid.hashCode()
        result = 31 * result + token.hashCode()
        result = 31 * result + password.hashCode()
        return result
    }

    override fun toString(): String {
        return "$uid\\_$token"
    }
}

