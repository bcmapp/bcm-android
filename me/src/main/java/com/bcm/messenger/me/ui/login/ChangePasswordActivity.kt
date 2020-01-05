package com.bcm.messenger.me.ui.login

import android.os.Bundle
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.me.R

class ChangePasswordActivity : SwipeBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_change_password)

        val accountContext = intent.getParcelableExtra<AccountContext>(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT)
        if (accountContext == null) {
            finish()
            return
        }

        val fragment = ChangePasswordFragment()
        fragment.arguments = Bundle().apply {
            putParcelable(ARouterConstants.PARAM.PARAM_ACCOUNT_CONTEXT, accountContext)
        }
        initFragment(R.id.change_password_container, fragment, null)
    }

    fun gotoNote() {
        val f = BanResetPasswordFragment()
        supportFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.common_slide_from_right, R.anim.common_popup_alpha_out, R.anim.common_popup_alpha_in, R.anim.common_slide_to_right)
                .replace(R.id.change_password_container, f)
                .addToBackStack("change_pwd_note")
                .commit()
    }
}