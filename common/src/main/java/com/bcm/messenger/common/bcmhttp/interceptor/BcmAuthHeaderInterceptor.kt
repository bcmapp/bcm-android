package com.bcm.messenger.common.bcmhttp.interceptor

import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.utility.logger.ALog
import okhttp3.Interceptor
import okhttp3.Response

class BcmAuthHeaderInterceptor : BcmHeaderInterceptor() {
    companion object {
        private const val HEAD_AUTH = "Authorization"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = AMESelfData.token
        if (token.isNotEmpty()) {
            setHeader(HEAD_AUTH, AMESelfData.token)
        } else {
            ALog.w("BcmAuthHeaderInterceptor", "token missed")
        }

        return super.intercept(chain)
    }
}