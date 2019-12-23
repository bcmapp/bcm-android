package com.bcm.messenger.me.ui.profile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.utils.isReleaseBuild
import com.bcm.messenger.common.utils.saveTextToBoard
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.me.R
import com.bcm.messenger.me.ui.destroy.DestroyAccountDialog
import com.bcm.messenger.me.ui.destroy.DestroyCheckPasswordFragment
import com.bcm.messenger.me.ui.keybox.SwitchAccountAdapter
import com.bcm.messenger.me.ui.keybox.VerifyKeyActivity
import com.bcm.messenger.me.ui.login.ChangePasswordActivity
import com.bcm.messenger.me.ui.login.backup.MyAccountKeyActivity
import com.bcm.messenger.me.ui.login.backup.VerifyFingerprintActivity
import com.bcm.messenger.me.ui.qrcode.BcmMyQRCodeActivity
import com.bcm.messenger.utility.InputLengthFilter
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.logger.ALog
import kotlinx.android.synthetic.main.me_fragment_my_profile.*

/**
 *
 * Created by wjh on 2019-12-11
 */
class MyProfileFragment : Fragment(), RecipientModifiedListener {

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
                intent.putExtra(VerifyKeyActivity.ACCOUNT_ID, AMESelfData.uid)
                startActivity(intent)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_fragment_my_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val address: Address? = arguments?.getParcelable(ARouterConstants.PARAM.PARAM_ADDRESS)
        if (address == null) {
            activity?.finish()
            return
        }
        recipient = Recipient.from(view.context, address, true)
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
            startActivityForResult(Intent(activity, VerifyFingerprintActivity::class.java), VERIFY_REQUEST)
        }

        profile_change_pwd_item?.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            val intent = Intent(activity, ChangePasswordActivity::class.java)
            startActivity(intent)
        }

        profile_logout_btn?.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            try {
                SwitchAccountAdapter().switchAccount(it.context, recipient.address.toString(), Recipient.fromSelf(it.context, true))
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
        profile_id_item?.setTip(InputLengthFilter.filterString(AMESelfData.uid, 15), 0)
        if (!isReleaseBuild()) {
            // 非正式包允许长按复制ID，方便查问题
            profile_id_item?.setOnLongClickListener {
                it.context.saveTextToBoard(AMESelfData.uid)
                ToastUtil.show(it.context, "User ID has been copied to clipboard")
                return@setOnLongClickListener true
            }
        }

        profile_qr_item?.setOnClickListener {
            val intent = Intent(activity, BcmMyQRCodeActivity::class.java)
            startActivity(intent)
        }

        initProfile(recipient)

        checkBackupNotice(recipient)
    }

    private fun checkBackupNotice(recipient: Recipient) {
        if (recipient.isSelf) {
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
        startActivity(Intent(activity, EditNameActivity::class.java).apply {
            putExtra(ARouterConstants.PARAM.PARAM_ADDRESS, recipient.address)
        })

    }

    /**
     * 处理头像编辑
     */
    private fun handleAvatarEdit() {
        val intent = Intent(activity, ImageViewActivity::class.java)
        intent.putExtra(ARouterConstants.PARAM.PARAM_ADDRESS, recipient.address)
        intent.putExtra(ARouterConstants.PARAM.ME.PROFILE_EDIT, true)
        startActivity(intent)
    }

    /**
     * 初始化profile
     */
    private fun initProfile(recipient: Recipient) {

        profile_icon?.setPhoto(recipient, IndividualAvatarView.PROFILE_PHOTO_TYPE)
        profile_name_item?.setTip(recipient.bcmName ?: recipient.address.format())
    }

}