package com.bcm.messenger.common.expiration

import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.utility.AppContextHolder

object ExpirationManager {
    private var expiringScheduler:IExpiringScheduler? = null
    private val ignoreExpiringScheduler = IgnoreExpiringScheduler()

    fun scheduler():IExpiringScheduler {
        if (AMESelfData.isLogin) {
            val scheduler = this.expiringScheduler
            if (scheduler?.getUid() == AMESelfData.uid) {
                return scheduler
            }

            synchronized(this) {
                val scheduler1 = this.expiringScheduler
                if (scheduler1?.getUid() == AMESelfData.uid) {
                    return scheduler1
                }

                val scheduler2 = ExpiringScheduler(AppContextHolder.APP_CONTEXT, AMESelfData.uid)
                this.expiringScheduler = scheduler2
                return scheduler2
            }
        }
        return  ignoreExpiringScheduler
    }
}