package com.bcm.messenger.common.imagepicker.ui.activity

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Looper
import android.os.MessageQueue
import com.bcm.messenger.common.ThemeBaseActivity
import com.bcm.messenger.common.core.setLocale
import com.bcm.messenger.common.utils.setStatusBarLightMode
import com.bumptech.glide.Glide
import com.bumptech.glide.util.Util

/**
 * Created by Kin on 2019/4/17
 */

open class BasePickActivity : ThemeBaseActivity() {
    private val idleHandler = MessageQueue.IdleHandler {
        if (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES) {
            window.setStatusBarLightMode()
        }
        return@IdleHandler false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Looper.myQueue().addIdleHandler(idleHandler)
    }

    override fun onDestroy() {
        super.onDestroy()
        Looper.myQueue().removeIdleHandler(idleHandler)
        if (Util.isOnMainThread() && !this.isFinishing && !this.isDestroyed) {
            Glide.with(this).pauseRequests()
        }
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