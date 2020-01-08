package com.bcm.messenger.common

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.utility.logger.ALog

/**
 * bcm.social.01 2019/3/7.
 */
open class BaseFragment : Fragment() {

    companion object {
        private const val TAG = "BaseFragment"
    }

    private lateinit var accountContextObj: AccountContext
    val accountContext get() = accountContextObj
    private lateinit var mAccountRecipient: Recipient
    private var mModifiedListener = RecipientModifiedListener { recipient ->
        if (mAccountRecipient == recipient) {
            mAccountRecipient = recipient
            if (isAdded) {
                onLoginRecipientRefresh()
            }
        }
    }
    private var isActive: Boolean = false

    fun getAccountRecipient() = mAccountRecipient

    fun getMasterSecret(): MasterSecret = BCMEncryptUtils.getMasterSecret(accountContextObj) ?: throw Exception("getMasterSecret is null")

    protected fun setAccountContext(context: AccountContext) {
        accountContextObj = context
        setAccountRecipient(Recipient.login(context))
    }

    private fun setAccountRecipient(recipient: Recipient) {
        if (::mAccountRecipient.isInitialized) {
            mAccountRecipient.removeListener(mModifiedListener)
        }
        mAccountRecipient = recipient
        mAccountRecipient.addListener(mModifiedListener)
    }

    open fun setActive(isActive: Boolean) {
        this.isActive = isActive
    }

    open fun isActive(): Boolean {
        return isActive
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::mAccountRecipient.isInitialized) {
            mAccountRecipient.removeListener(mModifiedListener)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accountContextObj: AccountContext? = arguments?.getParcelable(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT)
        if (accountContextObj == null) {
            ALog.w(TAG, "accountContextObj is null, finish")
            activity?.finish()
            return
        }
        setAccountContext(accountContextObj)
    }

    protected open fun onLoginRecipientRefresh() {

    }

    open fun onNewIntent() {
        val accountContextObj: AccountContext? = arguments?.getParcelable(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT)
        if (accountContextObj != null) {
            ALog.w(TAG, "onNewIntent, new accountContextObj: ${accountContextObj.uid}")
            setAccountContext(accountContextObj)
        }
    }

    fun <T : Fragment> initFragment(@IdRes target: Int,
                                    fragment: T,
                                    extras: Bundle?): T {
        val newArg = Bundle().apply {
            putParcelable(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContextObj)
        }
        if (extras != null) {
            newArg.putAll(extras)
        }
        fragment.arguments = newArg
        fragmentManager?.beginTransaction()
                ?.replace(target, fragment)
                ?.commitAllowingStateLoss()
        return fragment
    }
}