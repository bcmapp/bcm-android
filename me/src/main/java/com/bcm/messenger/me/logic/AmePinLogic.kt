package com.bcm.messenger.me.logic

import com.bcm.messenger.common.database.DatabaseFactory
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.common.utils.BCMPrivateKeyUtils
import com.bcm.messenger.login.bean.AmeAccountData
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.ui.keybox.VerifyKeyActivity
import com.bcm.messenger.me.ui.login.RegistrationActivity
import com.bcm.messenger.me.ui.pinlock.PinInputActivity
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.Base64
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.foreground.AppForeground
import com.bcm.messenger.utility.logger.ALog

/**
 * Created by bcm.social.01 on 2018/10/11.
 */
object AmePinLogic : AppForeground.IForegroundEvent {
    private const val TAG = "AmePinLogic"
    private var isLocked = false

    init {
        AppForeground.listener.addListener(this)
    }

    fun initLogic() {
        ALog.i(TAG, "AmePinLogic")
    }

    fun isLocked(): Boolean {
        return isLocked
    }

    fun unlock() {
        isLocked = false
    }

    fun setPin(pin: String): Boolean {
        val accountData = AmeLoginLogic.getMajorAccount()
        if (null != accountData && pin.isNotEmpty()) {
            val proPin = getProPin(accountData, pin)
            if (!proPin.isNullOrEmpty()) {
                accountData.pin = proPin
                accountData.lengthOfPin = pin.length
                AmeLoginLogic.saveAccount(accountData)
                return true
            }
        }
        return false
    }

    fun disablePin() {
        val accountData = AmeLoginLogic.getMajorAccount()
        if (null != accountData) {
            accountData.pin = ""
            accountData.lengthOfPin = -1
            AmeLoginLogic.saveAccount(accountData)
        }
    }

    fun lengthOfPin(): Int {
        val accountData = AmeLoginLogic.getMajorAccount()
        return accountData?.lengthOfPin ?: 0
    }

    /**
     * true pin set, false pin not set
     */
    fun hasPin(): Boolean {
        if (!AMELogin.isLogin) {
            return false
        }
        val accountData = AmeLoginLogic.getMajorAccount()
        return !accountData?.pin.isNullOrEmpty()
    }

    /**
     * true pin verify succeed, false pin verify failed
     */
    fun checkPin(pin: String): Boolean {
        val accountData = AmeLoginLogic.getMajorAccount()
        if (null != accountData && pin.isNotEmpty()) {
            return accountData.pin == getProPin(accountData, pin)
        }
        return false
    }

    fun enableUnlockWithFingerprint(enable: Boolean) {
        val accountData = AmeLoginLogic.getMajorAccount()
        if (null != accountData) {
            accountData.enableFingerprint = enable
            AmeLoginLogic.saveAccount(accountData)
        }
    }

    fun isUnlockWithFingerprintEnable(): Boolean {
        return AmeLoginLogic.getMajorAccount()?.enableFingerprint == true
    }

    fun setAppLockTime(time: Int) {
        val accountData = AmeLoginLogic.getMajorAccount()
        if (null != accountData) {
            accountData.pinLockTime = time
            AmeLoginLogic.saveAccount(accountData)
        }
    }

    fun appLockTime(): Int {
        val accountData = AmeLoginLogic.getMajorAccount()
        if (null != accountData) {
            return accountData.pinLockTime
        }
        return AmeAccountData.APP_LOCK_5_MIN
    }

    private fun getProPin(accountData: AmeAccountData, pin: String): String? {
        val temp = BCMPrivateKeyUtils.getHkdfInstance().deriveSecrets(accountData.uid.toByteArray(), pin.toByteArray(), BCMPrivateKeyUtils.KDF_INFO, 32)
        try {
            return Base64.encodeBytes(temp)
        } catch (e: Exception) {
            ALog.e(TAG, e)
        }

        return null
    }

    override fun onForegroundChanged(isForeground: Boolean) {
        ALog.i(TAG, "pin lock: background $isForeground")
        if (isForeground) {
            val lockTime = appLockTime()
            val leaveTime = (AppForeground.timeOfForeground() - AppForeground.timeOfBackground()) / 1000 / 60
            if (lockTime == AmeAccountData.APP_LOCK_INSTANTLY
                    || leaveTime >= lockTime) {
                ALog.i(TAG, "pin lock: leave $leaveTime minutes, lock set: $lockTime minutes")

                showPinLockView()
            }
        }
    }

    fun showPinLock() {
        if (hasPin() && isLocked) {
            showPinLockView()
        }
    }

    private fun showPinLockView() {
        if (hasPin()) {
            isLocked = true
            ALog.i(TAG, "setPinLock")
            val topActivity = AmeAppLifecycle.current()
            if (topActivity is PinInputActivity) {
                ALog.i(TAG, "showPinLock visible")
                return
            }
            ALog.i(TAG, "show pin lock activity: ${topActivity != null}")
            val context = AppContextHolder.APP_CONTEXT
            if(!DatabaseFactory.isDatabaseExist(context) || TextSecurePreferences.isDatabaseMigrated(context)) {
                if (null != topActivity && topActivity !is RegistrationActivity && topActivity !is VerifyKeyActivity) {
                    PinInputActivity.routerVerifyUnlock(topActivity)
                } else {
                    AmeDispatcher.mainThread.dispatch({
                        showPinLockView()
                    }, 250)
                }
            }
        }
    }

}