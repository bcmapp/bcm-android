package com.bcm.messenger.login.bean

import com.bcm.messenger.utility.proguard.NotGuard

/**
 * Created by bcm.social.01 on 2018/9/6.
 */
class AmeAccountData : NotGuard {

    var version: Int = V4
    var uid: String = ""
    var name: String = ""
    var avatar: String = ""
    var phone: String = ""
    var priKey: String = ""
    var pubKey: String = ""
    var genKeyTime: Long = 0
    var backupTime: Long = 0
    var lastLoginTime: Long = 0
    var pin: String = ""
    var lengthOfPin: Int = 0
    var pinLockTime: Int = 5 //pin lock enable time(app background run time)
    var enableFingerprint: Boolean = false

    var passwordHint: String = ""
    var mode: Int = ACCOUNT_MODE_NORMAL

    var registrationId: Int = 0
    var gcmDisabled: Boolean = false
    var signalPassword: String = ""
    var signalingKey: String = ""
    var signedPreKeyRegistered: Boolean = false
    var signedPreKeyFailureCount: Int = 0
    var signedPreKeyRotationTime: Long = 0
    @Deprecated("only use for compatible old login state")
    var curLogin = false

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AmeAccountData

        if (uid != other.uid) return false
        if (priKey != other.priKey) return false

        return true
    }

    override fun hashCode(): Int {
        var result = uid.hashCode()
        result = 31 * result + priKey.hashCode()
        return result
    }


    companion object {
        //account version
        const val V2 = 2
        const val V3 = 3
        const val V4 = 4

        const val ACCOUNT_MODE_NORMAL = 0
        const val ACCOUNT_MODE_BACKUP = 1

        const val TAG = "AmeAccountData"
    }
}