package com.bcm.messenger.common.metrics

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.bcmhttp.configure.sslfactory.IMServerSSL
import com.bcm.messenger.common.bcmhttp.interceptor.BcmAuthHeaderInterceptor
import com.bcm.messenger.common.bcmhttp.interceptor.RedirectInterceptorHelper
import com.bcm.messenger.common.bcmhttp.interceptor.metrics.ReportMetricInterceptor
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.bcmhttp.facade.AmeEmpty
import com.bcm.messenger.utility.bcmhttp.exception.NoContentException
import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp
import com.bcm.messenger.utility.bcmhttp.utils.ServerCodeUtil
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

class ReportHttp(accountContext: AccountContext) : BaseHttp() {
    companion object {
        private val httpClients = HashMap<AccountContext, ReportHttp>()

        fun getHttp(accountContext: AccountContext): ReportHttp {
            synchronized(httpClients) {
                var http = httpClients[accountContext]
                if (null == http) {
                    http = ReportHttp(accountContext)
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
        val clientBuilder = OkHttpClient.Builder()
                .sslSocketFactory(sslFactory.getSSLFactory(), sslFactory.getTrustManager())
                .hostnameVerifier(trustAllHostVerify())
                .addInterceptor(RedirectInterceptorHelper.metricsServerInterceptor)
                .addInterceptor(ReportMetricInterceptor())
                .addInterceptor(BcmAuthHeaderInterceptor(accountContext))
                .readTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)
                .writeTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)
                .connectTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)

        setClient(clientBuilder.build())
    }

    @Throws(IOException::class, NoContentException::class, HttpErrorException::class)
    fun <T> post(url: String, body: String, typeOfT: Type): T {
        val response = postString()
                .url(url)
                .addHeader("content-type", "application/json")
                .content(body)
                .build()
                .writeTimeOut(DEFAULT_MILLISECONDS)
                .readTimeOut(DEFAULT_MILLISECONDS)
                .connTimeOut(DEFAULT_MILLISECONDS)
                .buildCall().execute()
        return parseResponse(response, typeOfT)
    }

    @Throws(IOException::class, NoContentException::class, HttpErrorException::class)
    fun <T> put(url: String, body: String, typeOfT: Type): T {
        val response = putString()
                .url(url)
                .addHeader("content-type", "application/json")
                .content(body)
                .build()
                .writeTimeOut(DEFAULT_MILLISECONDS)
                .readTimeOut(DEFAULT_MILLISECONDS)
                .connTimeOut(DEFAULT_MILLISECONDS)
                .buildCall()
                .execute()

        return parseResponse(response, typeOfT)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> parseResponse(response: Response?, typeOfT: Type): T {
        try {
            if (response?.code() == 462 || response?.isSuccessful == true) {
                val resp = response.body()?.string()
                return when {
                    typeOfT == AmeEmpty::class.java -> AmeEmpty() as T
                    resp.isNullOrEmpty() -> throw NoContentException()
                    typeOfT == String::class.java -> resp as T
                    else -> try {
                        GsonUtils.fromJson<T>(resp, typeOfT)
                                ?: throw HttpErrorException(0, "parse error")
                    } catch (e: Throwable) {
                        throw HttpErrorException(ServerCodeUtil.CODE_PARSE_ERROR, "")
                    }
                }
            } else {
                throw HttpErrorException(response?.code()
                        ?: -200, response?.message() ?: "")
            }
        } finally {
            try {
                response?.close()
            } catch (e: Throwable) {
            }
        }
    }

}