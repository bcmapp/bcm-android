package com.bcm.messenger.common.bcmhttp

import com.bcm.messenger.common.bcmhttp.configure.sslfactory.IMServerSSL
import com.bcm.messenger.common.bcmhttp.interceptor.BcmAuthHeaderInterceptor
import com.bcm.messenger.common.bcmhttp.interceptor.RedirectInterceptorHelper
import com.bcm.messenger.common.bcmhttp.interceptor.error.IMServerErrorCodeInterceptor
import com.bcm.messenger.common.bcmhttp.interceptor.metrics.NormalMetricsInterceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object IMHttp : BcmBaseHttp() {
    init {
        val sslFactory = IMServerSSL()
        val client = OkHttpClient.Builder()
                .sslSocketFactory(sslFactory.getSSLFactory(), sslFactory.getTrustManager())
                .hostnameVerifier(trustAllHostVerify())
                .addInterceptor(BcmAuthHeaderInterceptor())
                .addInterceptor(RedirectInterceptorHelper.imServerInterceptor)
                .addInterceptor(IMServerErrorCodeInterceptor())
                .addInterceptor(NormalMetricsInterceptor())
                .readTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)
                .connectTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)
                .build()
        setClient(client)
    }
}