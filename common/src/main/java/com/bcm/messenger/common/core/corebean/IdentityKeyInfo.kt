package com.bcm.messenger.common.core.corebean

import com.bcm.messenger.common.config.BcmFeatureSupport
import com.bcm.messenger.common.core.AddressUtil
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.proguard.NotGuard

class IdentityKeyInfo(val uid:String="",
                      val identityKey:String="",
                      val features:String?): NotGuard {
    private var support:BcmFeatureSupport? = null

    fun isValid(): Boolean {
        if (identityKey.isEmpty() || uid.isEmpty()) {
            return false
        }

        return AddressUtil.isValid(uid, identityKey)
    }

    fun getSupport():BcmFeatureSupport? {
        if (null == support && !features.isNullOrEmpty()) {
            try {
                support = BcmFeatureSupport(features)
            } catch (e:Throwable) {
                ALog.e("IdentityKeyInfo", "parse failed", e)
            }
        }
        return support
    }
}