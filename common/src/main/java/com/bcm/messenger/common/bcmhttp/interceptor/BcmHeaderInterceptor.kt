package com.bcm.messenger.common.bcmhttp.interceptor

import com.bcm.messenger.common.core.SystemUtils
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.logger.ALog
import com.orhanobut.logger.Logger
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response

open class BcmHeaderInterceptor : Interceptor {
    private val HEAD_CLIENT = "X-Client-Version"
    private val headerMap = HashMap<String, String>()
    private val systemConfig = "BCM Android/${SystemUtils.getSimpleSystemVersion()} Model/${SystemUtils.getSimpleSystemInfo()} " +
            "Version/${SystemUtils.getVersionName()} Build/${SystemUtils.getVersionCode()} "

    fun setHeader(key: String, value: String) {
        headerMap[key] = value
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val area = RedirectInterceptorHelper.imServerInterceptor.getCurrentServer().area
        val clientParams = systemConfig + "Area/${area} Lang/${SystemUtils.getUseLanguage()}"
        setHeader(HEAD_CLIENT, clientParams)

        val requestBuilder = request.newBuilder()
        try {
            for ((key, value) in headerMap) {
                requestBuilder.header(key, value)
            }
        } catch (e: Exception) {
            Logger.e("intercept header error: $e")
        }

        val duration = System.currentTimeMillis()
        val response = chain.proceed(requestBuilder.build())
        val adjust = (System.currentTimeMillis() - duration) / 2

        //保存服务器的时间截
        saveServerTimeDelta(response.headers(), adjust)

        return response
    }

    private fun saveServerTimeDelta(headersList: Headers?, adjust: Long) {
        val time = headersList?.getDate("Date")?.time ?: 0
        if (time > 0) {
            if (adjust > 250) {
                ALog.i("AmeTimeUtil", "saveServerTimeDelta ignore")
                return
            }

            AmeTimeUtil.updateServerTimeMillis(time - adjust)
        }
    }
}