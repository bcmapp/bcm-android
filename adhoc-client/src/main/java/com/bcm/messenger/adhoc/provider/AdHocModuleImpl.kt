package com.bcm.messenger.adhoc.provider

import android.content.Context
import android.content.Intent
import com.bcm.messenger.adhoc.logic.AdHocSessionLogic
import com.bcm.messenger.adhoc.logic.AdHocSetting
import com.bcm.messenger.adhoc.sdk.AdHocSDK
import com.bcm.messenger.adhoc.ui.channel.AdHocConversationActivity
import com.bcm.messenger.adhoc.ui.setting.AdHocSettingActivity
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.provider.IAdHocModule
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.route.annotation.Route

@Route(routePath = ARouterConstants.Provider.PROVIDER_AD_HOC)
class AdHocModuleImpl : IAdHocModule {
    override fun initModule() {

    }

    override fun uninitModule() {

    }

    override fun startAdHocServer(start: Boolean) {
        if (start) {
            AdHocSDK.startAdHocServer()
        } else {
            AdHocSDK.stopAdHocServer()
        }
    }

    override fun startScan(start: Boolean) {
        if (start) {
            AdHocSDK.startScan()
        } else {
            AdHocSDK.stopScan()
        }
    }

    override fun startBroadcast(start: Boolean) {
        if (start) {
            AdHocSDK.startBroadcast()
        } else {
            AdHocSDK.stopBroadcast()
        }
    }

    override fun isAdHocMode(): Boolean {
        return AdHocSetting.isEnable()
    }

    override fun configHocMode() {
        val activity = AmeAppLifecycle.current()
        if (null != activity) {
            val intent = Intent(activity, AdHocSettingActivity::class.java)
            activity.startActivity(intent)
        }
    }

    override fun repairAdHocServer() {
        AdHocSDK.repairAdHocServer()
    }

    override fun repairAdHocScanner() {
        AdHocSDK.repairScanner()
    }

    override fun gotoPrivateChat(context: Context, uid: String) {
        AdHocSessionLogic.addChatSession(uid) { sessionId ->
            ALog.i("AdHocProviderImp", "gotoPrivateChat sessionId: $sessionId")
            if (sessionId.isNotEmpty()) {
                context.startActivity(Intent(context, AdHocConversationActivity::class.java).apply {
                    putExtra(ARouterConstants.PARAM.PARAM_ADHOC_SESSION, sessionId)
                })
            }
        }
    }
}