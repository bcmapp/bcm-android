package com.bcm.messenger.me.ui.destroy

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.core.setLocale
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.me.R
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter

/**
 * Created by Kin on 2018/9/19
 */
@Route(routePath = ARouterConstants.Activity.ACCOUNT_DESTROY)
class DestroyAccountActivity : AppCompatActivity() {
    private val TAG = "DestroyAccountActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.me_activity_destroy_account)

        val fragment = ForcedLogOutFragment()
        val arg = Bundle()
        arg.putParcelable(ARouterConstants.PARAM.PARAM_ADDRESS, intent.getParcelableExtra(ARouterConstants.PARAM.PARAM_ADDRESS))
        arg.putString(ARouterConstants.PARAM.PARAM_CLIENT_INFO, intent.getStringExtra(ARouterConstants.PARAM.PARAM_CLIENT_INFO))
        arg.putString(ARouterConstants.PARAM.PARAM_ACCOUNT_ID, intent.getStringExtra(ARouterConstants.PARAM.PARAM_ACCOUNT_ID))
        fragment.arguments = arg
        supportFragmentManager.beginTransaction()
                .replace(R.id.destroy_container, fragment)
                .commit()
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(setLocale(newBase))
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        if (overrideConfiguration != null) {
            val uiMode = overrideConfiguration.uiMode
            overrideConfiguration.setTo(baseContext.resources.configuration)
            overrideConfiguration.uiMode = uiMode
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    override fun onBackPressed() {
        //屏蔽返回
        if (supportFragmentManager.backStackEntryCount > 0) {
            super.onBackPressed()
        }
    }

    fun gotoLogin() {
        if (AMESelfData.isLogin) {
            ALog.e(TAG, "登录态还没释放完")
            return
        }

        AmeModuleCenter.onLoginStateChanged("")

        BcmRouter.getInstance().get(ARouterConstants.Activity.USER_REGISTER_PATH)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .navigation(this)
        finish()
    }

}