package com.bcm.messenger.me.ui.login

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.BaseFragment
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.R
import com.bcm.messenger.me.ui.keybox.MyAccountKeyActivity
import com.bcm.messenger.me.ui.keybox.VerifyKeyActivity
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.logger.ALog
import kotlinx.android.synthetic.main.me_fragment_change_password.*
import java.util.regex.Pattern

/**
 *
 * Created by wjh on 2019-12-10
 */
class ChangePasswordFragment : BaseFragment() {
    private val TAG = "ChangePasswordFragment"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_fragment_change_password, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        change_password_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                activity?.finish()
            }

            override fun onClickRight() {
                handleChangePassword()
            }
        })

        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                checkInputLegitimate()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }
        }

        origin_pwd_clear.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            origin_pwd_edit.text.clear()
        }
        new_pwd_clear.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            new_pwd_edit.text.clear()
        }
        confirm_pwd_clear.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            confirm_pwd_edit.text.clear()
        }

        origin_pwd_edit?.addTextChangedListener(watcher)
        new_pwd_edit?.addTextChangedListener(watcher)
        confirm_pwd_edit?.addTextChangedListener(watcher)

        val builder = SpannableStringBuilder(getString(R.string.me_change_password_notice_part))
        builder.append(StringAppearanceUtil.applyAppearance(getString(R.string.me_change_password_notce_action), color = getColorCompat(R.color.common_app_primary_color)))
        change_password_notice?.text = builder
        change_password_notice?.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            val a = activity
            if (a is ChangePasswordActivity) {
                a.gotoNote()
            }
        }

        checkInputLegitimate()
    }

    //检测输入合法性
    private fun checkInputLegitimate(): Boolean {
        origin_pwd_clear?.visibility = if (origin_pwd_edit.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        new_pwd_clear?.visibility = if (new_pwd_edit.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        confirm_pwd_clear?.visibility = if (confirm_pwd_edit.text.isNullOrEmpty()) View.GONE else View.VISIBLE

        val enable = if (!Pattern.matches(ARouterConstants.PASSWORD_REGEX, new_pwd_edit.text)) {
            confirm_pwd_notice.visibility = View.GONE
            new_pwd_notice.visibility = if (new_pwd_edit.text.isNullOrEmpty()) View.GONE else View.VISIBLE
            new_pwd_notice.setTextColor(getColorCompat(R.color.common_content_warning_color))
            new_pwd_notice.text = getString(R.string.me_change_password_weak_description)
            ALog.d(TAG, "new_pwd_edit is weak")
            false
        } else if (!checkLegitimateForNewPassword()) {
            new_pwd_notice.visibility = View.VISIBLE
            confirm_pwd_notice.visibility = if (confirm_pwd_edit.text.isNullOrEmpty()) View.GONE else View.VISIBLE
            new_pwd_notice.setTextColor(getColorCompat(R.color.common_app_green_color))
            new_pwd_notice.text = getString(R.string.me_change_password_strong_description)
            confirm_pwd_notice.setTextColor(getColorCompat(R.color.common_content_warning_color))
            confirm_pwd_notice.text = getString(R.string.me_change_password_not_match_description)
            ALog.d(TAG, "new_pwd_edit not match confirm_pwd_edit")
            false
        } else {
            new_pwd_notice.visibility = View.VISIBLE
            confirm_pwd_notice.visibility = View.VISIBLE
            new_pwd_notice.setTextColor(getColorCompat(R.color.common_app_green_color))
            new_pwd_notice.text = getString(R.string.me_change_password_strong_description)
            confirm_pwd_notice.setTextColor(getColorCompat(R.color.common_app_green_color))
            confirm_pwd_notice.text = getString(R.string.me_change_password_match_description)
            !origin_pwd_edit.text.isNullOrEmpty()
        }
        if (enable) {
            change_password_title_bar?.enableRight()
        } else {
            change_password_title_bar?.disableRight()
        }
        return enable
    }

    //检测新密码和确认新密码是否一致
    private fun checkLegitimateForNewPassword(): Boolean {
        return new_pwd_edit?.text?.toString() == confirm_pwd_edit?.text?.toString()
    }

    /**
     * 执行修改密码行为
     */
    private fun handleChangePassword() {
        if (origin_pwd_edit.text.toString() == new_pwd_edit.text.toString()) {
            AmePopup.result.failure(activity, getString(R.string.me_change_password_same_origin_warning), true)
        } else {
            AmePopup.loading.show(activity)
            AmeModuleCenter.user(accountContext)?.changePinPasswordAsync(activity as? AppCompatActivity, origin_pwd_edit.text.toString(), confirm_pwd_edit.text.toString()) { result, _ ->
                if (result) {
                    AmePopup.loading.dismiss()
                    AmeLoginLogic.accountHistory.resetBackupState(accountContext.uid)
                    AmePopup.result.succeed(activity, getString(R.string.me_change_password_success)) {
                        try {
                            //更改密码之后，提示备份账号
                            AmePopup.center.newBuilder().withCancelable(true)
                                    .withContent(getString(R.string.me_backup_after_change_pwd_notice))
                                    .withOkTitle(getString(R.string.me_backup_after_change_pwd_action))
                                    .withCancelTitle(getString(R.string.common_later))
                                    .withCancelListener {
                                        activity?.finish()
                                    }
                                    .withOkListener {
                                        val intent = Intent(activity, MyAccountKeyActivity::class.java)
                                        intent.putExtra(VerifyKeyActivity.ACCOUNT_ID, accountContext.uid)
                                        startActivity(intent)

                                        activity?.finish()
                                    }
                                    .show(activity)

                        } catch (ex: Exception) {
                            ALog.e(TAG, "handleChangePassword after show notice fail", ex)
                        }
                    }
                } else {
                    AmePopup.loading.dismiss()
                    AmePopup.result.failure(activity, getString(R.string.me_change_password_error))
                }
            }
        }
    }
}