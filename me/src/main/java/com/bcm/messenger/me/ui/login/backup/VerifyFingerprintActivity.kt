package com.bcm.messenger.me.ui.login.backup

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.me.R
import com.bcm.messenger.me.ui.fragment.VerifyFingerprintFragment
import com.bcm.messenger.me.ui.fragment.VerifyPasswordFragment
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog

/**
 * Created by Kin on 2018/9/3
 */
class VerifyFingerprintActivity : SwipeBaseActivity() {
    private val TAG = "VerifyFingerprintActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        disableClipboardCheck()
        disableStatusBarLightMode()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_verify_fingerprint)

        setSwipeBackEnable(false)

        initView()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            handleCallback(resultCode == Activity.RESULT_OK)
        }
    }

    private fun initView() {
        switchToPasswordFragment(false)
    }

    fun switchToFingerprintFragment() {
        val f = VerifyFingerprintFragment()
                .setCallback(this::handleCallback)
        supportFragmentManager.beginTransaction()
                .replace(R.id.container, f)
                .commitAllowingStateLoss()
    }

    fun switchToPasswordFragment(hasFingerprint: Boolean, lockout: Boolean = false) {
        val f = VerifyPasswordFragment()
                .setHasFingerprint(hasFingerprint)
                .setHasLockout(lockout)
                .setCallback(this::handleCallback)
        f.arguments = Bundle().apply {
            putString(ARouterConstants.PARAM.PARAM_ACCOUNT_ID, AMESelfData.uid)
        }
        supportFragmentManager.beginTransaction()
                .replace(R.id.container, f)
                .commitAllowingStateLoss()
    }

    private fun handleCallback(success: Boolean) {
        if (success) {
            ALog.d(TAG, "Authenticate success.")
            setResult(Activity.RESULT_OK)
        } else {
            ALog.d(TAG, "Authenticate failed.")
            setResult(Activity.RESULT_CANCELED)
        }
        AmeDispatcher.mainThread.dispatch({
            finish()
        }, 500)
    }

    override fun onBackPressed() {
        finish()
    }
}