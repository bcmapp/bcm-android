package com.bcm.messenger.utility.foreground

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import com.bcm.messenger.utility.IActivityCounter
import com.bcm.messenger.utility.logger.ALog


/**
 * Created by bcm.social.01 on 2018/10/10.
 */
class ActivityCounterService: Service() {
    private val TAG = "ActivityCounterService"
    private val mBinder = object : IActivityCounter.Stub() {
        @Synchronized
        override fun increase():Int {
            return AppForeground.increase()
        }

        @Synchronized
        override fun decrease(): Int {
            return AppForeground.decrease()
        }

        override fun pid(): Int {
            return Process.myPid()
        }

    }

    override fun onBind(intent: Intent): IBinder? {
        return mBinder
    }

    override fun onCreate() {
        super.onCreate()
        ALog.i(TAG, "onCreate")
    }

    override fun onDestroy() {
        super.onDestroy()
        ALog.i(TAG, "onDestroy")
    }
}