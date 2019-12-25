package com.bcm.messenger.common.expiration

import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.utility.AppContextHolder

object ExpirationManager {
    private var expiringScheduler:IExpiringScheduler? = null
    private val ignoreExpiringScheduler = IgnoreExpiringScheduler()

    fun scheduler():IExpiringScheduler {
        if (AMELogin.isLogin) {
            val scheduler = this.expiringScheduler
            if (scheduler?.getUid() == AMELogin.uid) {
                return scheduler
            }

            synchronized(this) {
                val scheduler1 = this.expiringScheduler
                if (scheduler1?.getUid() == AMELogin.uid) {
                    return scheduler1
                }

                val scheduler2 = ExpiringScheduler(AppContextHolder.APP_CONTEXT, AMELogin.uid)
                this.expiringScheduler = scheduler2
                return scheduler2
            }
        }
        return  ignoreExpiringScheduler
    }
}