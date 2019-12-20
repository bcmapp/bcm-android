package com.bcm.messenger.common.bcmhttp.interceptor.metrics

import com.bcm.messenger.common.metrics.ReportUtil
import okhttp3.Request

/**
 * 接口成功率打点
 */
class NormalMetricsInterceptor : MetricsInterceptor() {
    override fun onComplete(req: Request, succeed:Boolean, code:Int, duration: Long) {
        val url = req.url()
        if (succeed) {
            ReportUtil.addNetworkReportData(url.host(), url.port(), req.method(), url.encodedPath(), code.toString(), duration)
        } else {
            //lbs 接口响应状态上报
            ReportUtil.addNetworkReportData(url.host(), url.port(), req.method(), url.encodedPath(), code.toString(), duration)
        }
    }
}