package com.bcm.messenger.utility.logger

import android.util.Log
import com.orhanobut.logger.DiskLogAdapter
import com.orhanobut.logger.FormatStrategy
import com.orhanobut.logger.Logger

/**
 * bcm.social.01 2018/6/15.
 */
class AmeDiskLoggerAdapter(formatStrategy: FormatStrategy, private val level: Int) : DiskLogAdapter(formatStrategy) {
    override fun isLoggable(priority: Int, tag: String?): Boolean {
        return priority >= level
    }

    override fun log(priority: Int, tag: String?, message: String?) {
        val nonNullTag = tag?:"DEFAULT"
        when(priority){
            Logger.VERBOSE -> Log.v(nonNullTag, message)
            Logger.DEBUG -> Log.d(nonNullTag, message)
            Logger.INFO -> Log.i(nonNullTag, message)
            Logger.WARN -> Log.w(nonNullTag, message)
            Logger.ERROR -> Log.e(nonNullTag, message)
        }
        super.log(priority, nonNullTag, message)
    }

}