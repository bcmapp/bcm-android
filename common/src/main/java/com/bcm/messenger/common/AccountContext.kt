package com.bcm.messenger.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.utility.proguard.NotGuard
import com.bcm.route.api.BcmRouterIntent

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
}

private fun checkIntent(accountContext: AccountContext, intent: Intent) {
    if (!intent.hasExtra(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT)) {
        intent.putExtra(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContext)
    }
}

private fun checkBundle(accountContext: AccountContext, bundle: Bundle) {
    if (bundle.getParcelable<AccountContext>(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT) == null) {
        bundle.putParcelable(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContext)
    }
}

fun Activity.startBcmActivity(accountContext: AccountContext, intent: Intent) {
    checkIntent(accountContext, intent)
    startActivity(intent)
}

fun Activity.startBcmActivityForResult(accountContext: AccountContext, intent: Intent, requestCode: Int) {
    checkIntent(accountContext, intent)
    startActivityForResult(intent, requestCode)
}

fun Activity.startBcmActivityForResult(accountContext: AccountContext, intent: Intent, requestCode: Int, options: Bundle?) {
    checkIntent(accountContext, intent)
    startActivityForResult(intent, requestCode, options)
}

fun Context.startBcmActivity(accountContext: AccountContext, intent: Intent) {
    checkIntent(accountContext, intent)
    startActivity(intent)
}

fun Context.startBcmActivity(accountContext: AccountContext, intent: Intent, options: Bundle?) {
    checkIntent(accountContext, intent)
    startActivity(intent, options)
}

fun BcmRouterIntent.startBcmActivity(accountContext: AccountContext) {
    putParcelable(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContext)
    navigation()
}

fun BcmRouterIntent.startBcmActivity(accountContext: AccountContext, context: Context) {
    putParcelable(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContext)
    navigation(context)
}

fun BcmRouterIntent.startBcmActivity(accountContext: AccountContext, activity: Activity, requestCode: Int) {
    putParcelable(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContext)
    navigation(activity, requestCode)
}

fun <T> BcmRouterIntent.navigationWithAccountContext(accountContext: AccountContext): T {
    putParcelable(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContext)
    return navigationWithCast<T>()
}
