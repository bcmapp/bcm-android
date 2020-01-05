package com.bcm.messenger.common.bcmhttp.interceptor.metrics

import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.metrics.*
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.accountmodule.IMetricsModule
import okhttp3.Request

/**
 * Created by Kin on 2019/11/25
 * 
 */
class ReportMetricInterceptor(accountContext: AccountContext) : MetricsInterceptor() {
    private val metricsProvider = AmeProvider.getAccountModule<IMetricsModule>(ARouterConstants.Provider.REPORT_BASE, accountContext)

    override fun onComplete(req: Request, succeed: Boolean, code: Int, duration: Long) {
        val url = req.url()
        metricsProvider?.addCustomNetworkReportData(API_METRICS_SERVER, url.host(), url.port(), req.method(), url.encodedPath(), code.toString(), duration)

        if ((code in 200..299) || code == 462) {
            metricsProvider?.addCustomCounterReportData(COUNTER_TOPIC_METRIC_SERVER, COUNTER_METRIC_CONNECT_SUCCESS)
            metricsProvider?.addCustomCounterReportData(COUNTER_TOPIC_METRIC_SERVER, COUNTER_METRIC_CONNECT_FAIL, false)
        } else {
            metricsProvider?.addCustomCounterReportData(COUNTER_TOPIC_METRIC_SERVER, COUNTER_METRIC_CONNECT_FAIL)
            metricsProvider?.addCustomCounterReportData(COUNTER_TOPIC_METRIC_SERVER, COUNTER_METRIC_CONNECT_SUCCESS, false)
        }
    }
}