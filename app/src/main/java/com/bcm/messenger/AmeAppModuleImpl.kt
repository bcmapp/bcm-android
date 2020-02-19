package com.bcm.messenger

import android.app.Activity
import android.content.Intent
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.event.HomeTopEvent
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.IAmeAppModule
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.messenger.logic.EnvSettingLogic
import com.bcm.messenger.share.SystemShareActivity
import com.bcm.messenger.ui.HomeActivity
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.PlayStoreUtil
import com.bcm.route.annotation.Route

/**
 * Created by bcm.social.01 on 2018/6/20.
 */

@Route(routePath = ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)
class AmeAppModuleImpl : IAmeAppModule {

    override fun initModule() {

    }

    override fun uninitModule() {

    }

    override fun isSupportGooglePlay(): Boolean {
        return PlayStoreUtil.isGoogleStoreInstalled(AppContextHolder.APP_CONTEXT)
    }

    override fun useDevBlockChain(): Boolean {
        return EnvSettingLogic.getEnvSetting().walletDev
    }

    override fun lastBuildTime(): Long {
        return BuildConfig.BUILD_TIMESTAMP
    }

    override fun lbsEnable(): Boolean {
        return EnvSettingLogic.getEnvSetting().lbsEnable
    }

    override fun testEnvEnable(): Boolean {
        return EnvSettingLogic.getEnvSetting().devEnable
    }

    override fun isDevBuild(): Boolean {
        return BuildConfig.DEBUG
    }

    override fun isBetaBuild(): Boolean {
        return BuildConfig.FLAVOR == "official"
    }

    override fun isEnabledHttps(): Boolean {
        return EnvSettingLogic.getEnvSetting().httpsEnable
    }

    override fun isReleaseBuild(): Boolean {
        return BuildConfig.FLAVOR.startsWith(ARouterConstants.CONSTANT_RELEASE) && !isDevBuild()
    }

    override fun isGooglePlayEdition(): Boolean {
        return BuildConfig.FLAVOR == "releaseGoogle"
    }

    override fun serverHost(): String {
        return "https://${EnvSettingLogic.getEnvSetting().server}"
    }

    override fun systemForward(activity: Activity, text: String) {
        if (text.isNotBlank()) {
            val intent = Intent(activity, SystemShareActivity::class.java)
            intent.action = "android.intent.action.SEND"
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, text)
            intent.putExtra(ARouterConstants.PARAM.PARAM_ENTER_ANIM, R.anim.common_slide_from_bottom_fast)
            intent.putExtra(ARouterConstants.PARAM.PARAM_EXIT_ANIM, R.anim.common_slide_to_bottom_fast)
            activity.startBcmActivity(AMELogin.majorContext, intent)
        }
    }

    override fun gotoHome(accountContext: AccountContext, event: HomeTopEvent) {
        val intent = Intent(AppContextHolder.APP_CONTEXT, HomeActivity::class.java).apply {
            putExtra(ARouterConstants.PARAM.PARAM_DATA, event.toString())
        }
        val activity = AmeAppLifecycle.current()
        if (activity != null) {
            activity.startBcmActivity(AMELogin.majorContext, intent)
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            AppContextHolder.APP_CONTEXT.startBcmActivity(AMELogin.majorContext, intent)
        }
    }
}
