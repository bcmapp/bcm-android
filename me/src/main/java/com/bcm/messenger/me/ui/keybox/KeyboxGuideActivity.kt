package com.bcm.messenger.me.ui.keybox

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.utils.isReleaseBuild
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.R
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.me_activity_keybox_guide.*

/**
 * Created by zjl on 2018/8/15.
 */
@Route(routePath = ARouterConstants.Activity.ME_KEYBOX_GUIDE)
class KeyboxGuideActivity : SwipeBaseActivity() {

    private val REQUEST_VERIFICATION_SCAN = 1001
    private val TAG = "KeyboxGuideActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        disableStatusBarLightMode()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_keybox_guide)
        initView()
    }

    fun initView() {
        keybox_guide_title.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickCenter() {
                if (!isReleaseBuild()) {
                    BcmRouter.getInstance().get(ARouterConstants.Activity.APP_DEV_SETTING).navigation(this@KeyboxGuideActivity)
                }
            }

            override fun onClickRight() {
                // 采用新的扫一扫
                callScanActivity()
            }
        })
        keybox_scan_and_import.setOnClickListener {
            if (AmeLoginLogic.getAccountList().isNotEmpty()) {
                BcmRouter.getInstance().get(ARouterConstants.Activity.ME_KEYBOX).navigation(this)
                finish()
            } else {
                // 采用新的扫一扫
                callScanActivity()
            }

        }

        if (!isReleaseBuild()) {
            env_test_layout.visibility = View.VISIBLE
            env_export_account_list.setOnClickListener {
                AmeLoginLogic.accountHistory.export()
                ToastUtil.show(this@KeyboxGuideActivity, "导出成功")
            }

            env_import_account_list.setOnClickListener {
                AmeLoginLogic.accountHistory.import()
                ToastUtil.show(this@KeyboxGuideActivity, "导入成功")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VERIFICATION_SCAN && resultCode == Activity.RESULT_OK) {
            handleQrScan(data?.getStringExtra(ARouterConstants.PARAM.SCAN.SCAN_RESULT))
        }
    }

    private fun callScanActivity() {
        BcmRouter.getInstance().get(ARouterConstants.Activity.SCAN_NEW)
                .putString(ARouterConstants.PARAM.SCAN.SCAN_CHARSET, "utf-8")
                .putInt(ARouterConstants.PARAM.SCAN.SCAN_TYPE, ARouterConstants.PARAM.SCAN.TYPE_ACCOUNT)
                .navigation(this@KeyboxGuideActivity, REQUEST_VERIFICATION_SCAN)
    }

    private fun handleQrScan(qrCode: String?) {

        AmeLoginLogic.saveBackupFromExportModelWithWarning(qrCode ?: return) { accountId ->
            if (!accountId.isNullOrEmpty()) {
                //扫码成功，跳转到keybox页面
                BcmRouter.getInstance().get(ARouterConstants.Activity.ME_KEYBOX).navigation(this)
                finish()
            }
        }
    }
}