package com.bcm.messenger.common.theme

import android.app.Activity
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.utility.AppContextHolder

/**
 * Created by Kin on 2020/2/3
 */
open class ThemeManager {
    private var currentTheme = THEME_SYSTEM

    fun onCreate(activity: Activity) {
        currentTheme = getCurrentTheme(activity)
    }

    fun onResume(activity: Activity) {
        // DO noting.
    }

    protected fun getCurrentTheme(activity: Activity): Int {
        return SuperPreferences.getCurrentThemeSetting(activity, THEME_SYSTEM)
    }

    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        @JvmStatic
        fun initTheme() {
            when (SuperPreferences.getCurrentThemeSetting(AppContextHolder.APP_CONTEXT, THEME_SYSTEM)) {
                THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                THEME_LIGHT-> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }
    }
}