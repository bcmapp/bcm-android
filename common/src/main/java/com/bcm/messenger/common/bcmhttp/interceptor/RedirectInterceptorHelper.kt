package com.bcm.messenger.common.bcmhttp.interceptor

import com.bcm.messenger.common.bcmhttp.configure.IMServerUrl
import com.bcm.messenger.common.bcmhttp.configure.lbs.LBSManager
import com.bcm.messenger.common.bcmhttp.interceptor.RedirectInterceptor

object RedirectInterceptorHelper {
    val imServerInterceptor = RedirectInterceptor(LBSManager.imServerLbsType(), IMServerUrl.IM_DEFAULT)
    val metricsServerInterceptor = RedirectInterceptor(LBSManager.metricsServerLbsType(), IMServerUrl.METRICS_DEFAULT)
}