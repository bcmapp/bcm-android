package com.bcm.messenger.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.bcm.messenger.R
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.ThemeBaseActivity
import com.bcm.messenger.common.core.setLocale
import com.bcm.messenger.common.deprecated.DatabaseFactory
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.theme.ThemeManager
import com.bcm.messenger.common.ui.activity.DatabaseMigrateActivity
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.logic.SchemeLaunchHelper
import com.bcm.messenger.me.ui.login.RegistrationActivity
import com.bcm.messenger.me.utils.BcmUpdateUtil
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.permission.PermissionUtil
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter


/**
 * Launch
 */

@Route(routePath = ARouterConstants.Activity.APP_LAUNCH_PATH)
class LaunchActivity : ThemeBaseActivity() {
    private val TAG = "LaunchActivity"

    companion object {
        private val quickOp = QuickOpCheck(2000)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ThemeManager.isDarkTheme(this)) {
            window.setStatusBarDarkMode()
        } else {
            window.setStatusBarLightMode()
        }

        if (quickOp.isQuick || savedInstanceState != null) {
            finish()
            return
        }
        val hasSchemeData = SchemeLaunchHelper.hasSchemeData(intent)
        ALog.i(TAG, "root: $isTaskRoot scheme data:$hasSchemeData")

        if (AMELogin.isLogin) {
            if (!isTaskRoot && !hasSchemeData) {
                finish()
                return
            }
        }

        checkLaunch()
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
        } else if (DatabaseFactory.isDatabaseExist(AMELogin.majorContext,this) && !TextSecurePreferences.isDatabaseMigrated(AMELogin.majorContext)) {
            routeToDatabaseMigrate()
        } else {
            routeToHome()
        }
    }

    private fun routeToHome() {
        ALog.i(TAG, "routeToHome")
        SchemeLaunchHelper.storeSchemeIntent(intent)

        startBcmActivity(AMELogin.majorContext, Intent(this, HomeActivity::class.java).apply {
            if (isTaskRoot) {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        })

        if (!isTaskRoot) {
            finish()
        }
    }

    private fun routeToRegister() {
        if (AmeModuleCenter.login().accountSize() > 0) {
            ALog.i(TAG, "routeToLogin")
            BcmRouter.getInstance().get(ARouterConstants.Activity.ACCOUNT_SWITCHER)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .navigation(this)
        } else {
            ALog.i(TAG, "routeToRegister")
            SchemeLaunchHelper.storeSchemeIntent(intent)
            startActivity(Intent(this, RegistrationActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }

    private fun routeToDatabaseMigrate() {
        ALog.i(TAG, "Route to database migrate")
        SchemeLaunchHelper.storeSchemeIntent(intent)
        startBcmActivity(AMELogin.majorContext, Intent(this, DatabaseMigrateActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }
}
