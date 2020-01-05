package com.bcm.messenger.common

import android.os.Bundle
import androidx.fragment.app.Fragment
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
    private lateinit var mAccountContext: AccountContext
    private lateinit var mAccountRecipient: Recipient
    private var mModifiedListener = object : RecipientModifiedListener {
        override fun onModified(recipient: Recipient) {
            if (mAccountRecipient == recipient) {
                mAccountRecipient = recipient
                onLoginRecipientRefresh()
            }
        }
    }
    private var isActive: Boolean = false

    fun getAccountContext() = mAccountContext

    fun getAccountRecipient() = mAccountRecipient

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
        val accountContext: AccountContext? = arguments?.getParcelable(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT)
        if (accountContext == null) {
            ALog.w(TAG, "accountContext is null, finish")
            activity?.finish()
            return
        }
        mAccountContext = accountContext
        mAccountRecipient = Recipient.login(accountContext)
        mAccountRecipient.addListener(mModifiedListener)
    }

    protected open fun onLoginRecipientRefresh() {

    }

    protected open fun onNewIntent() {
        val accountContext: AccountContext? = arguments?.getParcelable(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT)
        if (accountContext != null) {
            ALog.w(TAG, "onNewIntent, new accountContext: ${accountContext.uid}")
            mAccountContext = accountContext
            mAccountRecipient.removeListener(mModifiedListener)
            mAccountRecipient = Recipient.login(accountContext)
            mAccountRecipient.addListener(mModifiedListener)
        }
    }
}