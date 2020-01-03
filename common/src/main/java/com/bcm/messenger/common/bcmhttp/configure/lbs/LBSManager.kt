package com.bcm.messenger.common.bcmhttp.configure.lbs

import com.bcm.messenger.common.BuildConfig
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.isLbsEnable
import com.bcm.messenger.common.utils.isTestEnvEnable
import com.bcm.messenger.utility.network.INetworkConnectionListener
import com.bcm.messenger.utility.network.NetworkUtil
import java.util.concurrent.ConcurrentHashMap

object LBSManager : INetworkConnectionListener {
    private val lbsFetcherMap = ConcurrentHashMap<String, LBSFetcher>()
    private val serverCache = ConcurrentHashMap<String, ServerStorage>()
    private var networkType = NetworkUtil.netType()

    init {
        NetworkUtil.addListener(this)
    }

    fun addListener(lbsFetchResult: LBSFetcher.ILBSFetchResult) {
        getFetcher(imServerLbsType()).listeners.addListener(lbsFetchResult)
    }

    fun addMetricsListener(lbsFetchResult: LBSFetcher.ILBSFetchResult) {
        getFetcher(metricsServerLbsType()).listeners.addListener(lbsFetchResult)
    }

    fun query(type: String, fetchingIndex: Int = 0, listener: LBSFetcher.ILBSFetchResult) {
        val fetcher = getFetcher(type)
        fetcher.listeners.addListener(listener)
        fetcher.request(fetchingIndex)
    }

    private fun getFetcher(type: String): LBSFetcher {
        var fetcher = lbsFetcherMap[type]
        if (fetcher == null) {
            fetcher = LBSFetcher(type)
            lbsFetcherMap[type] = fetcher
        }
        return fetcher
    }

    fun refresh() {
        if (lbsFetcherMap.isEmpty()) {
            getFetcher(imServerLbsType())
            getFetcher(metricsServerLbsType())
        }

        lbsFetcherMap.values.forEach {
            it.refresh()
        }
    }

    /**
     * 
     */
    fun getServerList(lbsType: String): List<ServerNode> {
        return getStorage(lbsType).getLastServerList()
    }

    /**
     * 
     */
    fun saveServerList(lbsType: String, ipList: List<ServerNode>) {
        getStorage(lbsType).saveServerList(ipList)
    }

    private fun getStorage(lbsType: String): ServerStorage {
        var store = serverCache[lbsType]
        return if (null != store) {
            store
        } else {
            store = ServerStorage(lbsType)
            serverCache[lbsType] = store
            store
        }
    }

    fun imServerLbsType(): String {
        return if (AppUtil.isReleaseBuild() || !AppUtil.isTestEnvEnable()) {
            BuildConfig.IM_SERVICE_NAME
        } else {
            BuildConfig.IM_TEST_SERVICE_NAME
        }
    }

    fun metricsServerLbsType(): String {
        return when {
            isLbsEnable() && isTestEnvEnable() -> BuildConfig.IM_METRIC_TEST_LBS_NAME
            isLbsEnable() -> BuildConfig.IM_METRIC_NAME
            else -> BuildConfig.IM_METRIC_TEST_NAME
        }
    }

    override fun onNetWorkStateChanged() {
        if (NetworkUtil.isConnected()) {
            if (networkType != NetworkUtil.netType()) {
                networkType = NetworkUtil.netType()
                refresh()
            }
        }
    }
}