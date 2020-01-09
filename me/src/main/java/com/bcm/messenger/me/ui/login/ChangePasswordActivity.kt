package com.bcm.messenger.me.ui.login

import android.os.Bundle
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.me.R

class ChangePasswordActivity : AccountSwipeBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_change_password)

        val fragment = ChangePasswordFragment()
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