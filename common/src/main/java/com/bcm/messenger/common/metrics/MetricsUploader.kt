package com.bcm.messenger.common.metrics

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.bcmhttp.interceptor.RedirectInterceptorHelper
import com.bcm.messenger.common.bcmhttp.configure.sslfactory.IMServerSSL
import com.bcm.messenger.common.bcmhttp.interceptor.BcmAuthHeaderInterceptor
import com.bcm.messenger.common.bcmhttp.interceptor.metrics.ReportMetricInterceptor
import com.bcm.messenger.utility.bcmhttp.exception.NoContentException
import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp
import com.bcm.messenger.utility.logger.ALog
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Created by Kin on 2019/8/19
 */
class MetricsUploader {
    private val tag = "MetricsUploader"

    private val METRICS_URL = "https://39.108.124.60:6666"
    private val reportPath = "${METRICS_URL}/v1/metrics/reports"
    private val configPath = "${METRICS_URL}/v1/metrics/config"


    @Throws(NoContentException::class)
    fun reportToServer(accountContext: AccountContext, reportData: ReportData): MetricsConfigs? {
        var configs: MetricsConfigs? = null

        try {
            configs = ReportHttp.getHttp(accountContext).put<MetricsConfigs>(reportPath, reportData.toJson(), MetricsConfigs::class.java)
        } catch (e: NoContentException) {
            throw e
        } catch (e: Throwable) {
            ALog.e(tag, e)
        }
        return configs
    }

    fun getTimeSplicesConfig(accountContext: AccountContext, configReq: HistogramConfigReq): MetricsConfigs? {
        var configs: MetricsConfigs? = null

        try {
            configs = ReportHttp.getHttp(accountContext).post<MetricsConfigs>(configPath, configReq.toJson(), MetricsConfigs::class.java)
        } catch (e: Throwable) {
            ALog.e(tag, e)
        }

        return configs
    }
}