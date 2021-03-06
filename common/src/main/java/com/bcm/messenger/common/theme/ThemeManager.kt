package com.bcm.messenger.common.theme

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
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
        val theme = getCurrentTheme(activity)
        if (theme != currentTheme) {
            activity.recreate()
        }
    }

    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
        const val THEME_SCHEDULE = 3
        const val THEME_SCHEDULE_LIGHT = 4
        const val THEME_SCHEDULE_DARK = 5

        @JvmStatic
        private var timerManager: ThemeTimerManager? = null

        @JvmStatic
        fun initTheme() {
            when (getCurrentTheme(AppContextHolder.APP_CONTEXT)) {
                THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                THEME_LIGHT-> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                THEME_SCHEDULE -> startTimer()
                THEME_SCHEDULE_LIGHT -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    startTimer()
                }
                THEME_SCHEDULE_DARK -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    startTimer()
                }
            }
        }

        @JvmStatic
        fun startTimer() {
            timerManager = ThemeTimerManager().apply {
                registerTimeReceiver()
            }
        }

        @JvmStatic
        fun stopTimer() {
            timerManager?.unregisterTimeReceiver()
            timerManager = null
        }

        @JvmStatic
        fun themeTimeChanged() {
            timerManager?.onTimeChanged()
        }

        @JvmStatic
        fun onConfigurationChanged(context: Context, newConfig: Configuration?) {
            if (Build.VERSION.SDK_INT >= 29
                    && getCurrentTheme(AppContextHolder.APP_CONTEXT) == THEME_SYSTEM
                    && newConfig != null) {
                val config = context.resources.configuration
                config.uiMode = newConfig.uiMode
                context.createConfigurationContext(config)
            }
        }

        @JvmStatic
        fun isDarkTheme(activity: Activity): Boolean {
            return activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }

        fun isScheduleTheme(context: Context): Boolean {
            val theme = getCurrentTheme(context)
            return theme == THEME_SCHEDULE || theme == THEME_SCHEDULE_LIGHT || theme == THEME_SCHEDULE_DARK
        }

        protected fun getCurrentTheme(context: Context): Int {
            return SuperPreferences.getCurrentThemeSetting(context, THEME_SYSTEM)
        }
    }
}