package com.bcm.messenger.appsflyer

import android.app.Application
import android.content.pm.PackageManager
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.provider.IAFModule
import com.bcm.messenger.common.utils.isReleaseBuild
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route

/**
 *
 * Created by wjh on 2019-12-06
 */
@Route(routePath = ARouterConstants.Provider.PROVIDER_AFLIB)
class AFModuleImp : IAFModule {

    private val TAG = "AFModuleImp"

    override fun onAppInit(context: Application) {
        ALog.i(TAG, "onAppInit")

        AppsFlyerLib.getInstance().init("Yj7efCFstL4gXSVD9NWLY7", object : AppsFlyerConversionListener {
            override fun onAppOpenAttribution(p0: MutableMap<String, String>?) {
                ALog.i(TAG, "onAppOpenAttribution")
            }

            override fun onAttributionFailure(p0: String?) {
                ALog.e(TAG, "onAttributionFailure error: $p0")
            }

            override fun onInstallConversionDataLoaded(p0: MutableMap<String, String>?) {
                ALog.i(TAG, "onInstallConversionDataLoaded")
            }

            override fun onInstallConversionFailure(p0: String?) {
                ALog.e(TAG, "onInstallConversionFailure error: $p0")
            }
        }, context)

        AppsFlyerLib.getInstance().startTracking(context)

        if (!isReleaseBuild()) {
            test(context)
        }
    }

    private fun test(context: Application) {
        try {
            val appInfo = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            ALog.d(TAG, "getAFMeta: ${appInfo.metaData.get("AF_PRE_INSTALL_NAME")}")
            ALog.d(TAG, "getChannelMeta: ${appInfo.metaData.get("channels")}")
        }catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}