package com.bcm.messenger.common.bcmhttp

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.utility.bcmhttp.facade.RxHttpWrapper
import java.util.concurrent.ConcurrentHashMap

class RxIMHttp {
    companion object {
        private val httpClients = ConcurrentHashMap<AccountContext, RxHttpWrapper>()

        fun getHttp(accountContext: AccountContext): RxHttpWrapper {
            var http = httpClients[accountContext]
            if (null == http) {
                http = RxHttpWrapper(IMHttp.getHttp(accountContext))
                httpClients[accountContext] = http
            }
            return http
        }

        fun removeHttp(accountContext: AccountContext) {
            httpClients.remove(accountContext)

            val gcList = httpClients.keys.filter { !it.isLogin }
            gcList.forEach {
                httpClients.remove(it)
            }
        }
    }
}