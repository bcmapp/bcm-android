package com.bcm.messenger.me.ui.pinlock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.messenger.login.bean.AmeAccountData
import com.bcm.messenger.me.R
import com.bcm.messenger.me.fingerprint.BiometricVerifyUtil
import com.bcm.messenger.me.logic.AmePinLogic
import kotlinx.android.synthetic.main.me_activity_pin_lock_setting.*

/**
 * bcm.social.01 2018/10/10.
 */
class PinLockSettingActivity : SwipeBaseActivity() {
    companion object {
        const val CHECK_PIN_FOR_SWITCH_FINGER_PRINT = 10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_pin_lock_setting)
        title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }
        })

        me_disable_pin.setOnClickListener {
            PinInputActivity.router(this, AmePinLogic.lengthOfPin(), PinInputActivity.DISABLE_PIN)
        }

        me_change_pin.setOnClickListener {
            PinInputActivity.router(this, AmePinLogic.lengthOfPin(), PinInputActivity.CHANGE_PIN)
        }

        me_switch_6_digit_pin.setSwitchEnable(false)
        me_switch_6_digit_pin.setOnClickListener {
            val pinSize = AmePinLogic.lengthOfPin()
            if (pinSize == PinInputActivity.INPUT_SIZE_6) {
                PinInputActivity.routerChangedPin(this, PinInputActivity.INPUT_SIZE_4)
            } else {
                PinInputActivity.routerChangedPin(this, PinInputActivity.INPUT_SIZE_6)
            }
        }

        me_pin_unlock_with_fingerprint.setSwitchEnable(false)
        if (BiometricVerifyUtil.canUseBiometricFeature()) {
            me_pin_unlock_with_fingerprint.setOnClickListener {
                PinInputActivity.routerCheckPin(this, CHECK_PIN_FOR_SWITCH_FINGER_PRINT)
            }
        } else {
            me_pin_unlock_with_fingerprint.setOnClickListener(null)
            me_pin_unlock_with_fingerprint.isClickable = false
            me_pin_unlock_with_fingerprint.setName(getString(R.string.me_unlock_with_fingerprint), getColorCompat(R.color.common_content_second_color))
            AmePinLogic.enableUnlockWithFingerprint(false)
        }

        me_pin_lock_auto.setOnClickListener {
            AmePopup.bottom.newBuilder()
                    .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.me_pin_lock_instantly)) {
                        AmePinLogic.setAppLockTime(AmeAccountData.APP_LOCK_INSTANTLY)
                        updateSetting()
                    })
                    .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.me_pin_lock_5_minutes)) {
                        AmePinLogic.setAppLockTime(AmeAccountData.APP_LOCK_5_MIN)
                        updateSetting()
                    })
                    .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.me_pin_lock_one_hour)) {
                        AmePinLogic.setAppLockTime(AmeAccountData.APP_LOCK_ONE_HOUR)
                        updateSetting()
                    })
                    .withDoneTitle(getString(R.string.common_cancel))
                    .show(this)
        }

    }

    private fun updateSetting() {
        me_switch_6_digit_pin.setSwitchStatus(AmePinLogic.lengthOfPin() == PinInputActivity.INPUT_SIZE_6)
        if (BiometricVerifyUtil.canUseBiometricFeature()) {
            me_pin_unlock_with_fingerprint.setSwitchStatus(AmePinLogic.isUnlockWithFingerprintEnable())
        }

        val text = when (AmePinLogic.appLockTime()) {
            AmeAccountData.APP_LOCK_5_MIN -> getString(R.string.me_pin_lock_5_minutes)
            AmeAccountData.APP_LOCK_INSTANTLY -> getString(R.string.me_pin_lock_instantly)
            else -> getString(R.string.me_pin_lock_one_hour)
        }

        me_pin_lock_auto.setTip(content = text)
    }

    override fun onResume() {
        super.onResume()
        if (!AmePinLogic.hasPin()) {
            val intent = Intent(this, PinLockInitActivity::class.java)
            startBcmActivity(getAccountContext(), intent)
            me_disable_pin.postDelayed({ finish() }, 50)
        } else {
            updateSetting()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CHECK_PIN_FOR_SWITCH_FINGER_PRINT && resultCode == Activity.RESULT_OK) {
            AmePinLogic.enableUnlockWithFingerprint(!AmePinLogic.isUnlockWithFingerprintEnable())
            me_pin_unlock_with_fingerprint.setSwitchStatus(AmePinLogic.isUnlockWithFingerprintEnable())
        }
    }
}