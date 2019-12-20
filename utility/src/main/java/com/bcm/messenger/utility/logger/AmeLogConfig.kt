package com.bcm.messenger.utility.logger

import android.content.Context
import android.util.Log
import com.bcm.messenger.utility.ProcessUtil
import com.orhanobut.logger.Logger
import java.io.File


/**
 * bcm.social.01 2018/6/21.
 */
object AmeLogConfig {
    var logDir: String? = ""

    fun setLog(context: Context, level: Int, logDir: String, logFileCount: Int, callback: () -> Unit) {
        try {
            val dir = File(logDir)
            if (!dir.exists()) {
                dir.mkdirs()
            }

            AmeLogConfig.logDir = logDir
            val logStrategy = if (ProcessUtil.isMainProcess(context)) {
                TextFileWriteHandler.buildDiskLogStrategy(context, logDir, logFileCount, callback)
            } else {
                null
            }

            val cvsFormatStrategy = TextFormatStrategy.newBuilder()
                    .logStrategy(logStrategy)
                    .tag("BCM")
                    .build()
            Logger.addLogAdapter(AmeDiskLoggerAdapter(cvsFormatStrategy, level))
        } catch ( e:Throwable) {
            Log.e("AmeLogConfig", "log init failed", e)
        }
    }
}