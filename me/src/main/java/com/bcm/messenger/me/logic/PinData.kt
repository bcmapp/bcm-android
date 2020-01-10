package com.bcm.messenger.me.logic

import android.text.TextUtils
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.logger.ALog
import com.google.gson.JsonParseException

class PinData {
    companion object {
        //app lock time
        const val APP_LOCK_5_MIN = 5
        const val APP_LOCK_INSTANTLY = 0
        const val APP_LOCK_ONE_HOUR = 60

        fun fromString(pinString:String): PinData {
            if (pinString.isEmpty()) {
                return PinData()
            }

            return try {
                GsonUtils.fromJson(pinString, PinData::class.java)
                        .takeIf { !TextUtils.isEmpty(it.pin) && it.lengthOfPin > 0 }?:PinData()
            } catch (e:JsonParseException) {
                ALog.e("PinData", "failed", e)
                PinData()
            }
        }
    }

    var pin = ""
    var lengthOfPin: Int = 0
    var pinLockTime: Int = APP_LOCK_5_MIN //pin lock enable time(app background run time)
    var enableFingerprint: Boolean = false

    override fun toString(): String {
        return GsonUtils.toJson(this)
    }
}