package com.bcm.messenger.common.bcmhttp

import com.bcm.messenger.common.bcmhttp.configure.sslfactory.IMServerSSL
import com.bcm.messenger.common.bcmhttp.interceptor.BcmAuthHeaderInterceptor
import com.bcm.messenger.common.bcmhttp.interceptor.RedirectInterceptorHelper
import com.bcm.messenger.common.bcmhttp.interceptor.error.IMServerErrorCodeInterceptor
import com.bcm.messenger.common.bcmhttp.interceptor.metrics.AccountMetricsInterceptor
import com.bcm.messenger.common.bcmhttp.interceptor.metrics.NormalMetricsInterceptor
import com.bcm.messenger.common.utils.AccountContextMap
import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object IMHttp: AccountContextMap<BaseHttp>({
    val http = BaseHttp()
    val sslFactory = IMServerSSL()
    val client = OkHttpClient.Builder()
            .sslSocketFactory(sslFactory.getSSLFactory(), sslFactory.getTrustManager())
            .hostnameVerifier(BaseHttp.trustAllHostVerify())
            .addInterceptor(BcmAuthHeaderInterceptor(it))
            .addInterceptor(RedirectInterceptorHelper.imServerInterceptor)
            .addInterceptor(IMServerErrorCodeInterceptor())
            .addInterceptor(AccountMetricsInterceptor(it))
            .readTimeout(BaseHttp.DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)
            .connectTimeout(BaseHttp.DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)
            .build()
    http.setClient(client)
    http
})