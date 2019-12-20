package com.bcm.messenger.utility

import android.app.ActivityManager
import android.content.Context
import android.os.Process

object ProcessUtil {
    fun isMainProcess(context:Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val processInfos = am?.runningAppProcesses?.toList()
        val mainProcessName = context.packageName;
        val myPid = Process.myPid()
        if (null != processInfos) {
            for (info in processInfos) {
                if (info.pid == myPid && mainProcessName == info.processName) {
                    return true
                }
            }
        }
        return false
    }
}