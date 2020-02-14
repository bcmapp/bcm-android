package com.bcm.messenger.common

import android.content.res.Configuration
import android.os.Bundle
import android.os.Looper
import android.os.MessageQueue
import androidx.appcompat.app.AppCompatActivity
import com.bcm.messenger.common.theme.ThemeManager
import com.bcm.messenger.common.utils.setStatusBarLightMode

/**
 * Created by Kin on 2020/2/7
 */
open class ThemeBaseActivity : AppCompatActivity() {
    protected val themeManager = ThemeManager()
    protected var disabledLightStatusBar = false

    private val idleHandler = MessageQueue.IdleHandler {
        if (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
            disabledLightStatusBar = true
        }
        if (!disabledLightStatusBar) {
            window?.setStatusBarLightMode()
        }
        return@IdleHandler false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        themeManager.onCreate(this)
        super.onCreate(savedInstanceState)

        Looper.myQueue().addIdleHandler(idleHandler)
    }

    override fun onResume() {
        super.onResume()
        themeManager.onResume(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        Looper.myQueue().removeIdleHandler(idleHandler)
    }
}