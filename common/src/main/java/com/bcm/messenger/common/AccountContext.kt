package com.bcm.messenger.common

import android.os.Parcel
import android.os.Parcelable
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.utility.proguard.NotGuard

class AccountContext(val uid: String, val token: String, val password: String) : Parcelable, Comparable<AccountContext>, NotGuard {
    override fun compareTo(other: AccountContext): Int {
        return uid.compareTo(other.uid)
    }

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


    val isPushRegistered: Boolean get() = AmeModuleCenter.login().isPushRegistered(uid)
}