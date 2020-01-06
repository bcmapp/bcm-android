package com.bcm.messenger.common.provider

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.provider.accountmodule.IAmeAccountModule
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.api.BcmRouter
import com.bcm.route.api.IRouteProvider

/**
 * bcm.social.01 2018/9/20.
 */
@Suppress("UNCHECKED_CAST")
object AmeProvider {
    private const val TAG = "AmeProvider"
    private val moduleMap = HashMap<Index, IRouteProvider>()
    private data class Index(val context: AccountContext?, val providerName: String)

    fun <T : IRouteProvider> get(providerName: String): T? {
        try {
            synchronized(moduleMap) {
                val key = Index(null, providerName)
                val module = moduleMap[key]
                if (null != module) {
                    return module as T
                }

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
            synchronized(moduleMap) {
                val key = Index(context, providerName)
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
        synchronized(moduleMap) {
            moduleMap.remove(Index(null, providerName))
        }
    }

    fun removeModule(context: AccountContext) {
        synchronized(moduleMap) {
            val keylist = moduleMap.keys.filter { it.context == context }
            keylist.forEach {
                moduleMap.remove(it)
            }
        }
    }
}