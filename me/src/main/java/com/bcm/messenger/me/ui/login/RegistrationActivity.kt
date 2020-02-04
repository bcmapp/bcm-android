package com.bcm.messenger.me.ui.login

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.setStatusBarLightMode
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.R
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter

/**
 * Created by ling
 */
@Route(routePath = ARouterConstants.Activity.USER_REGISTER_PATH)
class RegistrationActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "RegistrationActivity"
        const val RE_LOGIN_ID = "RE_LOGIN_ID"
        const val REQUEST_CODE_SCAN_QR_LOGIN = 10013
        const val REQUEST_CODE_SCAN_QR_IMPORT = 10014
        const val CREATE_ACCOUNT_ID = "CREATE_ACCOUNT"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            val action: (uid: String) -> Unit = when (requestCode) {
                REQUEST_CODE_SCAN_QR_LOGIN -> {
                    {
                        if (it.isNotEmpty()) {
                            val f = LoginVerifyPinFragment()
                            val arg = Bundle()
                            arg.putString(RE_LOGIN_ID, it)
                            f.arguments = arg
                            supportFragmentManager.beginTransaction()
                                    .replace(R.id.register_container, f)
                                    .addToBackStack("sms")
                                    .commit()
                        }
                    }
                }
                REQUEST_CODE_SCAN_QR_IMPORT -> {
                    {
                        if (it.isNotEmpty()) {
                            BcmRouter.getInstance().get(ARouterConstants.Activity.ACCOUNT_SWITCHER)
                                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    .navigation(this)
                        }
                    }
                }
                else -> return
            }

            val scanResult = data?.getStringExtra(ARouterConstants.PARAM.SCAN.SCAN_RESULT) ?: return
            AmeLoginLogic.saveBackupFromExportModelWithWarning(scanResult, true) { uid ->
                action(uid ?: "")
            }
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_registration)

        window.setStatusBarLightMode()

        val f = StartupFragment()
        val arg = Bundle()
        arg.putBoolean(CREATE_ACCOUNT_ID, intent.getBooleanExtra(CREATE_ACCOUNT_ID, false))
        f.arguments = arg
        supportFragmentManager.beginTransaction()
                .add(R.id.register_container, f, "startup")
                .commitNowAllowingStateLoss()

    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (supportFragmentManager.backStackEntryCount > 1) {
                supportFragmentManager.popBackStack()
                return true
            } else {
                if (AMELogin.isLogin || AmeModuleCenter.login().accountSize() > 0) {
                    finish()
                    return true
                } else {
                    BcmRouter.getInstance().get(ARouterConstants.Activity.USER_REGISTER_PATH)
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .navigation()
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

}