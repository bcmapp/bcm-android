package com.bcm.messenger.common.bcmhttp

import com.bcm.messenger.common.bcmhttp.configure.sslfactory.IMServerSSL
import com.bcm.messenger.common.bcmhttp.interceptor.BcmAuthHeaderInterceptor
import com.bcm.messenger.common.bcmhttp.interceptor.RedirectInterceptor
import com.bcm.messenger.common.bcmhttp.interceptor.RedirectInterceptorHelper
import com.bcm.messenger.common.bcmhttp.interceptor.error.IMServerErrorCodeInterceptor
import com.bcm.messenger.common.bcmhttp.interceptor.metrics.AccountMetricsInterceptor
import com.bcm.messenger.common.bcmhttp.interceptor.metrics.NormalMetricsInterceptor
import com.bcm.messenger.common.utils.AccountContextMap
import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp
import com.bcm.netswitchy.HookSystem
import com.bcm.netswitchy.proxy.ProxyConfigure
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object IMHttp: AccountContextMap<BcmBaseHttp>({
    val http = BcmBaseHttp()
    val client = IMHttp.baseHttpClient.newBuilder()
            .addInterceptor(BcmAuthHeaderInterceptor(it))
            .addInterceptor(RedirectInterceptorHelper.imServerInterceptor)
            .addInterceptor(IMServerErrorCodeInterceptor())
            .addInterceptor(AccountMetricsInterceptor(it))
            .build()
    http.setClient(client)
    http
}) {
    val baseHttpClient:OkHttpClient
    init {
        val sslFactory = IMServerSSL()
        val builder = OkHttpClient.Builder()
                .readTimeout(BaseHttp.DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)
                .connectTimeout(BaseHttp.DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)

        if (RedirectInterceptor.accessPoint == null) {
            builder.sslSocketFactory(sslFactory.getSSLFactory(), sslFactory.getTrustManager())
                    .hostnameVerifier(BaseHttp.trustAllHostVerify())
        }
        baseHttpClient = builder.build()
    }
}