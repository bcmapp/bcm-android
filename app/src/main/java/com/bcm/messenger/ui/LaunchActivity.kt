package com.bcm.messenger.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bcm.messenger.R
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.core.setLocale
import com.bcm.messenger.common.database.DatabaseFactory
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.ui.activity.DatabaseMigrateActivity
import com.bcm.messenger.logic.SchemeLaunchHelper
import com.bcm.messenger.me.ui.login.RegistrationActivity
import com.bcm.messenger.me.utils.BcmUpdateUtil
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.permission.PermissionUtil
import com.bcm.route.annotation.Route


/**
 * Launch
 */

@Route(routePath = ARouterConstants.Activity.APP_LAUNCH_PATH)
class LaunchActivity : AppCompatActivity() {
    private val TAG = "LaunchActivity"

    companion object {
        private val quickOp = QuickOpCheck(2000)
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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (quickOp.isQuick || savedInstanceState != null) {
            finish()
            return
        }

        val hasSchemeData = SchemeLaunchHelper.hasSchemeData(intent)
        ALog.i(TAG, "root: $isTaskRoot scheme data:$hasSchemeData")

        if (AMESelfData.isLogin) {
            if (!isTaskRoot && !hasSchemeData) {
                finish()
                return
            }
        }

        if (LaunchAdConfigure.adEnable()) {
            LaunchAdConfigure.disableAd()
            setContentView(R.layout.launch_activity)
            AmeDispatcher.mainThread.dispatch({
                checkLaunch()
            }, 2000)
        } else {
            checkLaunch()
        }
    }

    private fun checkLaunch() {
        PermissionUtil.checkStorage(this) {
            router()
        }
    }

    private fun router() {
        if (!AMELogin.isLogin) {
            ALog.i(TAG, "route to register")
            BcmUpdateUtil.checkUpdate { hasUpdate, forceUpdate, _ ->
                if (hasUpdate) {
                    if (forceUpdate) {
                        BcmUpdateUtil.showForceUpdateDialog()
                    } else {
                        BcmUpdateUtil.showUpdateDialog()
                    }
                }
            }
            routeToRegister()
        } else if (DatabaseFactory.isDatabaseExist(this) && !TextSecurePreferences.isDatabaseMigrated(this)) {
            routeToDatabaseMigrate()
        } else {
            routeToHome()
        }
    }

    private fun routeToHome() {
        ALog.i(TAG, "routeToHome")
        SchemeLaunchHelper.storeSchemeIntent(null)
        intent.component = ComponentName(this, HomeActivity::class.java)
        startActivity(intent)
        delayFinish()
    }

    private fun routeToRegister() {
        ALog.i(TAG, "routeToRegister")
        SchemeLaunchHelper.storeSchemeIntent(intent)
        startActivity(Intent(this, RegistrationActivity::class.java))
        delayFinish()
    }

    private fun routeToDatabaseMigrate() {
        ALog.i(TAG, "Route to database migrate")
        SchemeLaunchHelper.storeSchemeIntent(intent)
        startActivity(Intent(this, DatabaseMigrateActivity::class.java))
        delayFinish()
    }

    private fun delayFinish() {
        AmeDispatcher.mainThread.dispatch({
            finish()
        }, 2000)
    }
}
