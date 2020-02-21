package com.bcm.messenger.common

import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.crypto.AccountMasterSecret
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.LoginRecipient
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.proguard.NotGuard
import java.io.Serializable

class AccountContext(val uid: String, val token: String, val password: String) : Serializable, Comparable<AccountContext>, NotGuard {
    companion object {
        private const val serialVersionUID = 1L
    }

    val name get() = AmeModuleCenter.login().name(uid)
    val isLogin get() = AmeModuleCenter.login().isAccountLogin(uid)
    val accountDir: String get() = AmeModuleCenter.login().accountDir(uid)
    val genTime get() = AmeModuleCenter.login().genTime(uid)
    val registrationId: Int get() = AmeModuleCenter.login().registrationId(uid)
    val signalingKey: String get() = AmeModuleCenter.login().signalingKey(uid) ?: ""
    val tag get() = masterSecret?.tag ?: "NLogin"

    val masterSecret: MasterSecret?
        get() {
            return AccountMasterSecret.get(this)
        }

    val recipient get() = LoginRecipient.get(this)

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

    override fun hashCode(): Int {
        var result = uid.hashCode()
        result = 31 * result + token.hashCode()
        result = 31 * result + password.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AccountContext

        if (uid != other.uid) return false
        if (token != other.token) return false
        if (password != other.password) return false

        return true
    }

    override fun toString(): String {
        return "$uid\\_$token"
    }
}

