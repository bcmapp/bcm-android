package com.bcm.messenger.common.bcmhttp.interceptor.metrics

import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.metrics.ReportUtil
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.accountmodule.IMetricsModule
import okhttp3.Request

/**
 * 
 */
class NormalMetricsInterceptor(accountContext: AccountContext) : MetricsInterceptor() {
    private val metricsProvider = AmeProvider.getAccountModule<IMetricsModule>(ARouterConstants.Provider.REPORT_BASE, accountContext)

    override fun onComplete(req: Request, succeed:Boolean, code:Int, duration: Long) {
        val url = req.url()
        if (succeed) {
            metricsProvider?.addNetworkReportData(url.host(), url.port(), req.method(), url.encodedPath(), code.toString(), duration)
        } else {
            //lbs 
            metricsProvider?.addNetworkReportData(url.host(), url.port(), req.method(), url.encodedPath(), code.toString(), duration)
        }
    }
}