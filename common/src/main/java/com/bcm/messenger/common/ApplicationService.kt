package com.bcm.messenger.common

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.utility.AppContextHolder

class ApplicationService : Service() {
    companion object {
        const val TAG = "ApplicationService"
        var impl: IApplicationlImpl? = null
    }

    private val mBinder = object : IApplicationlImpl.Stub() {
        override fun isScreenSecurityEnabled(): Boolean {
            return TextSecurePreferences.isScreenSecurityEnabled(AMELogin.majorContext)
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return mBinder
    }
}