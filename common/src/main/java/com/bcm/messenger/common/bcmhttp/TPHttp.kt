package com.bcm.messenger.common.bcmhttp

import com.bcm.messenger.common.bcmhttp.interceptor.metrics.NormalMetricsInterceptor
import com.bcm.messenger.utility.bcmhttp.interceptor.ProgressInterceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object TPHttp: BcmBaseHttp() {
    init {
        val client = OkHttpClient.Builder()
                .addInterceptor(NormalMetricsInterceptor())
                .addInterceptor(ProgressInterceptor())
                .readTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)
                .connectTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)
                .build()
        setClient(client)
    }
}