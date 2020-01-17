package com.bcm.messenger.common.expiration

import com.bcm.messenger.common.utils.AccountContextMap
import com.bcm.messenger.utility.AppContextHolder

object ExpirationManager: AccountContextMap<ExpiringScheduler>({
    ExpiringScheduler(AppContextHolder.APP_CONTEXT, it)
})