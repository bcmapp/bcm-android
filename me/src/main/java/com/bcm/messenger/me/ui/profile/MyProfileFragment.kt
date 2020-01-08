package com.bcm.messenger.me.ui.profile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.BaseFragment
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.utils.isReleaseBuild
import com.bcm.messenger.common.utils.saveTextToBoard
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.messenger.common.utils.startBcmActivityForResult
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.R
import com.bcm.messenger.me.ui.destroy.DestroyAccountDialog
import com.bcm.messenger.me.ui.destroy.DestroyCheckPasswordFragment
import com.bcm.messenger.me.ui.keybox.MyAccountKeyActivity
import com.bcm.messenger.me.ui.keybox.SwitchAccount
import com.bcm.messenger.me.ui.login.ChangePasswordActivity
import com.bcm.messenger.me.ui.login.backup.VerifyFingerprintActivity
import com.bcm.messenger.me.ui.qrcode.BcmMyQRCodeActivity
import com.bcm.messenger.utility.InputLengthFilter
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.logger.ALog
import kotlinx.android.synthetic.main.me_fragment_my_profile.*

/**
 * Created by wjh on 2019-12-11
 *
 * This fragment MUST use the account context which is got from the arguments.
 */
class MyProfileFragment : BaseFragment(), RecipientModifiedListener {
    companion object {
        private const val TAG = "MyProfileFragment"
        private const val VERIFY_REQUEST = 1
    }

    private lateinit var recipient: Recipient

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VERIFY_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                val intent = Intent(activity, MyAccountKeyActivity::class.java)
                startBcmActivity(accountContext, intent)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_fragment_my_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recipient = Recipient.from(accountContext, accountContext.uid, true)
        recipient.addListener(this)
        initView()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::recipient.isInitialized) {
            recipient.removeListener(this)
        }
    }

    override fun onResume() {
        super.onResume()
        checkBackupNotice(recipient)
    }

    override fun onModified(recipient: Recipient) {
        if (this.recipient == recipient) {
            initProfile(recipient)
        }
    }

    private fun initView() {
        profile_title_bar?.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                activity?.finish()
            }
        })

        profile_photo_item?.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            handleAvatarEdit()
        }

        profile_name_item?.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            handleNameEdit()
        }

        profile_account_item?.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            val intent = Intent(activity, VerifyFingerprintActivity::class.java)
            startBcmActivityForResult(accountContext, intent, VERIFY_REQUEST)
        }

        profile_change_pwd_item?.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            val intent = Intent(activity, ChangePasswordActivity::class.java)
            startBcmActivity(accountContext, intent)
        }

        profile_logout_btn?.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            try {
                SwitchAccount().switchAccount(it.context, recipient.address.toString(), getAccountRecipient())
            } catch (ex: Exception) {
                ALog.e(TAG, "handleLogout error", ex)
            }
        }

        profile_destroy_btn?.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            val fragment = DestroyCheckPasswordFragment()
            val fm = fragmentManager ?: return@setOnClickListener
            fragment.arguments = arguments
            DestroyAccountDialog().setCallback {
                fragmentManager?.beginTransaction()
                        ?.setCustomAnimations(R.anim.common_slide_from_right, R.anim.common_popup_alpha_out, R.anim.common_popup_alpha_in, R.anim.common_slide_to_right)
                        ?.replace(R.id.profile_root_container, fragment)
                        ?.addToBackStack("destroy_check_password")
                        ?.commit()
            }.show(fm, "destroy_dialog")
        }

        profile_title_bar?.setCenterText(getString(R.string.me_profile_title))
        profile_id_item?.setTip(InputLengthFilter.filterString(getAccountRecipient().address.serialize(), 15), 0)
        if (!isReleaseBuild()) {
            // 非正式包允许长按复制ID，方便查问题
            profile_id_item?.setOnLongClickListener {
                it.context.saveTextToBoard(getAccountRecipient().address.serialize())
                ToastUtil.show(it.context, "User ID has been copied to clipboard")
                return@setOnLongClickListener true
            }
        }

        profile_qr_item?.setOnClickListener {
            val intent = Intent(activity, BcmMyQRCodeActivity::class.java)
            startBcmActivity(accountContext, intent)
        }

        initProfile(recipient)

        checkBackupNotice(recipient)
    }

    private fun checkBackupNotice(recipient: Recipient) {
        if (recipient.isLogin) {
            val hadBackup = AmeLoginLogic.accountHistory.getBackupTime(recipient.address.serialize()) > 0
            if (hadBackup) {
                profile_account_item.hideTip()
            } else {
                profile_account_item.setTip(getString(R.string.me_not_backed_up), R.drawable.common_not_backup_icon)
            }
        }
    }

    /**
     * 处理名称编辑
     */
    private fun handleNameEdit() {
        startBcmActivity(accountContext, Intent(activity, EditNameActivity::class.java))
    }

    /**
     * 处理头像编辑
     */
    private fun handleAvatarEdit() {
        val intent = Intent(activity, ImageViewActivity::class.java).apply {
            putExtra(ARouterConstants.PARAM.ME.PROFILE_EDIT, true)
        }
        startBcmActivity(accountContext, intent)
    }

    /**
     * 初始化profile
     */
    private fun initProfile(recipient: Recipient) {
        profile_icon?.setPhoto(recipient, IndividualAvatarView.PROFILE_PHOTO_TYPE)
        profile_name_item?.setTip(recipient.bcmName ?: recipient.address.format())
    }
}