package com.bcm.messenger.common.bcmhttp.interceptor.metrics

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
import okhttp3.Request

/**
 * 
 */
class AccountMetricsInterceptor(accountContext: AccountContext) : MetricsInterceptor() {
    private val metrics = AmeModuleCenter.metric(accountContext)
    override fun onComplete(req: Request, succeed:Boolean, code:Int, duration: Long) {
        val url = req.url()
        if (succeed) {
            metrics?.addNetworkReportData(url.host(), url.port(), req.method(), url.encodedPath(), code.toString(), duration)
        } else {
            //lbs 
            metrics?.addNetworkReportData(url.host(), url.port(), req.method(), url.encodedPath(), code.toString(), duration)
        }
    }
}