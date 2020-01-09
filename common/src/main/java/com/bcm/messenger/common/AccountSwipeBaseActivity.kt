package com.bcm.messenger.common

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.utility.logger.ALog

open class AccountSwipeBaseActivity : SwipeBaseActivity() {

    private val TAG = "AccountSwipeBaseActivity"

    private lateinit var accountContextObj: AccountContext
    private lateinit var accountRecipientObj: Recipient

    val accountContext get() = accountContextObj
    val accountRecipient get() = accountRecipientObj

    private var mModifiedListener = RecipientModifiedListener { recipient ->
        if (accountRecipientObj == recipient) {
            accountRecipientObj = recipient
            onLoginRecipientRefresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accountContextObj: AccountContext? = intent.getSerializableExtra(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT) as? AccountContext
        if (accountContextObj != null) {
            setAccountContext(accountContextObj)
        }
        if (!checkHasAccountContext()) {
            ALog.w(TAG, "accountContextObj is null, finish")
            finish()
            return
        }
    }

    override fun onResume() {
        super.onResume()
        updateScreenshotSecurity()
    }

    fun getMasterSecret(): MasterSecret = BCMEncryptUtils.getMasterSecret(accountContextObj) ?: throw Exception("getMasterSecret is null")

    fun setAccountContext(context: AccountContext) {
        if (!::accountContextObj.isInitialized || accountContextObj != context) {
            accountContextObj = context
            setAccountRecipient(Recipient.login(context))
        }
    }

    private fun checkHasAccountContext(): Boolean {
        return ::accountContextObj.isInitialized
    }

    private fun setAccountRecipient(recipient: Recipient) {
        if (::accountRecipientObj.isInitialized) {
            accountRecipientObj.removeListener(mModifiedListener)
        }
        accountRecipientObj = recipient
        accountRecipientObj.addListener(mModifiedListener)
    }

    override fun <T : Fragment> initFragment(@IdRes target: Int,
                                            fragment: T,
                                            extras: Bundle?): T {
        val newArg = Bundle()
        if (extras != null) {
            newArg.putAll(extras)
        }
        newArg.putSerializable(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContextObj)
        return super.initFragment(target, fragment, newArg)

    }

    private fun isScreenSecurityEnabled(context: Context): Boolean {
        return try {
            if (AppUtil.isMainProcess()) {
                TextSecurePreferences.isScreenSecurityEnabled(accountContext)
            } else {
                ApplicationService.impl?.isScreenSecurityEnabled == true
            }
        } catch (e: Exception) {
            true
        }
    }

    fun updateScreenshotSecurity() {
        if (isScreenSecurityEnabled(this)) {
            ALog.i(TAG, "updateScreenshotSecurity setWindowSecure")
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            ALog.i(TAG, "updateScreenshotSecurity clearWindowSecure")
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    protected open fun onLoginRecipientRefresh() {
    }
}