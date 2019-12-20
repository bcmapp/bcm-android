package com.bcm.messenger.adhoc.logic

import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.utility.AppContextHolder

object AdHocSetting {
    private val AD_HOC_MODE_CONFIG = "ad_hoc_mode_enable_status"

    fun isEnable():Boolean {
        return SuperPreferences.getBooleanPreference(AppContextHolder.APP_CONTEXT, AD_HOC_MODE_CONFIG, false)
    }

    fun setEnable(adHocMode:Boolean) {
        SuperPreferences.setBooleanPreference(AppContextHolder.APP_CONTEXT, AD_HOC_MODE_CONFIG, adHocMode)
    }
}