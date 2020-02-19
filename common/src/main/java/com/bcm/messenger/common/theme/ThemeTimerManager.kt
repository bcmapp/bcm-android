package com.bcm.messenger.common.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.utility.AppContextHolder
import java.util.*

/**
 * Created by Kin on 2020/2/6
 */
class ThemeTimerManager {
    private var timeChangeReceiver: TimeChangeReceiver? = null

    private var lightThemeStartTime = SuperPreferences.getLightStartTime(AppContextHolder.APP_CONTEXT, "08:00")
    private var darkThemeStartTime = SuperPreferences.getDarkStartTime(AppContextHolder.APP_CONTEXT, "22:00")

    fun registerTimeReceiver() {
        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        timeChangeReceiver = TimeChangeReceiver()

        AppContextHolder.APP_CONTEXT.registerReceiver(timeChangeReceiver, intentFilter)

        updateThemeByTime()
    }

    fun unregisterTimeReceiver() {
        timeChangeReceiver?.also {
            AppContextHolder.APP_CONTEXT.unregisterReceiver(timeChangeReceiver)
        }
        timeChangeReceiver = null
    }

    private inner class TimeChangeReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateThemeByTime()
        }
    }

    fun onTimeChanged() {
        lightThemeStartTime = SuperPreferences.getLightStartTime(AppContextHolder.APP_CONTEXT, "08:00")
        darkThemeStartTime = SuperPreferences.getDarkStartTime(AppContextHolder.APP_CONTEXT, "22:00")

        updateThemeByTime()
    }

    private fun updateThemeByTime() {
        val lightTime = lightThemeStartTime.split(":")
        val darkTime = darkThemeStartTime.split(":")

        val calendar = Calendar.getInstance()
        val lightCalendar = Calendar.getInstance()
        val darkCalendar = Calendar.getInstance()

        lightCalendar.clear()
        lightCalendar.set(calendar[Calendar.YEAR], calendar[Calendar.MONTH], calendar[Calendar.DATE], lightTime[0].toInt(), lightTime[1].toInt())

        darkCalendar.clear()
        darkCalendar.set(calendar[Calendar.YEAR], calendar[Calendar.MONTH], calendar[Calendar.DATE], darkTime[0].toInt(), darkTime[1].toInt())

        val currentTime = calendar.time.time
        val lightLongTime = lightCalendar.time.time
        val darkLongTime = darkCalendar.time.time

        if (isScheduleCustomTheme()) {
            if (currentTime == lightLongTime) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            } else if (currentTime == darkLongTime) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        } else {
            if (lightLongTime > darkLongTime) {
                if (currentTime in darkLongTime..lightLongTime) {
                    if (!isDarkMode()) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    }
                } else {
                    if (isDarkMode()) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    }
                }
            } else {
                if (currentTime in lightLongTime..darkLongTime) {
                    if (isDarkMode()) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    }
                } else {
                    if (!isDarkMode()) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    }
                }
            }
        }
    }

    private fun isDarkMode(): Boolean {
        val activity = AmeAppLifecycle.current() ?: return false
        return activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    private fun isScheduleCustomTheme(): Boolean {
        val setting = SuperPreferences.getCurrentThemeSetting(AppContextHolder.APP_CONTEXT, ThemeManager.THEME_SYSTEM)
        return setting == ThemeManager.THEME_SCHEDULE_LIGHT || setting == ThemeManager.THEME_SCHEDULE_DARK
    }
}