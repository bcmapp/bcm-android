package com.bcm.messenger.utility.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object NetworkUtil: NetworkCallbackImpl.StatusCallback {
    private lateinit var wiFiCallback: NetworkCallbackImpl
    private lateinit var mobileCallback: NetworkCallbackImpl
    private val listenerSet = Collections.newSetFromMap(ConcurrentHashMap<IConnectionListener, Boolean>())

    enum class NetType(val typeName: String) {
        WiFi("WiFi"),
        MOBILE_5G("5G"),
        MOBILE_4G("4G"),
        MOBILE_3G("3G"),
        MOBILE_GPRS("GPRS"),
        MOBILE_OTHER("other"),
        MOBILE_UNKNOWN("unknown");

        fun isMobile(): Boolean {
            return this != WiFi
        }
    }


    fun init(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        var wiFiNetwork:Network? = null
        var mobileNetwork:Network? = null

        val activeNetworkInfo = cm.activeNetworkInfo
        val networkList = cm.allNetworks

        if (activeNetworkInfo != null && activeNetworkInfo.isConnected && networkList?.isNotEmpty() == true) {
            if (activeNetworkInfo.type == ConnectivityManager.TYPE_WIFI) {
                wiFiNetwork = networkList.last()
            } else {
                mobileNetwork = networkList.last()
            }
        }

        wiFiCallback = NetworkCallbackImpl("Wi-Fi", wiFiNetwork)
        mobileCallback = NetworkCallbackImpl("Mobile", mobileNetwork)

        wiFiCallback.setCallback(this)
        mobileCallback.setCallback(this)

        val wiFiBuilder = NetworkRequest.Builder()
        wiFiBuilder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)

        val wiFiRequest = wiFiBuilder.build()
        cm.registerNetworkCallback(wiFiRequest, wiFiCallback)


        val mobileBuilder = NetworkRequest.Builder()
        mobileBuilder.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)

        val mobileRequest = mobileBuilder.build()
        cm.registerNetworkCallback(mobileRequest, mobileCallback)
    }


    fun unInit(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager?:return
        cm.unregisterNetworkCallback(mobileCallback)
        cm.unregisterNetworkCallback(wiFiCallback)
    }

    /**
     * @param listener
     */
    fun addListener(listener:IConnectionListener) {
        listenerSet.add(listener)
    }

    /**
     * @param listener
     */
    fun removeListener(listener: IConnectionListener) {
        listenerSet.remove(listener)
    }

    fun isConnected(): Boolean {
        return wiFiCallback.isConnected() || mobileCallback.isConnected()
    }


    fun isWiFi(): Boolean {
        return wiFiCallback.isConnected()
    }

    fun isMobile(): Boolean {
        return mobileCallback.isConnected()
    }

    override fun onNetworkStateChanged() {
        AmeDispatcher.mainThread.dispatch {
            listenerSet.forEach {
                it.onNetWorkStateChanged()
            }
        }
    }

    fun isWiFiEnable(): Boolean {
        val wifiManager: WifiManager = AppContextHolder.APP_CONTEXT.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifiManager.isWifiEnabled
    }

    fun netType(): NetType {
        try {
            val manager = AppContextHolder.APP_CONTEXT.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val info = manager.activeNetworkInfo
            if (isConnected()) {
                if (isWiFi()) {
                    return NetType.WiFi
                } else {
                    return when (info.subtype) {
                        TelephonyManager.NETWORK_TYPE_LTE -> NetType.MOBILE_4G
                        TelephonyManager.NETWORK_TYPE_HSDPA,
                        TelephonyManager.NETWORK_TYPE_UMTS,
                        TelephonyManager.NETWORK_TYPE_EVDO_0,
                        TelephonyManager.NETWORK_TYPE_EVDO_A,
                        TelephonyManager.NETWORK_TYPE_EVDO_B,
                        TelephonyManager.NETWORK_TYPE_TD_SCDMA,
                        TelephonyManager.NETWORK_TYPE_HSPA,
                        TelephonyManager.NETWORK_TYPE_HSPAP -> NetType.MOBILE_3G
                        TelephonyManager.NETWORK_TYPE_GSM,
                        TelephonyManager.NETWORK_TYPE_GPRS,
                        TelephonyManager.NETWORK_TYPE_EDGE,
                        TelephonyManager.NETWORK_TYPE_CDMA -> NetType.MOBILE_GPRS
                        else -> NetType.MOBILE_OTHER
                    }
                }
            }
        } catch (ex: Exception) { }
        return NetType.MOBILE_UNKNOWN
    }
}