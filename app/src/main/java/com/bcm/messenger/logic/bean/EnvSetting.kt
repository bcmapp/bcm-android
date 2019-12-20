package com.bcm.messenger.logic.bean

import com.bcm.messenger.utility.proguard.NotGuard


/**
 * bcm.social.01 2018/10/15.
 */
data class EnvSetting(var devEnable:Boolean,
                      var server:String,
                      var walletDev:Boolean,
                      var lbsEnable:Boolean,
                      var httpsEnable:Boolean
): NotGuard {
    fun diff(curEnv: EnvSetting): Boolean {
        return devEnable != curEnv.devEnable
                || server != curEnv.server
                || walletDev != curEnv.walletDev
                || lbsEnable != curEnv.lbsEnable
                || httpsEnable != curEnv.httpsEnable
    }

    fun copy():EnvSetting {
        return EnvSetting(devEnable, server, walletDev, lbsEnable, httpsEnable)
    }
}