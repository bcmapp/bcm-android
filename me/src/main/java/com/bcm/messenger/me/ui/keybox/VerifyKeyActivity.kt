package com.bcm.messenger.me.ui.keybox

import android.os.Bundle
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.utils.hideKeyboard
import com.bcm.messenger.me.R
import com.bcm.messenger.me.ui.login.LoginVerifyPinFragment
import com.bcm.messenger.me.ui.login.RegistrationActivity
import com.bcm.route.annotation.Route

@Route(routePath = ARouterConstants.Activity.VERIFY_PASSWORD)
class VerifyKeyActivity : AccountSwipeBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        disableDefaultTransitionAnimation()
        overridePendingTransition(R.anim.common_popup_alpha_in, R.anim.common_popup_alpha_out)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_verify)
        initView()
    }

    fun initView() {
        val arg = intent.extras
        val f = LoginVerifyPinFragment()
        arg?.putString(RegistrationActivity.RE_LOGIN_ID, intent.getStringExtra(RegistrationActivity.RE_LOGIN_ID))
        initFragment(R.id.register_container, f, arg)

    }

    override fun finish() {
        super.finish()
        hideKeyboard()
        overridePendingTransition(0, R.anim.common_popup_alpha_out)
    }
}