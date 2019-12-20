package com.bcm.messenger.common.bcmhttp.interceptor.error

import okhttp3.Interceptor
import okhttp3.Response

abstract class BcmErrorInterceptor: Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!response.isSuccessful) {
            onError(response)
        }
        return response
    }

    abstract fun onError(response: Response)
}