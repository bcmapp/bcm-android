package com.bcm.messenger.utility.logger

/**
 * bcm.social.01 2018/12/6.
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