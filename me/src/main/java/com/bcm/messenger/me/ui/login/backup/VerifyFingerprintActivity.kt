package com.bcm.messenger.me.ui.login.backup

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.me.R
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
        val accountContext = intent.getParcelableExtra<AccountContext>(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT)
        if (accountContext == null) {
            ALog.e(TAG, "Account context has not been passed from previous activity!")
            finish()
            return
        }

        switchToPasswordFragment(accountContext)
    }

    private fun switchToPasswordFragment(accountContext: AccountContext) {
        val f = VerifyPasswordFragment()
                .setHasFingerprint(false)
                .setHasLockout(false)
                .setCallback(this::handleCallback)
        f.arguments = Bundle().apply {
            putParcelable(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContext)
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