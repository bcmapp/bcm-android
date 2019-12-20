package com.bcm.messenger.common.metrics

import com.bcm.messenger.common.ARouterConstants
import com.bcm.route.annotation.Route
import com.bcm.messenger.common.provider.IMetricsModule

/**
 * Created by Kin on 2019/8/27
 */
@Route(routePath = ARouterConstants.Provider.REPORT_BASE)
class MetricsModuleImpl : IMetricsModule {
    override fun initModule() {

    }

    override fun uninitModule() {

    }

    override fun addNetworkReportData(serverIp: String?, port: Int, reqMethod: String?, path: String?, returnCode: String, time: Long) {
        ReportUtil.addNetworkReportData(serverIp, port, reqMethod, path, returnCode, time)
    }

    override fun addLbsNetworkReportData(serverIp: String?, port: Int, reqMethod: String?, path: String?, returnCode: String, time: Long) {
        ReportUtil.addLbsNetworkReportData(serverIp, port, reqMethod, path, returnCode, time)
    }

    override fun addCallNetworkReportData(serverIp: String?, port: Int, reqMethod: String?, path: String?, returnCode: String, time: Long) {
        ReportUtil.addCallNetworkReportData(serverIp, port, reqMethod, path, returnCode, time)
    }

    override fun addCustomNetworkReportData(topic: String, serverIp: String?, port: Int, reqMethod: String?, path: String?, returnCode: String, time: Long) {
        ReportUtil.addCustomNetworkReportData(topic, serverIp, port, reqMethod, path, returnCode, time)
    }

    override fun addDataErrorReportData(exceptionName: String) {
        ReportUtil.addDataErrorReportData(exceptionName)
    }

    override fun addSystemErrorReportData(exceptionName: String) {
        ReportUtil.addSystemErrorReportData(exceptionName)
    }

    override fun addCustomCounterReportData(topic: String, counterName: String, increment: Boolean) {
        ReportUtil.addCustomCounterReportData(topic, counterName, increment)
    }
}