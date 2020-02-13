package com.bcm.messenger.adhoc.ui.setting

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.text.SpannableStringBuilder
import com.bcm.messenger.adhoc.R
import com.bcm.messenger.adhoc.logic.AdHocSetting
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.ble.BleUtil
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.gps.GPSUtil
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.wifi.WiFiUtil
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.adhoc_setting_activity.*

class AdHocSettingActivity : AccountSwipeBaseActivity() {
    private val TAG = "AdHocSettingActivity"
    private val REQUEST_ENABLE_BLE = 1000
    private var checking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.adhoc_setting_activity)

        adhoc_setting_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                super.onClickLeft()
                finish()
            }
        })

        val builder = SpannableStringBuilder()
        val drawable = getDrawable(R.drawable.adhoc_setting_warning)
        if (null != drawable) {
            drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
            builder.append(StringAppearanceUtil.addImage("  ", drawable, 0))
        }

        builder.append(getString(R.string.adhoc_the_bluetooth_and_wi_fi_connectivity_of_this_device))
        adhoc_setting_tip.text = builder

        adhoc_setting_mode_enable.setOnClickListener {
            switchAdHocMode()
        }

        updateState()
    }

    private fun updateState() {
        if (AdHocSetting.isEnable()) {
            AmeModuleCenter.login().setAdHocUid(accountContext.uid)
            adhoc_setting_mode_enable.setBackgroundResource(R.drawable.common_red_tint_bg)
            adhoc_setting_mode_enable.text = getString(R.string.adhoc_setting_disable_airchat)
            adhoc_setting_mode_enable.setTextColor(AppUtil.getColor(resources, R.color.common_color_ff3737))
        } else {
            AmeModuleCenter.login().setAdHocUid("")
            adhoc_setting_mode_enable.setBackgroundResource(R.drawable.common_rectangle_8_blue_bg)
            adhoc_setting_mode_enable.text = getString(R.string.adhoc_setting_enable_airchat)
            adhoc_setting_mode_enable.setTextColor(AppUtil.getColor(resources, R.color.common_color_white))
        }
    }

    private fun switchAdHocMode() {
        val toOpen = !AdHocSetting.isEnable()
        if (toOpen) {
            val bleAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bleAdapter == null || !BleUtil.isSupport()) {
                AmeAppLifecycle.failure(getString(R.string.adhoc_ble_not_support_error), true)
                return
            }

            if (checking) {
                ALog.i(TAG, "checkRequirement checking doing and return")
                return
            }

            ALog.i(TAG, "checkRequirement checking")
            checking = true
            checkWiFi()
        } else {
            AdHocSetting.setEnable(toOpen)
            updateState()
            toHome()
        }
    }

    private fun checkWiFi() {
        if (!WiFiUtil.isEnable()) {
            ALog.i(TAG, "checkRequirement checking WiFi")
            WiFiUtil.enableWiFi(this, AppUtil.getString(R.string.common_go_setting_wifi)) { it, cancel ->
                ALog.i(TAG, "checkRequirement WiFi enable $it")

                checking = false
                if (it) {
                    checkBLE()
                } else {
                    AmeAppLifecycle.failure(getString(R.string.adhoc_wifi_not_enable_error), true)
                }
            }
            return
        }
        checkBLE()
    }


    private fun checkBLE() {
        if (!BleUtil.isEnable()) {
            ALog.i(TAG, "checkRequirement checking BLE")
            BleUtil.enableBLE(this, AppUtil.getString(R.string.common_ble_go_setting)) { it, cancel ->
                ALog.i(TAG, "checkRequirement BLE enable $it")

                AmeDispatcher.mainThread.dispatch({
                    checking = false
                    if (BleUtil.isEnable()) {
                        checkGPS()
                    } else {
                        AmeAppLifecycle.failure(getString(R.string.adhoc_ble_not_enable_error), true)
                    }
                }, 500)
            }
            return
        }
        checkGPS()
    }

    private fun checkGPS() {
        if (!GPSUtil.isEnable()) {
            ALog.i(TAG, "checkRequirement checking GPS")
            GPSUtil.enableGPS(this, AppUtil.getString(R.string.common_gps_go_setting)) { it, cancel ->
                ALog.i(TAG, "checkRequirement GPS enable $it")
                AmeDispatcher.mainThread.dispatch({
                    checking = false
                    if (GPSUtil.isEnable()) {
                        enableAdhocMode()
                    } else {
                        AmeAppLifecycle.failure(getString(R.string.adhoc_gps_not_enable_error), true)
                    }
                }, 500)
            }
            return
        }

        checking = false
        ALog.i(TAG, "checkRequirement check finished")

        enableAdhocMode()
    }

    private fun enableAdhocMode() {
        AdHocSetting.setEnable(true)
        updateState()
        toHome()
    }


    private fun toHome() {
        BcmRouter.getInstance().get(ARouterConstants.Activity.APP_HOME_PATH)
                .putBoolean(ARouterConstants.PARAM.PARAM_LOGIN_FROM_REGISTER, false)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .startBcmActivity(AMELogin.majorContext, this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BLE) {
            if (BluetoothAdapter.getDefaultAdapter().isEnabled) {
                switchAdHocMode()
            }
        }
    }
}