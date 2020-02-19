package com.bcm.messenger.me.ui.setting

import android.app.TimePickerDialog
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.common.theme.ThemeManager
import com.bcm.messenger.common.ui.CommonSettingItem
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.utils.getAttrColor
import com.bcm.messenger.me.R
import com.bcm.messenger.utility.ViewUtils
import kotlinx.android.synthetic.main.me_activity_theme_settings.*
import java.lang.ref.WeakReference

/**
 * Created by Kin on 2020/2/4
 */
class ThemeSettingsActivity : SwipeBaseActivity() {
    private var currentTheme = 0
    private var currentThemeSetting = AppCompatDelegate.getDefaultNightMode()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_theme_settings)

        initView()
        initData()
    }

    private fun initView() {
        theme_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }
        })

        theme_light.setOnClickListener {
            if (currentThemeSetting == ThemeManager.THEME_SYSTEM) {
                return@setOnClickListener
            }
            theme_select_light.setBackgroundResource(R.drawable.me_theme_settings_selected_bg)
            theme_select_dark.background = null

            if (ThemeManager.isScheduleTheme(this)) {
                currentThemeSetting = ThemeManager.THEME_SCHEDULE_LIGHT
            } else {
                currentThemeSetting = ThemeManager.THEME_LIGHT
                ViewUtils.fadeOut(theme_schedule_layout, 250)
            }
            SuperPreferences.setCurrentThemeSetting(this, currentThemeSetting)
            ThemeManager.stopTimer()
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
        theme_dark.setOnClickListener {
            if (currentThemeSetting == ThemeManager.THEME_SYSTEM) {
                return@setOnClickListener
            }
            theme_select_light.background = null
            theme_select_dark.setBackgroundResource(R.drawable.me_theme_settings_selected_bg)

            if (ThemeManager.isScheduleTheme(this)) {
                currentThemeSetting = ThemeManager.THEME_SCHEDULE_DARK
            } else {
                currentThemeSetting = ThemeManager.THEME_DARK
                ViewUtils.fadeOut(theme_schedule_layout, 250)
            }
            SuperPreferences.setCurrentThemeSetting(this, currentThemeSetting)
            ThemeManager.stopTimer()
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }

        theme_follow_system.setOnClickListener {
            theme_follow_system.showRightStatus(CommonSettingItem.RIGHT_YES)
            theme_schedule.showRightStatus(CommonSettingItem.RIGHT_NONE)
            theme_disabled.showRightStatus(CommonSettingItem.RIGHT_NONE)

            ViewUtils.fadeOut(theme_schedule_layout, 250)

            currentThemeSetting = ThemeManager.THEME_SYSTEM
            SuperPreferences.setCurrentThemeSetting(this, ThemeManager.THEME_SYSTEM)

            ThemeManager.stopTimer()
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        theme_schedule.setOnClickListener {
            if (currentThemeSetting == ThemeManager.THEME_SCHEDULE) {
                return@setOnClickListener
            }
            theme_follow_system.showRightStatus(CommonSettingItem.RIGHT_NONE)
            theme_schedule.showRightStatus(CommonSettingItem.RIGHT_YES)
            theme_disabled.showRightStatus(CommonSettingItem.RIGHT_NONE)

            ViewUtils.fadeIn(theme_schedule_layout, 250)

            currentThemeSetting = ThemeManager.THEME_SCHEDULE
            SuperPreferences.setCurrentThemeSetting(this, ThemeManager.THEME_SCHEDULE)
            ThemeManager.startTimer()
        }
        theme_disabled.setOnClickListener {
            theme_follow_system.showRightStatus(CommonSettingItem.RIGHT_NONE)
            theme_schedule.showRightStatus(CommonSettingItem.RIGHT_NONE)
            theme_disabled.showRightStatus(CommonSettingItem.RIGHT_YES)

            val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
                currentThemeSetting = ThemeManager.THEME_DARK
                theme_select_dark.setBackgroundResource(R.drawable.me_theme_settings_selected_bg)
                SuperPreferences.setCurrentThemeSetting(this, ThemeManager.THEME_DARK)
            } else {
                currentThemeSetting = ThemeManager.THEME_LIGHT
                theme_select_light.setBackgroundResource(R.drawable.me_theme_settings_selected_bg)
                SuperPreferences.setCurrentThemeSetting(this, ThemeManager.THEME_LIGHT)
            }

            ViewUtils.fadeOut(theme_schedule_layout, 250)
            ThemeManager.stopTimer()
        }

        theme_schedule_light.setOnClickListener {
            showTimePicker(true)
        }
        theme_schedule_dark.setOnClickListener {
            showTimePicker(false)
        }
    }

    private fun initData() {
        currentTheme = delegate.localNightMode

        if (Build.VERSION.SDK_INT >= 29) {
            currentThemeSetting = SuperPreferences.getCurrentThemeSetting(this, ThemeManager.THEME_SYSTEM)
        } else {
            currentThemeSetting = SuperPreferences.getCurrentThemeSetting(this, ThemeManager.THEME_LIGHT)
            theme_follow_system.visibility = View.GONE
            theme_schedule.setHead(getString(R.string.me_theme_settings_auto_title))
        }

        val lightStartTime = SuperPreferences.getLightStartTime(this, "07:00")
        val darkStartTime = SuperPreferences.getDarkStartTime(this, "22:00")
        theme_schedule_light.setTip(lightStartTime, contentColor = getAttrColor(R.attr.common_text_third_color))
        theme_schedule_dark.setTip(darkStartTime, contentColor = getAttrColor(R.attr.common_text_third_color))

        when (currentThemeSetting) {
            ThemeManager.THEME_SYSTEM -> theme_follow_system.showRightStatus(CommonSettingItem.RIGHT_YES)
            ThemeManager.THEME_LIGHT -> {
                theme_select_light.setBackgroundResource(R.drawable.me_theme_settings_selected_bg)
                theme_disabled.showRightStatus(CommonSettingItem.RIGHT_YES)
            }
            ThemeManager.THEME_DARK -> {
                theme_select_dark.setBackgroundResource(R.drawable.me_theme_settings_selected_bg)
                theme_disabled.showRightStatus(CommonSettingItem.RIGHT_YES)
            }
            ThemeManager.THEME_SCHEDULE,
            ThemeManager.THEME_SCHEDULE_LIGHT,
            ThemeManager.THEME_SCHEDULE_DARK -> {
                theme_schedule.showRightStatus(CommonSettingItem.RIGHT_YES)
                theme_schedule_layout.visibility = View.VISIBLE
            }
        }

        if (ThemeManager.isDarkTheme(this)) {
            theme_select_dark.setBackgroundResource(R.drawable.me_theme_settings_selected_bg)
        } else {
            theme_select_light.setBackgroundResource(R.drawable.me_theme_settings_selected_bg)
        }
    }

    private fun showTimePicker(isLight: Boolean) {
        val weakThis = WeakReference(this)
        TimePickerDialog(this, { _, hourOfDay, minute ->
            val newTime = String.format("%02d:%02d", hourOfDay, minute)
            if (isLight) {
                weakThis.get()?.theme_schedule_light?.setTip(newTime, contentColor = getAttrColor(R.attr.common_text_third_color))
                SuperPreferences.setLightStartTime(this, newTime)
            } else {
                weakThis.get()?.theme_schedule_dark?.setTip(newTime, contentColor = getAttrColor(R.attr.common_text_third_color))
                SuperPreferences.setDarkStartTime(this, newTime)
            }
            ThemeManager.themeTimeChanged()
        }, 0, 0, true).show()
    }
}