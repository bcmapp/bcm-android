package com.bcm.messenger.common.theme

import android.app.Activity
import android.content.res.Configuration
import com.bcm.messenger.common.preferences.SuperPreferences

/**
 * Created by Kin on 2020/2/3
 */
open class ThemeManager {
    val THEME_SYSTEM = 0
    val THEME_LIGHT = 1
    val THEME_DARK = 2

    private var currentTheme = THEME_SYSTEM

    fun onCreate(activity: Activity) {
        currentTheme = getCurrentTheme(activity)
    }

    fun onResume(activity: Activity) {
        if (currentTheme != getCurrentTheme(activity)) {
            activity.recreate()
        }
    }

    fun onConfigurationChanged(activity: Activity) {
        if (activity.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_NIGHT_YES) {

        } else {

        }
    }

    protected fun getCurrentTheme(activity: Activity): Int {
        return SuperPreferences.getCurrentThemeSetting(activity, THEME_SYSTEM)
    }
}