package com.bcm.messenger.me.ui.login

import android.os.Bundle
import android.text.TextUtils
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.R
import com.bcm.messenger.me.provider.UserModuleImp
import kotlinx.android.synthetic.main.me_activity_change_password.*
import java.util.regex.Pattern

class ChangePasswordActivity : SwipeBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_change_password)
        change_password_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                handleChangePasswordDoneClick()
            }
        })


    }


    //点击完成
    private fun handleChangePasswordDoneClick() {
        AmePopup.loading.show(this)
        if (checkInputLegitimate()) {
            handleChangePassword()
        } else {
            AmePopup.loading.dismiss()
        }
    }

    //检测输入合法性
    private fun checkInputLegitimate(): Boolean {
        if (TextUtils.isEmpty(origin_psw_edit.text)
                || TextUtils.isEmpty(new_psw_edit.text)
                || TextUtils.isEmpty(confirm_psw_edit.text)) {
            AmePopup.result.failure(this, getString(R.string.me_input_password_can_not_empty))
            return false
        } else if (new_psw_edit.text.length < ARouterConstants.PASSWORD_LEN_MIN ||
                confirm_psw_edit.text.length < ARouterConstants.PASSWORD_LEN_MIN ){
            AmePopup.result.failure(this, getString(R.string.common_new_password_too_short_warning))
            return false
        } else if (!checkLegitimateForNewPassword()) {
            AmePopup.result.failure(this, getString(R.string.me_psw_not_equals_confirm_psw))
            return false
        } else if (!Pattern.matches(ARouterConstants.PASSWORD_REGEX, new_psw_edit.text)) {
            AmePopup.result.failure(this, getString(R.string.common_password_format_error))
            return false
        }
        return true
    }

    //检测新密码和确认新密码是否一致
    private fun checkLegitimateForNewPassword(): Boolean {
        if (TextUtils.equals(new_psw_edit.text, confirm_psw_edit.text))
            return true
        return false
    }

    private fun handleChangePassword() {
        AmePopup.loading.show(this)
        val userProvider = UserModuleImp()
        userProvider.changePinPasswordAsync(this, origin_psw_edit.text.toString(), confirm_psw_edit.text.toString()
        ) { result, _ ->
            if (result) {
                AmePopup.loading.dismiss()
                AmeLoginLogic.accountHistory.resetBackupState(AMESelfData.uid)
                AmePopup.result.succeed(this, getString(R.string.me_change_password_success)) {
                    finish()
                }
            } else {
                AmePopup.loading.dismiss()
                AmePopup.result.failure(this, getString(R.string.me_input_origin_password_error))
            }
        }
    }
}