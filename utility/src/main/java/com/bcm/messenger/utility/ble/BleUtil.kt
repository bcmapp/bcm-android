package com.bcm.messenger.utility.ble

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.listener.SafeWeakListeners
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.permission.PermissionUtil
import io.reactivex.disposables.Disposable


object BleUtil {
    private var receiver:BroadcastReceiver?= null
    private var enabled:Boolean = false
    private var restarting:Boolean = false

    val stateNotify = SafeWeakListeners<IBleStateNotify>()

    fun init(context: Context) {
        enabled = isEnable()

        var receiver:BroadcastReceiver? = null
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val b = isEnable()

                if (enabled != b) {
                    enabled = b
                    if (!restarting) {
                        stateNotify.forEach { it.onBLEStateChanged() }
                    }
                }
            }
        }


        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(receiver, filter)
    }

    fun unInit(context: Context) {
        context.unregisterReceiver(receiver?:return)
    }

    fun isEnable(): Boolean {
        if(!AppContextHolder.APP_CONTEXT.packageManager
            .hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return false
        }

        val bleAdapter = BluetoothAdapter.getDefaultAdapter()
        return bleAdapter?.isEnabled == true
    }

    fun isSupport(): Boolean {
        if(!AppContextHolder.APP_CONTEXT.packageManager
                        .hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return false
        }

        return true
    }

    fun enableBLE(activity: Activity, error:String, result:(succeed:Boolean, canceled:Boolean)->Unit) {
        if(!AppContextHolder.APP_CONTEXT.packageManager
                .hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            ALog.e("BleUtil", "手机不支持FEATURE_BLUETOOTH_LE")
            result(false, true)
            return
        }

        val bleAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bleAdapter == null || isEnable()) {
            result(isEnable(), false)
            return
        }

        val language = PermissionUtil.languageSetting
        if (null == language) {
            result(isEnable(), false)
            return
        }

        TipShowUtil.show(activity,language.permissionTitle(), error, language.permissionGoSetting(), language.permissionCancel()) {
            goSetting, canceled ->
            if (goSetting) {
                var receiver:BroadcastReceiver? = null
                receiver = object : BroadcastReceiver() {
                    private var dispose:Disposable? = null
                    private var enabling = false
                    init {
                        dispose = AmeDispatcher.mainThread.dispatch({
                            dispose = null
                            checkEnable()
                        },5000)
                    }

                    override fun onReceive(context: Context, intent: Intent) {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                                BluetoothAdapter.ERROR)
                        ALog.i("BLEUtil", "Ble status $state")
                        if (state == BluetoothAdapter.STATE_TURNING_ON) {
                            ALog.i("BLEUtil", "Ble turning on")
                            enabling = true
                            return
                        }

                        if (!enabling) {
                            return
                        }

                        checkEnable()
                    }

                    private fun checkEnable() {
                        dispose?.dispose()

                        try {
                            if(isEnable()) {
                                result(true, canceled)
                            } else {
                                result(false, canceled)
                            }
                            AppContextHolder.APP_CONTEXT.unregisterReceiver(receiver?:return)
                        } catch (e:Throwable) { }
                    }
                }

                val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
                AppContextHolder.APP_CONTEXT.registerReceiver(receiver, filter)
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                activity.startActivityForResult(intent, 1000)
            } else {
                result(false, canceled)
            }
        }
    }

    fun restartBLE(result:(succeed:Boolean)->Unit) {
        if (!AppContextHolder.APP_CONTEXT.packageManager
                        .hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            ALog.e("BleUtil", "手机不支持FEATURE_BLUETOOTH_LE")
            result(true)
            return
        }

        val bleAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bleAdapter == null) {
            result(true)
            return
        }

        if (restarting) {
            result(false)
            return
        }

        restarting = true
        var receiver: BroadcastReceiver? = null
        receiver = object : BroadcastReceiver() {
            private var dispose: Disposable? = null
            private var enabling = false
            private var disableing = false

            init {
                dispose = AmeDispatcher.mainThread.dispatch({
                    dispose = null
                    checkEnable()
                }, 20000)
            }

            override fun onReceive(context: Context, intent: Intent) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR)
                ALog.i("BLEUtil", "restartBLE Ble status $state")
                if (state == BluetoothAdapter.STATE_TURNING_ON) {
                    ALog.i("BLEUtil", " restartBLEBle turning on")
                    enabling = true
                    return
                }

                if(state == BluetoothAdapter.STATE_TURNING_OFF) {
                    ALog.i("BLEUtil", " restartBLEBle turning off")
                    disableing = true
                    return
                }

                if (!enabling && !disableing) {
                    return
                }

                if (enabling) {
                    if (state == BluetoothAdapter.STATE_ON) {
                        ALog.i("BLEUtil", " restartBLEBle turn on")
                        checkEnable()
                    }
                }

                if (disableing) {
                    if (state == BluetoothAdapter.STATE_OFF) {
                        ALog.i("BLEUtil", " restartBLEBle turn off")
                        bleAdapter.enable()
                    }
                }
            }

            private fun checkEnable() {
                dispose?.dispose()

                restarting = false
                if (isEnable()) {
                    result(true)
                } else {
                    result(false)
                }
                AppContextHolder.APP_CONTEXT.unregisterReceiver(receiver ?: return)
            }
        }

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        AppContextHolder.APP_CONTEXT.registerReceiver(receiver, filter)
        if (isEnable()) {
            bleAdapter.disable()
        } else {
            bleAdapter.enable()
        }

    }


    interface IBleStateNotify {
        fun onBLEStateChanged()
    }
}