package com.bcm.messenger.utility.wifi

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.provider.Settings
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.ble.TipShowUtil
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.foreground.AppForeground
import com.bcm.messenger.utility.listener.SafeWeakListeners
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.permission.PermissionUtil
import io.reactivex.disposables.Disposable

object WiFiUtil {
    private val TAG = "WiFiUtil"
    private var receiver:BroadcastReceiver?= null
    private var enable = false

    val stateNotify = SafeWeakListeners<IWiFiStateNotify>()

    fun init(context: Context) {
        enable = isEnable()
        receiver = object :BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val b = isEnable()
                ALog.i(TAG, "onReceive wifi enable: $b")
                if (b != enable) {
                    enable = b
                    stateNotify.forEach { it.onWiFiStateChanged() }
                }
            }
        }

        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        context.registerReceiver(receiver, intentFilter)
    }

    fun unInit(context: Context) {
        context.unregisterReceiver(receiver ?:return)
    }

    fun isEnable(): Boolean {
        val wifiManager: WifiManager = AppContextHolder.APP_CONTEXT.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifiManager.isWifiEnabled
    }


    fun enableWiFi(activity: Activity, error:String, result:(enable:Boolean, cancel:Boolean)->Unit) {
        if (isEnable()) {
            result(true, false)
            return
        }

        val language = PermissionUtil.languageSetting
        if (null == language) {
            result(isEnable(), false)
            return
        }

        TipShowUtil.show(activity, language.permissionTitle(), error, language.permissionGoSetting(), language.permissionCancel()) {
            go, cancel->
            if(go) {
                var receiver:BroadcastReceiver? = null
                receiver = object :BroadcastReceiver(),AppForeground.IForegroundEvent {
                    private var delayCheck:Disposable? = null
                    private var connecting = false

                    init {
                        AppForeground.listener.addListener(this)
                    }

                    override fun onForegroundChanged(isForeground: Boolean) {
                        if (isForeground) {
                            delayCheck = AmeDispatcher.mainThread.dispatch({
                                delayCheck = null
                                checkConnectedState()
                            },500)
                        }
                    }

                    override fun onReceive(context: Context?, intent: Intent?) {
                        val wifiState = intent?.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1)

                        ALog.i("WiFiUtil", "Wi-Fi state $wifiState")
                        if (wifiState == WifiManager.WIFI_STATE_ENABLING) {
                            ALog.i("WiFiUtil", "Wi-Fi turning on")
                            connecting = true
                            return
                        }

                        if (!connecting) {
                            return
                        }

                        checkConnectedState()
                    }


                    private fun checkConnectedState() {
                        delayCheck?.dispose()

                        AppContextHolder.APP_CONTEXT.unregisterReceiver(receiver?:return)
                        AppForeground.listener.removeListener(this)
                        result(isEnable(),cancel)
                    }
                }

                val intentFilter = IntentFilter()
                intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                AppContextHolder.APP_CONTEXT.registerReceiver(receiver, intentFilter)
                toSetting(activity)
            } else {
                result(false, cancel)
            }
        }
    }

    fun toSetting(activity: Activity) {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
        activity.startActivity(intent)
    }

    interface IWiFiStateNotify {
        fun onWiFiStateChanged()
    }
}