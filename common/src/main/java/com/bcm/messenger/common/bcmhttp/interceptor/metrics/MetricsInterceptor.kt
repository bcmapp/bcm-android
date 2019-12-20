package com.bcm.messenger.common.bcmhttp.interceptor.metrics

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

abstract class MetricsInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val t = System.currentTimeMillis()
        val request = chain.request()
        var code = -200
        var succeed = false

        try {
            val response = chain.proceed(request)
            code = response.code()
            succeed = response.isSuccessful

            return response
        } catch (e: Throwable) {
            throw e
        } finally {
            onComplete(request, succeed, code, System.currentTimeMillis() - t)
        }
    }

    abstract fun onComplete(req: Request, succeed: Boolean, code: Int, duration: Long)
}