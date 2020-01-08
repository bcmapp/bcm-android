package com.bcm.messenger.common.provider

import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.provider.accountmodule.IAmeAccountModule
import com.bcm.messenger.utility.ClassHelper
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.api.BcmRouter
import com.bcm.route.api.IRouteProvider
import java.util.concurrent.ConcurrentHashMap

/**
 * bcm.social.01 2018/9/20.
 */
@Suppress("UNCHECKED_CAST")
object AmeProvider {
    private const val TAG = "AmeProvider"
    private val moduleMap = ConcurrentHashMap<Index, IRouteProvider>()
    private data class Index(val context: AccountContext?, val providerName: String) {
        override fun equals(other: Any?): Boolean {
            if (other is Index) {
                return this.context == other.context && this.providerName == other.providerName
            }
            return false
        }
    }

    fun <T : IRouteProvider> get(providerName: String): T? {
        try {
            val key = Index(null, providerName)

            val module1 = moduleMap[key]
            if (null != module1) {
                return module1 as T
            }

            ALog.e(TAG, "$providerName wating")
            synchronized(TAG) {
                val module = moduleMap[key]
                if (null != module) {
                    return module as T
                }

                ALog.e(TAG, "$providerName entered")
                val provider = BcmRouter.getInstance().get(providerName).navigationWithCast<T>()
                if (provider is IAmeAccountModule) {
                    ALog.e(TAG, "$providerName provider instance failed, account module instance please call getAccountModule")
                    return null
                }

                if (null == provider) {
                    ALog.e(TAG, "$providerName provider instance failed")
                } else {
                    moduleMap[key] = provider
                }
                return provider
            }
        } catch (ex: Exception) {//ARouter，ARouter，，null
            ALog.e(TAG, "getProvider $providerName fail", ex)
        }
        return null
    }

    fun <T : IRouteProvider> getAccountModule(providerName: String, context: AccountContext): T? {

        try {
            val key = Index(context, providerName)

            val module1 = moduleMap[key]
            if (null != module1) {
                return module1 as T
            }

            synchronized(TAG) {
                val module = moduleMap[key]
                if (null != module) {
                    return module as T
                }

                if (!context.isLogin) {
                    return null
                }
                
                val provider = BcmRouter.getInstance().get(providerName).navigationWithCast<T>()
                if (null != provider && provider is IAmeAccountModule) {
                    provider.setContext(context)
                    moduleMap[key] = provider
                } else {
                    return null
                }
                return provider
            }
        } catch (ex: Exception) {
            ALog.e(TAG, "getProvider $providerName fail", ex)
        }
        return null
    }

    fun removeModule(providerName: String) {
        synchronized(TAG) {
            moduleMap.remove(Index(null, providerName))
        }
    }

    fun removeModule(context: AccountContext) {
        synchronized(TAG) {
            val keylist = moduleMap.keys.filter { it.context == context }
            keylist.forEach {
                moduleMap.remove(it)
            }
        }
    }
}