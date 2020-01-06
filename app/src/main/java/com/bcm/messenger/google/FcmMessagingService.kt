package com.bcm.messenger.google

import android.content.Context
import com.bcm.messenger.common.jobs.GcmRefresh
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.utils.AmePushProcess
import com.bcm.messenger.utility.logger.ALog
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.*

class FcmMessagingService : FirebaseMessagingService() {
    companion object {
        private const val TAG = "FCM"
        private const val PAYLOAD_KEY = "bcmdata"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage?) {
        if (remoteMessage == null) {
            ALog.w(TAG, "onMessageReceived null")
            return
        }
        // Check if messageDetail contains a data payload.
        if (remoteMessage.data.isNotEmpty()) {
            ALog.d(TAG, "FCM Message data payload: ${remoteMessage.data}")
            onReceive(this, remoteMessage)
        }

        // Check if messageDetail contains a notification payload.
        if (remoteMessage.notification != null) {
            ALog.d(TAG, "FCM Message Notification title: ${remoteMessage.notification?.title}, body: ${remoteMessage.notification?.body}")
        }
    }

    override fun onNewToken(token: String?) {
        if (token == null) {
            ALog.w(TAG, "onNewToken null")
            return
        }
        if (!AMELogin.isLogin) {
            ALog.i(TAG, "Got a new FCM token, but the user isn't registered.")
            return
        }
        ALog.i(TAG, "onNewToken")
        GcmRefresh.refresh()
    }

    fun onReceive(context: Context, msg: RemoteMessage) {
        ALog.i(TAG, "FCM message...")

        if (!TextSecurePreferences.isPushRegistered(context)) {
            ALog.w(TAG, "Not push registered!")
            return
        }

        if (msg.data.containsKey(PAYLOAD_KEY)) {
            try {
                val bcmData = String.format(Locale.US, "{\"%s\":%s}", PAYLOAD_KEY, msg.data[PAYLOAD_KEY])
                ALog.d(TAG, "FCM message...$bcmData")

                handleReceivedBCMNotify(bcmData)
            } catch (e: Throwable) {
                ALog.e(TAG, "Decode source failed", e)
            }

        }
    }

    private fun handleReceivedBCMNotify(bcmdata: String) {
        AmePushProcess.processPush(bcmdata)
    }
}