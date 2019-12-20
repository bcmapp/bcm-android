package com.bcm.messenger.common.bcmhttp.configure.lbs

import android.net.Uri
import com.bcm.messenger.common.metrics.*
import com.bcm.messenger.utility.bcmhttp.utils.ServerCodeUtil
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.listener.SafeWeakListeners
import com.bcm.messenger.utility.logger.ALog
import com.bcm.netswitchy.configure.AmeConfigure
import com.bcm.netswitchy.configure.RxConfigHttp

class LBSFetcher(val type: String) {
    val listeners = SafeWeakListeners<ILBSFetchResult>()
    private var fetchingIndex = 0

    /**
     * @param fetchIndex 上一次请求的索引,防止请求重复触发
     */
    fun request(fetchIndex: Int) {
        AmeDispatcher.singleScheduler.scheduleDirect {
            if (fetchIndex + 1 <= this.fetchingIndex) {
                ALog.i("LBSFetcher", "fetching")
                return@scheduleDirect
            }
            fetchingIndex = fetchIndex + 1

            val url = "${RxConfigHttp.DIRECT_URL}${AmeConfigure.LBS_API}$type"
            val seedUri = Uri.parse(url)

            val startTime = System.currentTimeMillis()
            try {
                val lbs = AmeConfigure.queryLBS(url)
                val endTime = System.currentTimeMillis()

                ReportUtil.addCustomNetworkReportData(NET_TOPIC_LBS_SERVER, seedUri.host, seedUri.port, "", LBS_LBS, METRIC_SUCCESS, endTime - startTime)

                ReportUtil.addCustomCounterReportData(COUNTER_TOPIC_LBS, COUNTER_LBS_SUCCESS)
                ReportUtil.addCustomCounterReportData(COUNTER_TOPIC_LBS, COUNTER_LBS_FAIL, false)

                val imServerList = lbs.services.map { server -> ServerNode(ServerNode.SCHEME, server.ip, server.port, server.area, server.priority) }
                LBSManager.saveServerList(type, imServerList)

                listeners.forEach {
                    it.onLBSFetchResult(true, fetchingIndex)
                }
            } catch (e: Throwable) {
                val endTime = System.currentTimeMillis()
                val statusCode = ServerCodeUtil.getNetStatusCode(e)

                if (statusCode == ServerCodeUtil.CODE_PARSE_ERROR) {
                    //只是本地解析数据失败,应该算接口响应成功
                    ReportUtil.addCustomNetworkReportData(NET_TOPIC_LBS_SERVER, seedUri.host, seedUri.port, "", LBS_LBS, METRIC_SUCCESS, endTime - startTime)
                } else {
                    ReportUtil.addCustomNetworkReportData(NET_TOPIC_LBS_SERVER, seedUri.host, seedUri.port, "", LBS_LBS, METRIC_FAILED, endTime - startTime)
                }

                ReportUtil.addCustomCounterReportData(COUNTER_TOPIC_LBS, COUNTER_LBS_FAIL)
                ReportUtil.addCustomCounterReportData(COUNTER_TOPIC_LBS, COUNTER_LBS_SUCCESS, false)

                listeners.forEach {
                    it.onLBSFetchResult(false, fetchingIndex)
                }
            }
        }
    }

    fun refresh() {
        request(fetchingIndex)
    }

    interface ILBSFetchResult {
        fun onLBSFetchResult(succeed: Boolean, fetchIndex: Int)
    }
}