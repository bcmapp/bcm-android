package com.bcm.messenger.adhoc.ui

import android.app.Activity
import com.bcm.messenger.adhoc.R
import com.bcm.messenger.adhoc.util.ScreenUtil
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.IAdHocModule
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.utility.ble.BleUtil
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.foreground.AppForeground
import com.bcm.messenger.utility.gps.GPSUtil
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.wifi.WiFiUtil

class AdHocDeviceRequire(private val activity: Activity) : WiFiUtil.IWiFiStateNotify,
        BleUtil.IBleStateNotify,
        GPSUtil.IGPSStateNotify,
        ScreenUtil.IScreenStateListener, AppForeground.IForegroundEvent {

    private val TAG = "AdHocDeviceRequire"
    private var checking: Boolean = false
    private var needRequire: Boolean = false
    private var screenOn: Boolean = true
    private val provider = AmeProvider.get<IAdHocModule>(ARouterConstants.Provider.PROVIDER_AD_HOC)

    fun require() {
        needRequire = true
        ScreenUtil.init(activity)
        ScreenUtil.addListener(this)
        GPSUtil.stateNotify.addListener(this)
        WiFiUtil.stateNotify.addListener(this)
        BleUtil.stateNotify.addListener(this)
        AppForeground.listener.addListener(this)
        checkRequirement()
    }

    fun unRequire() {
        needRequire = false
        GPSUtil.stateNotify.removeListener(this)
        WiFiUtil.stateNotify.removeListener(this)
        BleUtil.stateNotify.removeListener(this)
        AppForeground.listener.removeListener(this)
        ScreenUtil.unInit(activity)
        ScreenUtil.removeListener(this)
    }

    override fun onWiFiStateChanged() {
        checkRequirement()
    }

    override fun onBLEStateChanged() {
        checkRequirement()
    }

    override fun onGPSStateChanged() {
        checkRequirement()
    }

    override fun onScreenStateChanged(on: Boolean) {
        if (this.screenOn == on) {
            return
        }
        this.screenOn = on
        if (screenOn) {
            checkRequirement()
        }
    }

    fun checkRequirement() {
        if (!needRequire) {
            ALog.i(TAG, "checkRequirement not call require, return")
            return
        }
        if (!screenOn) {
            ALog.i(TAG, "checkRequirement not screen on, return")
            return
        }
        if (checking) {
            ALog.i(TAG, "checkRequirement checking doing and return")
            return
        }

        ALog.i(TAG, "checkRequirement checking")
        checking = true
        checkWiFi()
    }

    private fun checkWiFi() {
        if (!WiFiUtil.isEnable()) {
            ALog.i(TAG, "checkRequirement checking WiFi")
            WiFiUtil.enableWiFi(activity, AppUtil.getString(R.string.common_go_setting_wifi)) { it, cancel ->
                ALog.i(TAG, "checkRequirement WiFi enable $it")

                if (it) {
                    provider?.repairAdHocServer()
                }

                checking = false
                if (!cancel) {
                    checkWiFi()
                } else {
                    provider?.startAdHocServer(false)
                    checkBLE()
                }
            }
            return
        } else {
            provider?.startAdHocServer(true)
        }

        checkBLE()
    }


    private fun checkBLE() {
        if (BleUtil.isSupport()) {
            if (!BleUtil.isEnable()) {
                ALog.i(TAG, "checkRequirement checking BLE")
                BleUtil.enableBLE(activity, AppUtil.getString(R.string.common_ble_go_setting)) { it, cancel ->
                    ALog.i(TAG, "checkRequirement BLE enable $it")
                    if (it) {
                        provider?.repairAdHocScanner()
                    }

                    AmeDispatcher.mainThread.dispatch({
                        if (BleUtil.isEnable() && !it) {
                            provider?.repairAdHocScanner()
                        }
                        checking = false
                        if (!cancel) {
                            checkBLE()
                        } else {
                            provider?.startBroadcast(false)
                            checkGPS()
                        }
                    }, 500)
                }
                return
            } else {
                provider?.startBroadcast(true)
            }
        }
        checkGPS()
    }

    private fun checkGPS() {
        if (!GPSUtil.isEnable()) {
            ALog.i(TAG, "checkRequirement checking GPS")
            GPSUtil.enableGPS(activity, AppUtil.getString(R.string.common_gps_go_setting)) { it, cancel ->
                ALog.i(TAG, "checkRequirement GPS enable $it")
                AmeDispatcher.mainThread.dispatch({
                    provider?.repairAdHocScanner()

                    checking = false
                    if (!cancel) {
                        checkGPS()
                    } else {
                        provider?.startScan(false)
                        ALog.i(TAG, "checkRequirement check finished")
                    }
                }, 500)
            }
            return
        } else {
            provider?.startScan(true)
        }
        checking = false
        ALog.i(TAG, "checkRequirement check finished")
    }

    override fun onForegroundChanged(isForeground: Boolean) {
        if (isForeground) {
            checkRequirement()
        }
    }
}