package com.bcm.messenger.utility

import android.os.SystemClock
import com.bcm.messenger.utility.logger.ALog
import kotlin.math.abs

/**
 * Created by bcm.social.01 on 2018/11/12.
 */
object AmeTimeUtil {
    private var serverTime = 0L
    private var markSystemRunTime = 0L
    private var lastMessageSendTime = 0L

    fun updateServerTimeMillis(time:Long){
        if (time > 0){
            if (serverTime == 0L || abs(time - serverTimeMillis()) >= 300){
                serverTime = time
                markSystemRunTime = SystemClock.elapsedRealtime()

                ALog.i("AmeTimeUtil", "server time adjust $time local:${System.currentTimeMillis()}")
            } else {
                ALog.i("AmeTimeUtil", "server time ignore $time server time:$serverTime")
            }
        }
    }

    @Synchronized
    fun getMessageSendTime(): Long{
        var time = serverTimeMillis()
        if (time <= lastMessageSendTime) {
            time = ++lastMessageSendTime
        } else {
            lastMessageSendTime = time
        }
        return time
    }

    fun serverTimeMillis():Long {
        if (serverTime == 0L){
            return localTimeMillis()
        }
        return serverTime + (SystemClock.elapsedRealtime() - markSystemRunTime)
    }

    fun localTimeMillis():Long {
        return System.currentTimeMillis()
    }

    fun localTimeSecond(): Long{
        return localTimeMillis() /1000
    }
}