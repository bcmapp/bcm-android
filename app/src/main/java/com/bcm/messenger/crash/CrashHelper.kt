package com.bcm.messenger.crash

import android.util.Log
import com.bcm.messenger.BuildConfig
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.bcmhttp.configure.sslfactory.IMServerSSL
import com.bcm.messenger.common.bcmhttp.interceptor.metrics.NormalMetricsInterceptor
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp
import com.bcm.messenger.utility.bcmhttp.interceptor.ProgressInterceptor
import com.bcm.messenger.utility.logger.AmeLogConfig
import com.sdk.crashreport.CrashReport
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "CrashReport"

fun initCrash() {
    val CRASH_URL = "https://reporting.bcm.social:8070/crash/reporting"
    val ANR_URL = "https://reporting.bcm.social:8070/anr/reporting"

    val version = when {
        BuildConfig.DEBUG -> "${AppUtil.getVersionName(AppContextHolder.APP_CONTEXT)}_debug"
        !BuildConfig.FLAVOR.startsWith(ARouterConstants.CONSTANT_RELEASE) -> "${AppUtil.getVersionName(AppContextHolder.APP_CONTEXT)}_test"
        else -> AppUtil.getVersionName(AppContextHolder.APP_CONTEXT)
    }
    CrashReport.setAppVersion(version)

    val sslFactory = IMServerSSL()
    val clientCrash = OkHttpClient.Builder()
            .sslSocketFactory(sslFactory.getSSLFactory(), sslFactory.getTrustManager())
            .hostnameVerifier(BaseHttp.trustAllHostVerify())
            .addInterceptor(NormalMetricsInterceptor())
            .addInterceptor(ProgressInterceptor())
            .readTimeout(BaseHttp.DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)
            .connectTimeout(BaseHttp.DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)
            .build()

    val builder = CrashReport.CrashReportBuilder()
            .setAnrUrl(ANR_URL)
            .setCrashUrl(CRASH_URL)
            .setHttpClient(clientCrash)
            .setAppId("ame-android")
            .setAppMarket(BuildConfig.FLAVOR)
            .setContext(AppContextHolder.APP_CONTEXT)

    CrashReport.init(builder)
    CrashReport.startANRDetecting(AppContextHolder.APP_CONTEXT)
}

fun setCrashReportLogs() {
    val list = File(AmeLogConfig.logDir).listFiles()?.map { f -> f.absolutePath }
    list?.let {
        Log.d(TAG, " logCount:" + it.size + " list: " + it.toString())
        if (list.isNotEmpty()){
            CrashReport.setUserLogList(it)
        }
    }
}