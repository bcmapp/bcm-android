package com.bcm.messenger.utility.gps

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.ble.TipShowUtil
import com.bcm.messenger.utility.listener.SafeWeakListeners
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.permission.PermissionUtil


object GPSUtil {
    private var receiver:BroadcastReceiver?= null
    private var enable:Boolean = false

    val stateNotify = SafeWeakListeners<IGPSStateNotify>()

    fun init(context: Context) {
        enable = isEnable()
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                    val b = isEnable()
                    if (b != enable) {
                        enable = b
                        stateNotify.forEach {it.onGPSStateChanged()}
                    }
                }
            }
        }

        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        context.registerReceiver(receiver, filter)
    }

    fun unInit(context: Context) {
        context.unregisterReceiver(receiver?:return)
    }

    fun isEnable(): Boolean {
        return try {
            val pm = AppContextHolder.APP_CONTEXT.packageManager;
            if(PackageManager.PERMISSION_GRANTED != pm.checkPermission("android.permission.ACCESS_COARSE_LOCATION", AppContextHolder.APP_CONTEXT.applicationContext.packageName)){
                return false
            }

            val lm= AppContextHolder.APP_CONTEXT.getSystemService(Activity.LOCATION_SERVICE) as? LocationManager
            lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        } catch (e:SecurityException) {
            ALog.e("GPSUtil", e)
            false
        }
    }

    fun enableGPS(context: Activity, tip:String, result:(succeed:Boolean, canceled:Boolean)->Unit) {
        ALog.i("GPSUtil", "call enable GPS")

        PermissionUtil.checkLocationPermission(context) { succeed ->
            if (succeed) {
                ALog.i("GPSUtil", "call enable GPS")
                val lm= AppContextHolder.APP_CONTEXT.getSystemService(Activity.LOCATION_SERVICE) as? LocationManager

                val language = PermissionUtil.languageSetting
                if(lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) != true && language != null) {
                    TipShowUtil.show(context, language.permissionTitle(), tip,language.permissionGoSetting(), language.permissionCancel() ) {
                        go,canceled->
                        if (go) {
                            var receiver:BroadcastReceiver?= null
                            receiver = object : BroadcastReceiver() {
                                override fun onReceive(context: Context, intent: Intent) {
                                    if (intent.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                                        result(isEnable(), canceled)
                                        AppContextHolder.APP_CONTEXT.unregisterReceiver(receiver?:return)
                                    }
                                }
                            }

                            val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
                            AppContextHolder.APP_CONTEXT.registerReceiver(receiver, filter)

                            val intent= Intent()
                            intent.action = android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS
                            context.startActivity(intent)
                        } else {
                            result(false, canceled)
                        }
                    }
                } else {
                    result(true, false)
                }
            } else {
                result(false, false)
            }
        }
    }

    interface IGPSStateNotify {
        fun onGPSStateChanged()
    }
}