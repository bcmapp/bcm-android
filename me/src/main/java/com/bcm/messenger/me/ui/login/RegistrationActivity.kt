package com.bcm.messenger.me.ui.login

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.R
import com.bcm.route.annotation.Route

/**
 * Created by ling
 */
@Route(routePath = ARouterConstants.Activity.USER_REGISTER_PATH)
class RegistrationActivity : SwipeBaseActivity() {

    companion object {
        private const val TAG = "RegistrationActivity"
        const val RE_LOGIN_ID = "RE_LOGIN_ID"
        const val REQUEST_CODE_SCAN_QR_LOGIN = 10013
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SCAN_QR_LOGIN && resultCode == Activity.RESULT_OK) {
            val scanResult = data?.getStringExtra(ARouterConstants.PARAM.SCAN.SCAN_RESULT) ?: return
            AmeLoginLogic.saveBackupFromExportModelWithWarning(scanResult, true) { id ->
                if (!id.isNullOrEmpty()) {
                    val f = LoginVerifyPinFragment()
                    val arg = Bundle()
                    arg.putString(RE_LOGIN_ID, id)
                    f.arguments = arg
                    supportFragmentManager.beginTransaction()
                            .replace(R.id.register_container, f)
                            .addToBackStack("sms")
                            .commit()
                }
            }

        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        disableStatusBarLightMode()

        super.onCreate(savedInstanceState)
        setSwipeBackEnable(false)
        setContentView(R.layout.me_activity_registration)

        val lastLoginUid = AmeLoginLogic.accountHistory.lastLoginUid()
        handleFirstGoToLogin(lastLoginUid)
    }

    private fun handleFirstGoToLogin(lastLoginUid: String?) {
        if (!lastLoginUid.isNullOrEmpty()) {
            val f = ReloginFragment()
            val arg = Bundle()
            arg.putString(RE_LOGIN_ID, lastLoginUid)
            f.arguments = arg

            //重登陆页面
            supportFragmentManager.beginTransaction()
                    .add(R.id.register_container, f, "relogin")
                    .commitNowAllowingStateLoss()
        } else {
            //登陆校验验证码页面
            val f = StartupFragment()
            val arg = Bundle()
            f.arguments = arg
            supportFragmentManager.beginTransaction()
                    .add(R.id.register_container, f, "startup")
                    .commitNowAllowingStateLoss()
        }

    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

}