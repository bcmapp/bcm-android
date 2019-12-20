package com.bcm.messenger.ui

import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.utility.AppContextHolder

object LaunchAdConfigure {
    private const val AD_SHOW_VERSION = "bcm_ad_show_version"

    fun adEnable(): Boolean {
        val version = SuperPreferences.getStringPreference(AppContextHolder.APP_CONTEXT, AD_SHOW_VERSION)
        if (version.isNullOrEmpty()) {
            return true
        }
        return false
    }

    fun disableAd() {
        SuperPreferences.setStringPreference(AppContextHolder.APP_CONTEXT, AD_SHOW_VERSION, AppUtil.getVersionCode(AppContextHolder.APP_CONTEXT).toString())
    }
}