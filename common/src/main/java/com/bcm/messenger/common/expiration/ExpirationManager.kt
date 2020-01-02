package com.bcm.messenger.common.expiration

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.utility.AppContextHolder

object ExpirationManager {
    private var expiringScheduler:IExpiringScheduler? = null
    private val ignoreExpiringScheduler = IgnoreExpiringScheduler()

    fun scheduler(accountContext:AccountContext):IExpiringScheduler {
        //todo wangshuhe
        if (accountContext.isLogin) {
            val scheduler = this.expiringScheduler
            if (scheduler?.getUid() == accountContext.uid) {
                return scheduler
            }

            synchronized(this) {
                val scheduler1 = this.expiringScheduler
                if (scheduler1?.getUid() == accountContext.uid) {
                    return scheduler1
                }

                val scheduler2 = ExpiringScheduler(AppContextHolder.APP_CONTEXT, accountContext)
                this.expiringScheduler = scheduler2
                return scheduler2
            }
        }
        return  ignoreExpiringScheduler
    }
}