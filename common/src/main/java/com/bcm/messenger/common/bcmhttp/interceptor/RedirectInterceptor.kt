package com.bcm.messenger.common.bcmhttp.interceptor

import com.bcm.messenger.common.bcmhttp.configure.IMServerUrl
import com.bcm.messenger.common.bcmhttp.configure.lbs.LBSFetcher
import com.bcm.messenger.common.bcmhttp.configure.lbs.LBSManager
import com.bcm.messenger.common.bcmhttp.imserver.*
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.utility.bcmhttp.utils.ServerCodeUtil
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.network.NetworkUtil
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicInteger

/**
 * ip
 */
class RedirectInterceptor(private val lbsType: String, private val defaultServer: IMServerUrl) : Interceptor {
    companion object {
        var accessPoint: AccessPointIMServerIterator? = null
    }
    private var lbsFetchIndex = 0
    private var serverIterator: IServerIterator
    private var currentServer: IMServerUrl
    private val failedTimes = AtomicInteger(0)

    init {
        if (AccessPointConfigure.isEnable && AccessPointConfigure.current.isNotEmpty()) {
            accessPoint = AccessPointIMServerIterator(AccessPointConfigure.current)
        }

        serverIterator = if (AppUtil.isReleaseBuild() || AppUtil.isLbsEnable()) {
            IMServerIterator(LBSManager.getServerList(lbsType), defaultServer)
        } else {
            if (lbsType == LBSManager.metricsServerLbsType()) {
                DevMetricsServerIterator()
            } else {
                DevIMServerIterator()
            }
        }
        currentServer = serverIterator.next()
    }

    private val listener = object : LBSFetcher.ILBSFetchResult {
        override fun onLBSFetchResult(succeed: Boolean, fetchIndex: Int) {
            if (AppUtil.isReleaseBuild() || AppUtil.isLbsEnable()) {
                if (succeed) {
                    serverIterator = IMServerIterator(LBSManager.getServerList(lbsType), defaultServer)
                    currentServer = serverIterator.next()
                }
            }
            lbsFetchIndex = fetchIndex
        }
    }

    init {
        LBSManager.query(AMELogin.majorContext,lbsType, lbsFetchIndex, listener)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val server = getCurrentServer()

        val request = chain.request()
        val redirectUrlBuilder = request.url()
                .newBuilder()
                .host(server.ip)

        if (server.port > 0) {
            redirectUrlBuilder.port(server.port)
        }

        val redirectUrl = redirectUrlBuilder.build().url().toString()
        ALog.logForSecret("RedirectInterceptor", redirectUrl)

        val newRequestBuilder = request.newBuilder().url(redirectUrlBuilder.build())

        var exception: Throwable? = null
        val response = try {
            chain.proceed(newRequestBuilder.build())
        } catch (e: Throwable) {
            exception = e
            null
        }

        if (response?.isSuccessful == true) {
            failedTimes.set(0)
        } else if (serverOrNetWorkFailed(response)) {
            //3，
            val times = failedTimes.get()
            if (times >= 3) {
                failedTimes.set(0)
                getNextServer()
            } else {
                failedTimes.set(times+1)
            }
        }

        if (null != exception) {
            ALog.logForSecret("RedirectInterceptor", redirectUrl, exception)
            throw exception
        }

        ALog.logForSecret("RedirectInterceptor", "$redirectUrl resp ${response!!.code()}")

        return response
    }

    private fun serverOrNetWorkFailed(response: Response?): Boolean {
        if (!NetworkUtil.isConnected()) {
            //，
            return false
        }

        if (null == response) {
            return true
        }

        if (response.code() == ServerCodeUtil.CODE_SERVICE_460
                || response.code() > ServerCodeUtil.CODE_SERVICE_500) {
            return true
        }

        return false
    }


    fun getCurrentServer(): IMServerUrl {
        //，lbs
        val accessPoint = RedirectInterceptor.accessPoint
        if (accessPoint == null || !accessPoint.isValid()) {
            if (!serverIterator.isValid()) {
                LBSManager.query(AMELogin.majorContext, lbsType, lbsFetchIndex, listener)
            }
        } else {
            return accessPoint.next()
        }

        return currentServer
    }

    private fun getNextServer(): IMServerUrl {
        currentServer = serverIterator.next()

        //，lbs
        if (!serverIterator.isValid()) {
            LBSManager.query(AMELogin.majorContext, lbsType, lbsFetchIndex, listener)
        }
        return currentServer
    }
}