package com.bcm.messenger.common.provider

/**
 * Created by Kin on 2019/8/27
 */
interface IMetricsModule : IAmeModule {
    fun addNetworkReportData(serverIp: String?, port: Int, reqMethod: String?, path: String?, returnCode: String, time: Long)

    fun addLbsNetworkReportData(serverIp: String?, port: Int, reqMethod: String?, path: String?, returnCode: String, time: Long)

    fun addCallNetworkReportData(serverIp: String?, port: Int, reqMethod: String?, path: String?, returnCode: String, time: Long)

    fun addCustomNetworkReportData(topic: String, serverIp: String?, port: Int, reqMethod: String?, path: String?, returnCode: String, time: Long)

    fun addDataErrorReportData(exceptionName: String)

    fun addSystemErrorReportData(exceptionName: String)

    fun addCustomCounterReportData(topic: String, counterName: String, increment: Boolean = true)
}