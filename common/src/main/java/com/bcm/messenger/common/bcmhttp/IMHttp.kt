package com.bcm.messenger.common.bcmhttp

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.bcmhttp.configure.sslfactory.IMServerSSL
import com.bcm.messenger.common.bcmhttp.interceptor.BcmAuthHeaderInterceptor
import com.bcm.messenger.common.bcmhttp.interceptor.RedirectInterceptorHelper
import com.bcm.messenger.common.bcmhttp.interceptor.error.IMServerErrorCodeInterceptor
import com.bcm.messenger.common.bcmhttp.interceptor.metrics.NormalMetricsInterceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class IMHttp(accountContext: AccountContext) : BcmBaseHttp() {
    companion object {
        private val httpClients = HashMap<AccountContext, IMHttp>()

        fun getHttp(accountContext: AccountContext): IMHttp {
            synchronized(httpClients) {
                var http = httpClients[accountContext]
                if (null == http) {
                    http = IMHttp(accountContext)
                    httpClients[accountContext] = http
                }
                return http
            }
        }

        fun removeHttp(accountContext: AccountContext) {
            synchronized(httpClients) {
                httpClients.remove(accountContext)

                val gcList = httpClients.keys.filter { !it.isLogin }
                gcList.forEach {
                    httpClients.remove(it)
                }
            }
        }
    }

    init {
        val sslFactory = IMServerSSL()
        val client = OkHttpClient.Builder()
                .sslSocketFactory(sslFactory.getSSLFactory(), sslFactory.getTrustManager())
                .hostnameVerifier(trustAllHostVerify())
                .addInterceptor(BcmAuthHeaderInterceptor(accountContext))
                .addInterceptor(RedirectInterceptorHelper.imServerInterceptor)
                .addInterceptor(IMServerErrorCodeInterceptor())
                .addInterceptor(NormalMetricsInterceptor())
                .readTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)
                .connectTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)
                .build()
        setClient(client)
    }
}