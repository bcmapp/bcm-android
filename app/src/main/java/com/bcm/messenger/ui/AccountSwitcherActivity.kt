package com.bcm.messenger.ui

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import com.bcm.messenger.R
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.ThemeBaseActivity
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.setStatusBarLightMode
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.ui.keybox.VerifyKeyActivity
import com.bcm.messenger.me.ui.login.RegistrationActivity
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.activity_account_switcher.*

/**
 * Created by Kin on 2020/1/9
 */
@Route(routePath = ARouterConstants.Activity.ACCOUNT_SWITCHER)
class AccountSwitcherActivity : ThemeBaseActivity() {
    private val REQ_SCAN_ACCOUNT = 1001
    private val REQ_SCAN_LOGIN = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_account_switcher)
        initView()

        val uid = intent.getStringExtra(ARouterConstants.PARAM.PARAM_UID) ?: ""
        if (uid.isNotEmpty()) {
            val intent = Intent(this, VerifyKeyActivity::class.java).apply {
                putExtra(RegistrationActivity.RE_LOGIN_ID, uid)
            }
            startBcmActivity(AmeLoginLogic.getAccountContext(uid), intent)
        }

        window.setStatusBarLightMode()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_SCAN_ACCOUNT -> switcher_profile_layout.analyseQrCode(data, false)
            REQ_SCAN_LOGIN -> switcher_profile_layout.analyseQrCode(data, true)
        }
    }

    private fun initView() {
        switcher_profile_layout.setListener(object : HomeProfileLayout.HomeProfileListener {
            override fun onClickExit() {
            }

            override fun onDragVertically(ev: MotionEvent?): Boolean {
                return false
            }

            override fun onInterceptEvent(ev: MotionEvent?): Boolean {
                return false
            }

            override fun onViewPagerScrollStateChanged(newState: Int) {

            }

            override fun onViewChanged(newRecipient: Recipient?) {

            }
        })

        if (!AppUtil.isReleaseBuild()) {
            switcher_env_setting.visibility = View.VISIBLE
        }

        switcher_env_setting.setOnClickListener {
            BcmRouter.getInstance().get(ARouterConstants.Activity.APP_DEV_SETTING).navigation(this)
        }
    }
}