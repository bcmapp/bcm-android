package com.bcm.messenger.me.ui.setting

import android.os.Bundle
import android.view.View
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.common.utils.getPackageInfo
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.me.BuildConfig
import com.bcm.messenger.me.R
import com.bcm.messenger.me.utils.BcmUpdateUtil
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.me_activity_about.*
import com.bcm.messenger.common.SwipeBaseActivity
import java.lang.ref.WeakReference
import java.util.*


/**
 * Created by zjl on 2018/5/4.
 */
class AboutActivity : SwipeBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_about)

        about_title.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }
        })

        about_version.text = getString(R.string.me_version_head, getPackageInfo().versionName + "-" + getPackageInfo().versionCode)

        about_policy_layout.setOnClickListener {
            BcmRouter.getInstance().get(ARouterConstants.Activity.WEB).putString(ARouterConstants.PARAM.WEB_URL, BuildConfig.POLICY_HOST).navigation(this)
        }

        about_cooperation_layout.setOnClickListener {
            if (Locale.getDefault() == Locale.SIMPLIFIED_CHINESE) {
                BcmRouter.getInstance().get(ARouterConstants.Activity.WEB).putString(ARouterConstants.PARAM.WEB_URL, BuildConfig.BCM_COOPERATION_ZH_ADDRESS).navigation(this)
            } else {
                BcmRouter.getInstance().get(ARouterConstants.Activity.WEB).putString(ARouterConstants.PARAM.WEB_URL, BuildConfig.BCM_COOPERATION_EN_ADDRESS).navigation(this)
            }
        }

        about_faq_layout.setOnClickListener {
            if (Locale.getDefault() == Locale.SIMPLIFIED_CHINESE) {
                BcmRouter.getInstance().get(ARouterConstants.Activity.WEB).putString(ARouterConstants.PARAM.WEB_URL, BuildConfig.BCM_FAQ_ZH_ADDRESS).navigation(this)
            } else {
                BcmRouter.getInstance().get(ARouterConstants.Activity.WEB).putString(ARouterConstants.PARAM.WEB_URL, BuildConfig.BCM_FAQ_EN_ADDRESS).navigation(this)
            }
        }

        about_donate_layout.setOnClickListener {
            BcmRouter.getInstance().get(ARouterConstants.Activity.WEB).putString(ARouterConstants.PARAM.WEB_URL, BuildConfig.DONATE_ADDRESS).navigation(this)
        }

        about_os_licenses_layout.setOnClickListener {
            BcmRouter.getInstance().get(ARouterConstants.Activity.WEB).putString(ARouterConstants.PARAM.WEB_URL, BuildConfig.BCM_OPEN_SOURCE_LICENSE_ADDRESS).navigation(this)
        }

        if(!AppUtil.isReleaseBuild()){
            about_env_setting.visibility = View.VISIBLE
            about_env_setting.setOnClickListener{
                BcmRouter.getInstance().get(ARouterConstants.Activity.APP_DEV_SETTING).navigation(this)
            }
        }
    }

    private fun checkUpgradeInfo() {
        val weakThis = WeakReference(this)

        BcmUpdateUtil.checkUpdate { hasUpdate, forceUpdate, version ->
            if (hasUpdate) {
                weakThis.get()?.setHasVersionStatus(version)
                if (forceUpdate) {
                    BcmUpdateUtil.showForceUpdateDialog()
                }
            } else {
                weakThis.get()?.setNoVersionStatus()
            }
        }
    }

    private fun setHasVersionStatus(versionName: String) {
        if (isFinishing){
            return
        }

        about_update_title.visibility = View.GONE
        about_update_red_dot.visibility = View.VISIBLE
        about_update_conetnt.visibility = View.VISIBLE
        about_update_conetnt.text = getString(R.string.common_new_version_tip, versionName)
        about_update_btn.text = getString(R.string.me_about_new_version_status)
        about_update_btn.setTextColor(getColorCompat(R.color.common_color_white))
        about_update_btn.background = AppUtil.getDrawable(resources, R.drawable.common_blue_round_bg)
        about_update_layout.setOnClickListener {
            BcmUpdateUtil.showUpdateDialog()
        }
    }

    private fun setNoVersionStatus() {
        if (isFinishing){
            return
        }

        about_update_conetnt.visibility = View.GONE
        about_update_red_dot.visibility = View.GONE
        about_update_title.visibility = View.VISIBLE
        about_update_btn.text = getString(R.string.me_about_latest_version_status)
        about_update_btn.setTextColor(getColorCompat(R.color.common_disable_color))
        about_update_btn.background = AppUtil.getDrawable(resources, R.drawable.common_grey_round_bg)
    }

    override fun onResume() {
        super.onResume()
        checkUpgradeInfo()
    }
}