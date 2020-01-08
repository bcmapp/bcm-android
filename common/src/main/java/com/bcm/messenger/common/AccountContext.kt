package com.bcm.messenger.common

import android.os.Parcel
import android.os.Parcelable
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.utility.proguard.NotGuard

class AccountContext(val uid: String, val token: String, val password: String) : Parcelable, Comparable<AccountContext>, NotGuard {

    constructor(parcel: Parcel) : this(parcel.readString() ?: "", parcel.readString()
            ?: "", parcel.readString() ?: "")

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(uid)
        parcel.writeString(token)
        parcel.writeString(password)
    }

    override fun describeContents(): Int {
        return 0
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

        if (uid != other.uid) return false
        if (token != other.token) return false
        return uid == other.uid && token == other.token
    }

    override fun hashCode(): Int {
        return uid.hashCode()
    }


    companion object CREATOR : Parcelable.Creator<AccountContext> {
        override fun createFromParcel(parcel: Parcel): AccountContext {
            return AccountContext(parcel)
        }

        override fun newArray(size: Int): Array<AccountContext?> {
            return arrayOfNulls(size)
        }
    }

    val isLogin get() = AmeModuleCenter.login().isAccountLogin(uid)
    val accountDir: String get() = AmeModuleCenter.login().accountDir(uid)
    val genTime get() = AmeModuleCenter.login().genTime(uid)

    val registrationId: Int get() = AmeModuleCenter.login().registrationId(uid)
    val signalingKey: String get() = AmeModuleCenter.login().signalingKey(uid) ?: ""
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
}

