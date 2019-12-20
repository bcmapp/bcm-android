package com.bcm.messenger.common.provider

import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.api.BcmRouter

/**
 * bcm.social.01 2018/9/20.
 */
object AmeProvider {
    private const val TAG = "AmeProvider"
    fun <T> get(providerName: String): T? {
        try {
            val provider = BcmRouter.getInstance().get(providerName).navigationWithCast<T>()
            if (null == provider) {
                ALog.i(TAG, "$providerName provider instance failed")
            }
            return provider
        }catch (ex: Exception) {//ARouter，ARouter，，null
            ALog.e(TAG, "getProvider $providerName fail", ex)
        }
        return null
    }
}