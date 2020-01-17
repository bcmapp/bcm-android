package com.bcm.messenger.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bcm.messenger.R
import com.bcm.messenger.common.core.setLocale
import com.bcm.messenger.common.mms.GlideApp
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.utils.setTranslucentStatus
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import kotlinx.android.synthetic.main.activity_tabless_intro.*

/**
 * Created by Kin on 2019/12/12
 */
class TablessIntroActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_tabless_intro)
        initView()
    }

    private fun initView() {
        window.setTranslucentStatus()

        GlideApp.with(this)
                .asGif()
                .load(R.drawable.home_tabless_intro_gif)
                .into(tabless_gif)

        tabless_confirm.setOnClickListener {
            SuperPreferences.setTablessIntroductionFlag(this@TablessIntroActivity)
            finish()
        }
        
        migrateSettings()
    }
    
    private fun migrateSettings() {
        AmeDispatcher.io.dispatch { 
            val majorContext = AMELogin.majorContext
            SuperPreferences.setNotificationsEnabled(AppContextHolder.APP_CONTEXT, TextSecurePreferences.isNotificationsEnabled(majorContext))
            SuperPreferences.setNotificationRingtone(AppContextHolder.APP_CONTEXT, TextSecurePreferences.getNotificationRingtone(majorContext))
            SuperPreferences.setNotificationVibrateEnabled(AppContextHolder.APP_CONTEXT, TextSecurePreferences.isNotificationVibrateEnabled(majorContext))
            SuperPreferences.setTurnOnly(AppContextHolder.APP_CONTEXT, TextSecurePreferences.isTurnOnly(majorContext))
            SuperPreferences.setScreenSecurityEnabled(AppContextHolder.APP_CONTEXT, TextSecurePreferences.isScreenSecurityEnabled(majorContext))
        }
    }

    override fun onBackPressed() {
        // Cannot back
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(setLocale(newBase))
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        if (overrideConfiguration != null) {
            val uiMode = overrideConfiguration.uiMode
            overrideConfiguration.setTo(baseContext.resources.configuration)
            overrideConfiguration.uiMode = uiMode
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }
}