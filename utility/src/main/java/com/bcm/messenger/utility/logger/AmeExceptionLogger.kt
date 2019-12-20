package com.bcm.messenger.utility.logger

/**
 * Created by bcm.social.01 on 2018/12/6.
 */
class AmeExceptionLogger:Thread.UncaughtExceptionHandler {
    private var originHandler: Thread.UncaughtExceptionHandler? = null

    fun initLogger() {
        originHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(t: Thread?, e: Throwable?) {
        try {
            ALog.e("AmeExceptionLogger", e)
        } catch (e: Throwable) {}
        originHandler?.uncaughtException(t, e)
    }
}