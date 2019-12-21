package com.bcm.messenger.common.bcmhttp.interceptor.metrics

import com.bcm.messenger.common.metrics.*
import okhttp3.Request

/**
 * Created by Kin on 2019/11/25
 * 
 */
class ReportMetricInterceptor : MetricsInterceptor() {
    override fun onComplete(req: Request, succeed: Boolean, code: Int, duration: Long) {
        val url = req.url()
        ReportUtil.addCustomNetworkReportData(API_METRICS_SERVER, url.host(), url.port(), req.method(), url.encodedPath(), code.toString(), duration)

        if ((code in 200..299) || code == 462) {
            ReportUtil.addCustomCounterReportData(COUNTER_TOPIC_METRIC_SERVER, COUNTER_METRIC_CONNECT_SUCCESS)
            ReportUtil.addCustomCounterReportData(COUNTER_TOPIC_METRIC_SERVER, COUNTER_METRIC_CONNECT_FAIL, false)
        } else {
            ReportUtil.addCustomCounterReportData(COUNTER_TOPIC_METRIC_SERVER, COUNTER_METRIC_CONNECT_FAIL)
            ReportUtil.addCustomCounterReportData(COUNTER_TOPIC_METRIC_SERVER, COUNTER_METRIC_CONNECT_SUCCESS, false)
        }
    }
}