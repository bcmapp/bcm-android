package com.bcm.messenger.me.logic

import com.bcm.messenger.common.deprecated.DatabaseFactory
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.login.bean.AmeAccountData
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.ui.keybox.VerifyKeyActivity
import com.bcm.messenger.me.ui.login.RegistrationActivity
import com.bcm.messenger.me.ui.pinlock.PinInputActivity
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.Base64
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.foreground.AppForeground
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.storage.SPEditor

/**
 * Created by bcm.social.01 on 2018/10/11.
 */
object AmePinLogic : AppForeground.IForegroundEvent {
    private const val TAG = "AmePinLogic"
    private const val PIN_STORAGE = "pin_storage"

    private var isLocked = false
    private val storage = SPEditor("pin_storage")

    private var pinData = PinData()
    private var oldPin: PinData? = null

    init {
        AppForeground.listener.addListener(this)
        pinData = PinData.fromString(storage.get(PIN_STORAGE, ""))

        AmeLoginLogic.getMajorAccount()?.apply {
            if (pin.isNotEmpty()) {
                val oldPinData = PinData()
                oldPinData.pin = pin
                oldPinData.lengthOfPin = lengthOfPin
                oldPinData.enableFingerprint = enableFingerprint
                oldPinData.pinLockTime = pinLockTime
                oldPin = oldPinData

                pinData.lengthOfPin = oldPinData.lengthOfPin
                pinData.enableFingerprint = oldPinData.enableFingerprint
                pinData.pinLockTime = oldPinData.pinLockTime
            }
        }
        onForegroundChanged(AppForeground.foreground())
    }

    fun isLocked(): Boolean {
        return isLocked
    }

    fun unlock() {
        isLocked = false
    }

    fun setPin(pin: String): Boolean {
        if (pin.isNotEmpty()) {
            val proPin = getProPin(pin)
            if (!proPin.isNullOrEmpty()) {
                pinData.pin = proPin
                pinData.lengthOfPin = pin.length
                storage.set(PIN_STORAGE, pinData.toString())
                return true
            }
        }
        return false
    }

    fun clearAccountPin() {
        if (pinData.pin.isNotEmpty() || oldPin?.pin?.isNotEmpty() != true ) {
            AmeLoginLogic.accountHistory.clearPin()
        }
    }

    fun majorHasPin(): Boolean {
        return AmeLoginLogic.accountHistory.majorHasPin()
    }

    fun anyAccountHasPin(): Boolean {
        return AmeLoginLogic.accountHistory.anyAccountHasPin()
    }

    fun disablePin() {
        pinData.pin = ""
        pinData.lengthOfPin = 0
        storage.remove(PIN_STORAGE)
    }

    fun lengthOfPin(): Int {
        val oldPinData = oldPin
        if (oldPinData != null) {
            return oldPinData.lengthOfPin
        }
        return pinData.lengthOfPin
    }

    /**
     * true pin set, false pin not set
     */
    fun hasPin(): Boolean {
        if (!AMELogin.isLogin) {
            return false
        }
        return pinData.pin.isNotEmpty() || oldPin?.pin?.isNotEmpty() == true
    }

    /**
     * true pin verify succeed, false pin verify failed
     */
    fun checkPin(pin: String): Boolean {
        if (pinData.pin.isNotEmpty() && pin.isNotEmpty()) {
            return pinData.pin == getProPin(pin)
        }

        val oldPinData = oldPin ?: return false
        if (oldPinData.pin.isNotEmpty() && pin.isNotEmpty()) {
            val check = oldPinData.pin == getOldProPin(AmeLoginLogic.accountHistory.majorAccountUid(), pin)
            if (check) {
                setPin(pin)
                oldPin = null
                return true
            }
        }
        return false
    }

    fun enableUnlockWithFingerprint(enable: Boolean) {
        pinData.enableFingerprint = enable
        storage.set(PIN_STORAGE, pinData.toString())
    }

    fun isUnlockWithFingerprintEnable(): Boolean {
        return pinData.enableFingerprint
    }

    fun setAppLockTime(time: Int) {
        pinData.pinLockTime = time
        storage.set(PIN_STORAGE, pinData.toString())
    }

    fun appLockTime(): Int {
        val oldPinData = oldPin
        if (oldPinData != null && oldPinData.pin.isNotEmpty()) {
            return oldPinData.pinLockTime
        }
        return pinData.pinLockTime
    }

    private fun getProPin(pin: String): String? {
        val temp = BCMPrivateKeyUtils.getHkdfInstance().deriveSecrets(pin.toByteArray(), pin.toByteArray(), BCMPrivateKeyUtils.KDF_INFO, 32)
        try {
            return temp.base64Encode().format()
        } catch (e: Exception) {
            ALog.e(TAG, e)
        }
        return null
    }

    private fun getOldProPin(uid: String, pin: String): String? {
        val temp = BCMPrivateKeyUtils.getHkdfInstance().deriveSecrets(uid.toByteArray(), pin.toByteArray(), BCMPrivateKeyUtils.KDF_INFO, 32)
        try {
            //please do not changed this code
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
            if (lockTime == PinData.APP_LOCK_INSTANTLY
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
            if (!DatabaseFactory.isDatabaseExist(AMELogin.majorContext, context) || TextSecurePreferences.isDatabaseMigrated(AMELogin.majorContext)) {
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