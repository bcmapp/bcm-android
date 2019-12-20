package com.bcm.messenger.push

import android.content.Context
import android.content.Intent
import android.text.TextUtils
import com.bcm.messenger.common.utils.AmePushProcess
import com.bcm.messenger.utility.logger.ALog
import com.umeng.message.UmengIntentService
import com.umeng.message.entity.UMessage
import org.android.agoo.common.AgooConstants
import org.json.JSONObject

class BcmUmengIntentService: UmengIntentService() {
    override fun onMessage(p0: Context?, intent: Intent?) {
        super.onMessage(p0, intent)
        ALog.i("BcmUmengIntentService", "recv push")

        if (null != intent) {
            val message = intent.getStringExtra(AgooConstants.MESSAGE_BODY)
            try {
                val msg = UMessage(JSONObject(message))
                if (msg.extra != null) {
                    ALog.d("BcmUmengIntentService", "recv push, message is " + msg.text)
                    ALog.i("BcmUmengIntentService", "recv push, message is empty? " + TextUtils.isEmpty(msg.text))
                    val bcmData = String.format("{\"bcmdata\":%s}", msg.extra["bcmdata"])
                    AmePushProcess.processPush(bcmData)
                } else {
                    AmePushProcess.processPush(msg.text)
                    ALog.e("BcmUmengIntentService", "unknown notification")
                }
            } catch (e: Exception) {
                ALog.e("BcmUmengIntentService", e)
            }
        }
    }
}