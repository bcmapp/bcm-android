package com.bcm.messenger.common.provider

import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.api.BcmRouter

/**
 * Created by bcm.social.01 on 2018/9/20.
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
        }catch (ex: Exception) {//某些特殊原因导致ARouter还没有初始化完毕，所以这时候调用ARouter是会抛异常，这里捕获一下，返回null
            ALog.e(TAG, "getProvider $providerName fail", ex)
        }
        return null
    }
}