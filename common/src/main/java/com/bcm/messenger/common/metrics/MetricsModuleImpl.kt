package com.bcm.messenger.common.metrics

import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.route.annotation.Route
import com.bcm.messenger.common.provider.accountmodule.IMetricsModule

/**
 * Created by Kin on 2019/8/27
 */
@Route(routePath = ARouterConstants.Provider.REPORT_BASE)
class MetricsModuleImpl : IMetricsModule {
    private lateinit var accountContext: AccountContext
    override val context: AccountContext
        get() = accountContext

    override fun setContext(context: AccountContext) {
        this.accountContext = context
    }

    override fun initModule() {

    }

    override fun uninitModule() {

    }

    override fun launchEnd() {
        ReportUtil.launchEnded()
    }

    override fun setAdhocRunning(isRunning: Boolean) {
        ReportUtil.setAdhocRunning(isRunning)
    }

    override fun addNetworkReportData(serverIp: String?, port: Int, reqMethod: String?, path: String?, returnCode: String, time: Long) {
        ReportUtil.addNetworkReportData(accountContext, serverIp, port, reqMethod, path, returnCode, time)
    }

    override fun addLbsNetworkReportData(serverIp: String?, port: Int, reqMethod: String?, path: String?, returnCode: String, time: Long) {
        ReportUtil.addLbsNetworkReportData(accountContext, serverIp, port, reqMethod, path, returnCode, time)
    }

    override fun addCallNetworkReportData(serverIp: String?, port: Int, reqMethod: String?, path: String?, returnCode: String, time: Long) {
        ReportUtil.addCallNetworkReportData(accountContext, serverIp, port, reqMethod, path, returnCode, time)
    }

    override fun addCustomNetworkReportData(topic: String, serverIp: String?, port: Int, reqMethod: String?, path: String?, returnCode: String, time: Long) {
        ReportUtil.addCustomNetworkReportData(accountContext, topic, serverIp, port, reqMethod, path, returnCode, time)
    }

    override fun addDataErrorReportData(exceptionName: String) {
        ReportUtil.addDataErrorReportData(accountContext, exceptionName)
    }

    override fun addSystemErrorReportData(exceptionName: String) {
        ReportUtil.addSystemErrorReportData(accountContext, exceptionName)
    }

    override fun addCustomCounterReportData(topic: String, counterName: String, increment: Boolean) {
        ReportUtil.addCustomCounterReportData(accountContext, topic, counterName, increment)
    }
}