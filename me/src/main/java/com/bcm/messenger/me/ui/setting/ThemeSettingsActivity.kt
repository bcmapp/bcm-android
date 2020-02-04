package com.bcm.messenger.me.ui.setting

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.common.theme.ThemeManager
import com.bcm.messenger.common.ui.CommonSettingItem
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.me.R
import kotlinx.android.synthetic.main.me_activity_theme_settings.*
import kotlinx.android.synthetic.main.me_item_language_select.view.*

/**
 * Created by Kin on 2020/2/4
 */
class ThemeSettingsActivity : SwipeBaseActivity() {
    private val themeList = mutableListOf<String>()
    private val themeManager = ThemeManager()

    private var currentTheme = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        themeManager.onCreate(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_theme_settings)

        initView()
    }

    override fun onResume() {
        super.onResume()
        themeManager.onResume(this)
    }

    private fun initView() {
        if (Build.VERSION.SDK_INT >= 29) {
            currentTheme = SuperPreferences.getCurrentThemeSetting(this, themeManager.THEME_SYSTEM)
            themeList.addAll(listOf(
                    getString(R.string.me_theme_settings_system_default),
                    getString(R.string.me_theme_settings_light),
                    getString(R.string.me_theme_settings_dark)
            ))
        } else {
            currentTheme = SuperPreferences.getCurrentThemeSetting(this, themeManager.THEME_LIGHT)
            themeList.addAll(listOf(
                    getString(R.string.me_theme_settings_light),
                    getString(R.string.me_theme_settings_dark)
            ))
        }

        theme_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                save()
            }
        })

        theme_list.layoutManager = LinearLayoutManager(this)
        theme_list.adapter = ThemeSelectAdapter()
    }

    private inner class ThemeSelectAdapter : RecyclerView.Adapter<ThemeSelectViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeSelectViewHolder {
            return ThemeSelectViewHolder(layoutInflater.inflate(R.layout.me_item_language_select, parent, false))
        }

        override fun getItemCount(): Int {
            return themeList.size
        }

        override fun onBindViewHolder(holder: ThemeSelectViewHolder, position: Int) {
            holder.bindData(themeList[position], position)
        }
    }

    private inner class ThemeSelectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bindData(data: String, position: Int) {
            itemView.language_select_item.setName(data)
            itemView.language_select_item.setOnClickListener {
                itemView.language_select_item.showRightStatus(CommonSettingItem.RIGHT_YES)
                if (position != currentTheme) {
                    (theme_list.layoutManager as LinearLayoutManager).findViewByPosition(currentTheme)?.language_select_item?.showRightStatus(CommonSettingItem.RIGHT_NONE)
                    currentTheme = position
                }
            }

            if (position == currentTheme) {
                itemView.language_select_item.showRightStatus(CommonSettingItem.RIGHT_YES)
            } else {
                itemView.language_select_item.showRightStatus(CommonSettingItem.RIGHT_NONE)
            }
        }
    }

    private fun save() {
        if (Build.VERSION.SDK_INT < 29) {
            currentTheme++
        }

        when (currentTheme) {
            themeManager.THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            themeManager.THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            themeManager.THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
        SuperPreferences.setCurrentThemeSetting(this, currentTheme)

        finish()
    }
}