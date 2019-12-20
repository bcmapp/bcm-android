package com.bcm.messenger.adhoc.ui

import com.bcm.messenger.adhoc.R
import com.bcm.messenger.adhoc.logic.AdHocChannelLogic
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.utility.ble.BleUtil
import com.bcm.messenger.utility.gps.GPSUtil
import com.bcm.messenger.utility.wifi.WiFiUtil
import com.bcm.messenger.utility.AppContextHolder
import java.lang.StringBuilder

class AdHocDeviceStateListener: WiFiUtil.IWiFiStateNotify, BleUtil.IBleStateNotify, GPSUtil.IGPSStateNotify{
    private var listener:IDeviceStateListener? = null
    private var state = ""

    fun init() {
        WiFiUtil.stateNotify.addListener(this)
        GPSUtil.stateNotify.addListener(this)
        BleUtil.stateNotify.addListener(this)

        update()
    }

    fun unInit() {
        WiFiUtil.stateNotify.removeListener(this)
        GPSUtil.stateNotify.removeListener(this)
        BleUtil.stateNotify.removeListener(this)
    }

    fun refresh() {
        update()
    }

    fun getState(): String {
        return state
    }

    fun setListener(listener:IDeviceStateListener) {
        this.listener = listener
    }

    override fun onWiFiStateChanged() {
        update()
    }

    override fun onBLEStateChanged() {
        update()
    }

    override fun onGPSStateChanged() {
        update()
    }

    private fun update() {
        val stateBuilder = StringBuilder()
        if(!WiFiUtil.isEnable()) {
            stateBuilder.append(AppUtil.getString(R.string.adhoc_device_wifi))
        }

        if (!BleUtil.isEnable() && BleUtil.isSupport()) {
            if (stateBuilder.isNotEmpty()) {
                stateBuilder.append(',')
            }
            stateBuilder.append(AppUtil.getString(R.string.adhoc_device_ble))
        }

        if (!GPSUtil.isEnable()) {
            if (stateBuilder.isNotEmpty()) {
                stateBuilder.append(',')
            }

            stateBuilder.append(AppUtil.getString(R.string.adhoc_device_gps))
        }

        val error = (stateBuilder.isNotEmpty() && !AdHocChannelLogic.isOnline())
        val errorText = if (error) {
            AppContextHolder.APP_CONTEXT.getString(R.string.adhoc_device_state_error, stateBuilder.toString())
        } else {
            ""
        }

        if (this.state != errorText) {
            this.state = errorText
            listener?.onDeviceStateChanged(getState(), error)
        }
    }

    interface IDeviceStateListener {
        fun onDeviceStateChanged(newState:String, error:Boolean)
    }
}