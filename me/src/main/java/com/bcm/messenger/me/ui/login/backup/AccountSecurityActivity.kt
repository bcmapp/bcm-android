package com.bcm.messenger.me.ui.login.backup

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.login.bean.AmeAccountData
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.R
import com.bcm.messenger.me.ui.keybox.MyAccountKeyActivity
import com.bcm.messenger.me.ui.keybox.SwitchAccount
import com.bcm.messenger.me.ui.keybox.VerifyKeyActivity
import com.bcm.messenger.me.ui.login.ChangePasswordActivity
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.me_activity_acount_security.*

/**
 * Created by wjh on 2018/6/6
 */
@Route(routePath = ARouterConstants.Activity.ME_ACCOUNT)
class AccountSecurityActivity : AccountSwipeBaseActivity() {

    private val SAFETY_VERIFY_CODE = 1

    private var account: AmeAccountData? = null
    private var isShowRedDot = true
    private var isShowTip = true

    override fun onCreate(savedInstanceState: Bundle?) {
        disableStatusBarLightMode()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.me_activity_acount_security)
        account_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                showAccountKeyDetails()
            }
        })
        logout_button.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick){
                return@setOnClickListener
            }
            handleLogout()
        }
        change_psw_head.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick){
                return@setOnClickListener
            }

            val intent = Intent(this, ChangePasswordActivity::class.java)
            startBcmActivity(intent)
        }

        me_keybox.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick){
                return@setOnClickListener
            }

            if (AmeLoginLogic.getAccountList().isNotEmpty()) {
                BcmRouter.getInstance().get(ARouterConstants.Activity.ME_KEYBOX).startBcmActivity(accountContext, this)
            } else {
                BcmRouter.getInstance().get(ARouterConstants.Activity.ME_KEYBOX_GUIDE).startBcmActivity(accountContext, this)
            }
        }

        me_account_backup_tip.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick){
                return@setOnClickListener
            }

            me_account_backup_tip.visibility = View.GONE
            if (isShowTip) {
                SuperPreferences.setAccountTipVisible(this, false)
                isShowTip = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        initCard()
    }

    override fun onLoginRecipientRefresh() {
        me_account_img?.setPhoto(accountRecipient)
    }

    private fun initCard() {
        initSelf()

        if (isShowTip) {
            me_account_backup_tip.visibility = View.VISIBLE
        } else {
            me_account_backup_tip.visibility = View.GONE
        }
        if (isShowRedDot) {
            me_backup_red_dot.visibility = View.VISIBLE
        } else {
            me_backup_red_dot.visibility = View.GONE
        }

        me_account_qrcode.setOnClickListener {
            showSafetyVerifyActivity()
            if (isShowRedDot) {
                SuperPreferences.setAccountRedDot(this, false)
                isShowRedDot = false
            }
            if (isShowTip) {
                SuperPreferences.setAccountTipVisible(this, false)
                isShowTip = false
            }
        }

        me_keybox.setTip(AmeLoginLogic.getAccountList().size.toString(), contentColor = getColorCompat(R.color.common_content_second_color))

    }

    private fun initSelf() {
        account = AmeLoginLogic.getMajorAccount()
        account?.let {
            isShowRedDot = SuperPreferences.getAccountRedDot(this)
            isShowTip = SuperPreferences.getAccountTipVisible(this)

            me_account_name.text = it.name
            account_generate_date.text = getString(R.string.me_str_generation_key_date, DateUtils.formatDayTime(it.genKeyTime))
            me_account_openid.text = "${getString(R.string.me_id_title)}: ${it.uid}"
            val backupState = AmeLoginLogic.accountHistory.getBackupTime(accountRecipient.address.serialize())

            val dateBuilder = SpannableStringBuilder()
            if (backupState > 0) {
                dateBuilder.append(AppUtil.getString(this, R.string.me_str_backup_date_export))
                val extra = DateUtils.formatDayTime(backupState)
                dateBuilder.append(extra)
                val foregroundColor = ForegroundColorSpan(getColorCompat(R.color.common_color_white))
                dateBuilder.setSpan(foregroundColor, dateBuilder.length - extra.length - 1, dateBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                isShowTip = false
                isShowRedDot = false
            } else {
                dateBuilder.append(AppUtil.getString(this, R.string.me_str_backup_date_export))
                val extra = getString(R.string.me_not_backed_up)
                dateBuilder.append(extra)
                val foregroundColor = ForegroundColorSpan(getColorCompat(R.color.common_color_ff3737))
                dateBuilder.setSpan(foregroundColor, dateBuilder.length - extra.length - 1, dateBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            account_backup_date.text = dateBuilder
            me_account_img.setPhoto(accountRecipient)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(resultCode == Activity.RESULT_OK){
            if (requestCode == SAFETY_VERIFY_CODE) {
                showQrCode()
            }
        }
    }

    private fun showQrCode() {
        val intent = Intent(this, MyAccountKeyActivity::class.java)
        intent.putExtra(VerifyKeyActivity.ACCOUNT_ID, accountRecipient.address.serialize())
        startBcmActivity(intent)
    }

    private fun showAccountKeyDetails() {
        AmeModuleCenter.user(accountContext)?.gotoBackupTutorial()
    }

    private fun handleLogout() {
        try {
            SwitchAccount.switchAccount(accountContext,this, accountRecipient)
        }catch (ex: Exception) {
            ALog.e("AccountSecurity", "handleLogout error", ex)
        }
    }

    private fun showSafetyVerifyActivity() {
        startBcmActivityForResult(Intent(this, VerifyFingerprintActivity::class.java), SAFETY_VERIFY_CODE)
    }
}
