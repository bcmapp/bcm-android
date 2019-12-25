/**
 * Copyright (C) 2014 Open Whisper Systems
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.bcm.messenger.common.jobs;

import com.bcm.messenger.common.gcm.FcmUtil
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.utils.PushUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability


object GcmRefresh {
    private const val TAG = "GcmRefresh"

    fun refresh() {
        if (AMELogin.isGcmDisabled) {
            ALog.i(TAG, "gcm is Disable, onRun return")
            return
        }

        AmeDispatcher.io.dispatch({
            ALog.i(TAG, "GcmRefreshJob onRun...");
            val result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(AppContextHolder.APP_CONTEXT);
            if (result == ConnectionResult.SUCCESS) {
                val gcmToken = FcmUtil.getToken()
                if (gcmToken.isPresent) {
                    val oldToken = AMELogin.gcmToken
                    if (gcmToken.get() != oldToken) {
                        val oldLength = oldToken?.length ?: 0
                        ALog.i(TAG, "Token changed. oldLength: " + oldLength + "  newLength: " + gcmToken.get().length)
                    } else {
                        ALog.i(TAG, "Token didn't change.");
                    }

                    if (AMELogin.isLogin) {
                        PushUtil.registerPush(gcmToken.get())
                    }

                    AMELogin.gcmToken = gcmToken.get()
                    AMELogin.gcmTokenLastSetTime = System.currentTimeMillis()
                }
            }
        }, 2000)
    }
}
