package com.bcm.messenger.common.utils

import com.bcm.messenger.common.AccountContext
import java.util.concurrent.ConcurrentHashMap

open class AccountContextMap<T>(private val instanceOfContext: (context: AccountContext) -> T) {
    private val instances = ConcurrentHashMap<AccountContext, T>()

    fun get(accountContext: AccountContext): T {
        val v = instances[accountContext]
        if (null != v) {
            return v
        }

        synchronized(instances) {
            var v1 = instances[accountContext]
            if (null != v1) {
                return v1
            }

            v1 = instanceOfContext(accountContext)
            instances[accountContext] = v1
            return v1
        }
    }

    fun remove(accountContext: AccountContext) {
        instances.remove(accountContext)

        val gcList = instances.keys.filter { !it.isLogin }
        gcList.forEach {
            instances.remove(it)
        }
    }

    fun containsKey(accountContext: AccountContext): Boolean {
        return instances.containsKey(accountContext)
    }
}