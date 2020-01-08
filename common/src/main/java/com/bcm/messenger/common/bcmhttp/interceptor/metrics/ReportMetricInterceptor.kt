package com.bcm.messenger.common.bcmhttp.interceptor.metrics

import com.bcm.messenger.common.metrics.API_METRICS_SERVER
import com.bcm.messenger.common.metrics.COUNTER_METRIC_CONNECT_FAIL
import com.bcm.messenger.common.metrics.COUNTER_METRIC_CONNECT_SUCCESS
import com.bcm.messenger.common.metrics.COUNTER_TOPIC_METRIC_SERVER
import com.bcm.messenger.common.provider.accountmodule.IMetricsModule
import okhttp3.Request

/**
 * Created by Kin on 2019/11/25
 * 
 */
class ReportMetricInterceptor(private val metrics:IMetricsModule) : MetricsInterceptor() {
    override fun onComplete(req: Request, succeed: Boolean, code: Int, duration: Long) {
        val url = req.url()
        metrics.addCustomNetworkReportData(API_METRICS_SERVER, url.host(), url.port(), req.method(), url.encodedPath(), code.toString(), duration)

        if ((code in 200..299) || code == 462) {
            metrics.addCustomCounterReportData(COUNTER_TOPIC_METRIC_SERVER, COUNTER_METRIC_CONNECT_SUCCESS)
            metrics.addCustomCounterReportData(COUNTER_TOPIC_METRIC_SERVER, COUNTER_METRIC_CONNECT_FAIL, false)
        } else {
            metrics.addCustomCounterReportData(COUNTER_TOPIC_METRIC_SERVER, COUNTER_METRIC_CONNECT_FAIL)
            metrics.addCustomCounterReportData(COUNTER_TOPIC_METRIC_SERVER, COUNTER_METRIC_CONNECT_SUCCESS, false)
        }
    }
}