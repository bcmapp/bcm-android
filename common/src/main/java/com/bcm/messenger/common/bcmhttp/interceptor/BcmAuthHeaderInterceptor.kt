package com.bcm.messenger.common.bcmhttp.interceptor

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.utility.logger.ALog
import okhttp3.Interceptor
import okhttp3.Response

class BcmAuthHeaderInterceptor(private val accountContext: AccountContext) : BcmHeaderInterceptor() {
    companion object {
        private const val HEAD_AUTH = "Authorization"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = accountContext.token
        if (token.isNotEmpty()) {
            setHeader(HEAD_AUTH, token)
        } else {
            ALog.w("BcmAuthHeaderInterceptor", "token missed")
        }

        return super.intercept(chain)
    }
}