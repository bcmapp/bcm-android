package com.bcm.messenger.common

import android.os.Parcel
import android.os.Parcelable
import com.bcm.messenger.utility.proguard.NotGuard

class AccountContext(val uid: String) : Parcelable, Comparable<AccountContext>, NotGuard {
    override fun compareTo(other: AccountContext): Int {
        return uid.compareTo(other.uid)
    }

    constructor(parcel: Parcel) : this(parcel.readString() ?: "")

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(uid)
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
}