package com.bcm.messenger.me.ui.pinlock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.common.utils.getNavigationBarHeight
import com.bcm.messenger.common.utils.getStatusBarHeight
import com.bcm.messenger.me.R
import com.bcm.messenger.me.fingerprint.BiometricVerifyUtil
import com.bcm.messenger.me.logic.AmePinLogic
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import kotlinx.android.synthetic.main.me_activity_pin_lock.*
import java.util.*
import kotlin.collections.ArrayList


/**
 * Created by zjl on 2018/10/9.
 */
@Route(routePath = ARouterConstants.Activity.PIN_INPUT)
class PinInputActivity : SwipeBaseActivity() {
    companion object {
        const val INPUT_STYPE = "input_style"
        const val INPUT_SIZE = "input_size"
        const val NEW_PIN_STYLE = "new_pin_style"
        const val CHECK_PIN_REQUEST_CODE = "check_pin_request"
        const val NEED_FINGERPRINT = "need_fingerprint"

        //pin size
        const val INPUT_SIZE_4 = 4
        const val INPUT_SIZE_6 = 6

        //view style
        const val INPUT_PIN = 1
        const val CONFIRM_NEW_PIN = 2
        const val CHANGE_PIN = 3
        const val NEW_PIN = 4
        const val VERIFY_UNLOCK = 5
        const val DISABLE_PIN = 6
        const val CHECK_PIN = 7

        fun router(activity: Activity, pinSize: Int, viewStyle: Int) {
            val intent = Intent(activity, PinInputActivity::class.java)
            intent.putExtra(INPUT_STYPE, viewStyle)
            intent.putExtra(INPUT_SIZE, pinSize)
            activity.startActivity(intent)
        }

        fun routerChangedPin(activity: Activity, newPinStyle: Int) {
            val intent = Intent(activity, PinInputActivity::class.java)
            intent.putExtra(INPUT_STYPE, CHANGE_PIN)
            intent.putExtra(INPUT_SIZE, AmePinLogic.lengthOfPin())
            intent.putExtra(NEW_PIN_STYLE, newPinStyle)
            activity.startActivity(intent)
        }

        fun routerCheckPin(activity: Activity, requestCode: Int) {
            val intent = Intent(activity, PinInputActivity::class.java)
            intent.putExtra(INPUT_STYPE, CHECK_PIN)
            intent.putExtra(INPUT_SIZE, AmePinLogic.lengthOfPin())
            intent.putExtra(CHECK_PIN_REQUEST_CODE, requestCode)
            activity.startActivityForResult(intent, requestCode)
        }

        fun routerVerifyUnlock(activity: Activity) {
            val intent = Intent(activity, PinInputActivity::class.java)
            intent.putExtra(INPUT_STYPE, VERIFY_UNLOCK)
            intent.putExtra(INPUT_SIZE, AmePinLogic.lengthOfPin())
            activity.startActivity(intent)
        }
    }

    private val TAG = "PinInputActivity"
    private var pin: StringBuilder = StringBuilder()
    private var inputSize = INPUT_SIZE_4
    private var inputStyle = INPUT_PIN
    private var pinList: MutableList<ImageView>? = null
    private var inputList: MutableList<TextView>? = null
    private var setPin: String? = null
    private var inputFragment = Stack<Int>()

    private var isFingerShow = false
    private var newPinSize = INPUT_SIZE_4
    private var checkPinRequestCode = 0

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        ALog.d(TAG, "onNewIntent")

        pin_lock_avatar.visibility = View.GONE
        pin_lock_nikename.visibility = View.GONE
        pin_input_title.visibility = View.VISIBLE
        input_back.visibility = View.VISIBLE
        input_verify.visibility = View.GONE

        if (intent != null) {
            pushFragment(inputStyle)
            initParams(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!AmePinLogic.hasPin() && inputStyle == VERIFY_UNLOCK) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        disableStatusBarLightMode()
        super.onCreate(savedInstanceState)
        setSwipeBackEnable(false)

        if (resources.displayMetrics.density >= 2f) {
            setContentView(R.layout.me_activity_pin_lock)
        } else {
            setContentView(R.layout.me_activity_pin_lock_small)
        }
        initParams(intent)

        pin_word_size.setOnClickListener {
            when {
                inputStyle == VERIFY_UNLOCK -> {
                }
                inputSize == INPUT_SIZE_4 -> setPinSize(INPUT_SIZE_6)
                inputSize == INPUT_SIZE_6 -> setPinSize(INPUT_SIZE_4)
            }
        }

        inputList = ArrayList(listOf(input_0, input_1, input_2, input_3, input_4, input_5, input_6, input_7, input_8, input_9))

        for (input in inputList ?: listOf<TextView>()) {
            input.setOnClickListener {
                if (!isFingerShow) {
                    addPin(input.text)
                }
            }
        }

        input_delete.setOnClickListener {
            deletePin()
        }

        input_back.setOnClickListener {
            checkBack()
        }
        if (pin_lock_avatar.visibility == View.VISIBLE && AppUtil.checkDeviceHasNavigationBar(this) && getNavigationBarHeight() > 0) {
            val lp = input_keyboard.layoutParams as ConstraintLayout.LayoutParams
            lp.topMargin = 60.dp2Px()
            input_keyboard.layoutParams = lp
        }
    }

    private fun initParams(intent: Intent) {
        inputSize = intent.getIntExtra(INPUT_SIZE, INPUT_SIZE_4)
        inputStyle = intent.getIntExtra(INPUT_STYPE, VERIFY_UNLOCK)
        newPinSize = intent.getIntExtra(NEW_PIN_STYLE, inputSize)
        checkPinRequestCode = intent.getIntExtra(CHECK_PIN_REQUEST_CODE, 0)
        setPinSize(inputSize)
        setTitleStyle()
    }

    private fun setPinList() {
        if (inputSize == INPUT_SIZE_4) {
            pinList = ArrayList(listOf(pin_2, pin_3, pin_4, pin_5))
        } else if (inputSize == INPUT_SIZE_6) {
            pinList = ArrayList(listOf(pin_1, pin_2, pin_3, pin_4, pin_5, pin_6))
        }
    }

    private fun setTitleStyle() {
        val titlelp = pin_input_title.layoutParams as ConstraintLayout.LayoutParams
        titlelp.setMargins(0, getStatusBarHeight() + 36.dp2Px(), 0, 0)

        when (inputStyle) {
            INPUT_PIN -> {
                pin_word_size.visibility = View.VISIBLE
                pin_input_title.text = getString(R.string.me_enter_pin)
            }
            CONFIRM_NEW_PIN -> {
                pin_word_size.visibility = View.GONE
                pin_input_title.text = getString(R.string.me_confirm_pin)
            }
            CHECK_PIN -> {
                pin_word_size.visibility = View.GONE
                pin_input_title.text = getString(R.string.me_enter_pin)
            }
            CHANGE_PIN -> {
                pin_word_size.visibility = View.GONE
                pin_input_title.text = getString(R.string.me_enter_current_pin)
            }
            NEW_PIN -> {
                pin_word_size.visibility = View.VISIBLE
                pin_input_title.text = getString(R.string.me_new_pin)
            }
            VERIFY_UNLOCK -> {
                pin_input_title.visibility = View.INVISIBLE
                input_back.visibility = View.GONE
                input_verify.visibility = View.VISIBLE
                pin_lock_avatar.visibility = View.VISIBLE
                pin_lock_nikename.visibility = View.VISIBLE
                pin_word_size.visibility = View.GONE
                pin_word_size.text = getString(R.string.me_forget_pin)
                val recipient = Recipient.fromSelf(this, true)
                pin_lock_avatar.setPhoto(recipient)
                pin_lock_nikename.text = recipient.name
                recipient.addListener {
                    pin_lock_avatar.setPhoto(recipient)
                    pin_lock_nikename.text = recipient.name
                }
                input_verify.setOnClickListener {
                    if (!isFingerShow) {
                        startAuthenticate()
                    }
                }
                val avaterlp = pin_lock_avatar.layoutParams as ConstraintLayout.LayoutParams
                avaterlp.setMargins(0, getStatusBarHeight() + 16.dp2Px(), 0, 0)
                pin_lock_avatar.layoutParams = avaterlp

                titlelp.setMargins(0, getStatusBarHeight() + 66.dp2Px(), 0, 0)
                startAuthenticate()
            }
            DISABLE_PIN -> {
                pin_input_title.text = resources.getString(R.string.me_enter_pin)
                pin_word_size.visibility = View.GONE
            }
        }

        pin_input_title.layoutParams = titlelp

        clearPin()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            checkBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun checkBack() {
        when (inputStyle) {
            INPUT_PIN -> {
                if (!inputFragment.empty()) {
                    inputStyle = inputFragment.pop()
                    setTitleStyle()
                } else {
                    finish()
                }
            }
            CONFIRM_NEW_PIN -> {
                if (!inputFragment.empty()) {
                    inputStyle = inputFragment.pop()
                    setTitleStyle()
                } else {
                    finish()
                }
            }
            CHANGE_PIN -> {
                finish()
            }
            NEW_PIN -> {
                if (!inputFragment.empty()) {
                    inputStyle = inputFragment.pop()
                    setTitleStyle()
                } else {
                    finish()
                }
            }
            CHECK_PIN -> {
                finish()
            }
            DISABLE_PIN -> {
                finish()
            }
            VERIFY_UNLOCK -> {

            }
            else -> {
                finish()
            }
        }
    }

    private fun setPinSize(size: Int) {
        inputSize = if (size == 0) {
            val length = AmePinLogic.lengthOfPin()
            if (length > 0) {
                length
            } else {
                INPUT_SIZE_4
            }
        } else {
            size
        }
        if (inputSize == INPUT_SIZE_4) {
            pin_1.visibility = View.GONE
            pin_6.visibility = View.GONE
            pin_word_size.text = resources.getString(R.string.me_pin_size_6)
        } else if (inputSize == INPUT_SIZE_6) {
            pin_1.visibility = View.VISIBLE
            pin_6.visibility = View.VISIBLE
            pin_word_size.text = resources.getString(R.string.me_pin_size_4)
        }
        clearPin()
        setPinList()
    }

    private fun addPin(num: CharSequence) {
        if (pin.length < inputSize) {
            pin.append(num)
            changePinShow()

            if (pin.length == inputSize) {
                AmeDispatcher.mainThread.dispatch({
                    checkPin(pin)
                }, 150)
            }
        }
    }

    private fun clearPin() {
        if (pin.isNotEmpty()) {
            pin.delete(0, pin.length)
            changePinShow()
        }
    }

    private fun deletePin() {
        if (pin.isNotEmpty()) {
            pin.deleteCharAt(pin.lastIndex)
            changePinShow()
        }
    }

    private fun checkPin(pin: StringBuilder) {
        when (inputStyle) {
            INPUT_PIN -> {
                inputStyle = CONFIRM_NEW_PIN
                setPin = pin.toString()
                setTitleStyle()
                pushFragment(INPUT_PIN)
            }
            CONFIRM_NEW_PIN -> {
                if (pin.toString() == setPin) {
                    AmePopup.result.succeed(this@PinInputActivity, resources.getString(R.string.me_set_pin_successfully))
                    AmePinLogic.setPin(pin.toString())
                    pin_input_title.postDelayed({
                        finish()
                    }, 1000)
                } else {
                    AmePopup.result.failure(this@PinInputActivity, resources.getString(R.string.me_pin_not_matched))
                    clearPin()
                }
            }
            CHECK_PIN -> {
                if (AmePinLogic.checkPin(pin.toString())) {
                    setResult(Activity.RESULT_OK)
                    finish()
                } else {
                    AmePopup.result.failure(this@PinInputActivity, resources.getString(R.string.me_pin_not_matched))
                    clearPin()
                }
            }
            CHANGE_PIN -> {
                if (AmePinLogic.checkPin(pin.toString())) {
                    inputStyle = NEW_PIN
                    setTitleStyle()
                    setPinSize(newPinSize)
                    pushFragment(CHANGE_PIN)
                } else {
                    AmePopup.result.failure(this@PinInputActivity, resources.getString(R.string.me_wrong_pin))
                    clearPin()
                }
            }
            NEW_PIN -> {
                inputStyle = CONFIRM_NEW_PIN
                setPin = pin.toString()
                setTitleStyle()
                pushFragment(NEW_PIN)
            }
            VERIFY_UNLOCK -> {
                if (AmePinLogic.checkPin(pin.toString())) {
                    AmePinLogic.unlock()
                    finish()
                } else {
                    AmePopup.result.failure(this@PinInputActivity, resources.getString(R.string.me_pin_not_matched))
                    clearPin()
                }
            }
            DISABLE_PIN -> {
                if (AmePinLogic.checkPin(pin.toString())) {
                    AmePinLogic.disablePin()
                    AmePopup.result.succeed(this@PinInputActivity, resources.getString(R.string.me_pin_disabled))
                    pin_input_title.postDelayed({
                        finish()
                    }, 1000)
                } else {
                    AmePopup.result.failure(this@PinInputActivity, resources.getString(R.string.me_pin_not_matched))
                    clearPin()
                }
            }
        }
    }

    private fun pushFragment(fragment: Int) {
        if ((inputFragment.size > 0 && inputFragment.peek() != inputStyle) || inputFragment.size == 0) {
            inputFragment.push(fragment)
        }
    }

    private fun changePinShow() {
        val length = pin.length
        for ((index, pin) in pinList?.withIndex() ?: listOf()) {
            pin.isEnabled = index >= length
        }
    }

    /**
     * fingerprint authenticate
     */
    private fun startAuthenticate() {
        if (!BiometricVerifyUtil.canUseBiometricFeature() || !AmePinLogic.isUnlockWithFingerprintEnable()) {
            pin_word_size.visibility = View.VISIBLE
            input_back.visibility = View.GONE
            input_verify.visibility = View.GONE
            return
        }

        isFingerShow = true
        ALog.d(TAG, "Start authenticate")

        BiometricVerifyUtil.BiometricBuilder(this)
                .setTitle(getString(R.string.me_unlock_app))
                .setDescription(getString(R.string.me_unlock_app_description))
                .setCancelTitle(getString(R.string.common_cancel))
                .setCallback { success, hasHw, isLocked ->
                    if (success) {
                        isFingerShow = false
                        AmeDispatcher.mainThread.dispatch({
                            AmePopup.result.succeed(this@PinInputActivity, resources.getString(R.string.me_fingerprint_unlock_successful)) {
                                AmePinLogic.unlock()
                                finish()
                            }
                        }, 300)
                    } else {
                        if (!hasHw || isLocked) {
                            input_verify.visibility = View.GONE
                        }
                        pin_word_size.visibility = View.VISIBLE
                        isFingerShow = false
                    }
                }
                .build()
    }
}