package com.bcm.messenger.common.bcmhttp

import com.bcm.messenger.common.bcmhttp.configure.sslfactory.IMServerSSL
import com.bcm.messenger.common.bcmhttp.interceptor.BcmAuthHeaderInterceptor
import com.bcm.messenger.common.bcmhttp.interceptor.RedirectInterceptorHelper
import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class WebSocketHttp:BaseHttp() {
    init {
        val sslFactory = IMServerSSL()
        val client  = OkHttpClient.Builder()
                .sslSocketFactory(sslFactory.getSSLFactory(), sslFactory.getTrustManager())
                .hostnameVerifier(trustAllHostVerify())
                .addInterceptor(BcmAuthHeaderInterceptor())
                .addInterceptor(RedirectInterceptorHelper.imServerInterceptor)
                .connectTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)
                .readTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)
                .writeTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)
                .build()

        setClient(client)
    }

    fun connect(url:String, userAgent:String, listener:WebSocketListener):WebSocket {
        val requestBuilder = Request.Builder().url(url)

        if (userAgent.isNotEmpty()) {
            requestBuilder.addHeader("X-Signal-Agent", userAgent)
        }

        return client.newWebSocket(requestBuilder.build(), listener)
    }
}