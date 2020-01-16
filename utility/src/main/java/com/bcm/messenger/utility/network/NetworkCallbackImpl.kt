package com.bcm.messenger.utility.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.AppContextHolder

class NetworkCallbackImpl(private val type:String, network:Network?): ConnectivityManager.NetworkCallback() {
    companion object {
        private const val TAG = "Network"
    }
    private var connectNetwork:Network? = network
    private var callback: StatusCallback? = null

    init {
    }

    fun setCallback(callback: StatusCallback) {
        this.callback = callback
    }

    fun updateNetwork(network: Network) {
        this.connectNetwork = network
    }

    override fun onAvailable(network: Network) {
        super.onAvailable(network)
        ALog.i(TAG, "$type onAvailable ")

        connectNetwork = network

        callback?.onNetworkStateChanged()
    }

    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities?) {
        super.onCapabilitiesChanged(network, networkCapabilities)
        ALog.i(TAG, "$type onCapabilitiesChanged")
    }

    override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties?) {
        super.onLinkPropertiesChanged(network, linkProperties)
        ALog.i(TAG, "$type onLinkPropertiesChanged")
    }

    override fun onLosing(network: Network, maxMsToLive: Int) {
        super.onLosing(network, maxMsToLive)
        ALog.i(TAG, "$type onLosing")
    }

    override fun onLost(network: Network) {
        super.onLost(network)
        ALog.i(TAG, "$type onLost")

        connectNetwork = null
        callback?.onNetworkStateChanged()
    }

    override fun onUnavailable() {
        super.onUnavailable()
        ALog.i(TAG, "$type onUnavailable")
        connectNetwork = null
    }

    fun isConnected(): Boolean {
        val network = this.connectNetwork
        val cm = AppContextHolder.APP_CONTEXT.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager?:return false
        val networkInfo = cm.getNetworkInfo(network?:return false)?:return false
        return networkInfo.isConnected
    }


    interface StatusCallback {
        fun onNetworkStateChanged()
    }
}