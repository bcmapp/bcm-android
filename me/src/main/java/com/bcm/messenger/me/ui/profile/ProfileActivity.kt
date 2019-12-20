package com.bcm.messenger.me.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.database.repositories.RecipientRepo
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.CommonSettingItem
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.common.utils.isReleaseBuild
import com.bcm.messenger.common.utils.saveTextToBoard
import com.bcm.messenger.me.R
import com.bcm.messenger.me.provider.UserModuleImp
import com.bcm.messenger.me.ui.qrcode.BcmMyQRCodeActivity
import com.bcm.messenger.utility.InputLengthFilter
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import kotlinx.android.synthetic.main.me_activity_my_profile.*
import kotlinx.android.synthetic.main.me_activity_my_profile.profile_icon
import kotlinx.android.synthetic.main.me_activity_my_profile.profile_icon_arrow
import kotlinx.android.synthetic.main.me_activity_my_profile.profile_name_item
import kotlinx.android.synthetic.main.me_activity_my_profile.profile_photo_item
import kotlinx.android.synthetic.main.me_activity_my_profile.profile_title_bar
import kotlinx.android.synthetic.main.me_activity_other_profile.*

/**
 * Created by Kin on 2018/9/6
 */
@Route(routePath = ARouterConstants.Activity.PROFILE_EDIT)
class ProfileActivity : SwipeBaseActivity(), RecipientModifiedListener {

    private val TAG = "ProfileActivity"
    private lateinit var recipient: Recipient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val address = intent.getParcelableExtra<Address?>(ARouterConstants.PARAM.PARAM_ADDRESS)
            recipient = if (address == null) {
                Recipient.fromSelf(this, true)
            } else {
                Recipient.from(this, address, true)
            }
            recipient.addListener(this)

        }catch (ex: Exception) {
            ALog.e(TAG, "from recipient fail", ex)
            finish()
            return
        }

        if (recipient.isSelf) {
            setContentView(R.layout.me_activity_my_profile)
        }else {
            setContentView(R.layout.me_activity_other_profile)
        }

        initView()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::recipient.isInitialized) {
            recipient.removeListener(this)
        }
    }

    override fun onModified(recipient: Recipient) {
        profile_name_item?.post {
            if (this.recipient == recipient) {
                initProfile(recipient)
            }
        }
    }

    private fun initView() {

        profile_title_bar?.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }
        })

        profile_photo_item?.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            handleAvatarEdit(false)
        }

        profile_name_item?.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            handleNameEdit(false)
        }

        profile_display_photo_item?.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            handleAvatarEdit(true)
        }

        profile_display_alias_item?.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            handleNameEdit(true)
        }

        profile_display_control_layout?.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            handleReset()
        }

        if (recipient.isSelf) {
            profile_title_bar?.setCenterText(getString(R.string.me_profile_title))
            profile_id_item?.setTip(InputLengthFilter.filterString(AMESelfData.uid, 15), 0)
            if (!isReleaseBuild()) {
                // 非正式包允许长按复制ID，方便查问题
                profile_id_item?.setOnLongClickListener {
                    saveTextToBoard(AMESelfData.uid)
                    ToastUtil.show(this, "User ID has been copied to clipboard")
                    return@setOnLongClickListener true
                }
            }

            profile_qr_item?.setOnClickListener {
                val intent = Intent(this, BcmMyQRCodeActivity::class.java)
                startActivity(intent)
            }

        } else {
            profile_title_bar?.setCenterText(getString(R.string.me_local_profile_title))
            profile_name_item?.showRightIcon(CommonSettingItem.RIGHT_NONE)
            profile_icon_arrow?.visibility = View.GONE

            if (recipient.relationship == RecipientRepo.Relationship.STRANGER || recipient.relationship == RecipientRepo.Relationship.REQUEST) {

                profile_display_control_layout?.visibility = View.GONE
                profile_display_alias_item?.visibility = View.GONE
                profile_display_photo_item?.visibility = View.GONE
                profile_display_notice?.visibility = View.GONE
            }
        }

        initProfile(recipient)
    }

    private fun handleNameEdit(forLocal: Boolean) {
        //如果是自己，则进入的是个人编辑页面，这里可以点击，否则是其他联系人的个人资料页面，这里无法点击，必须是备注那里才可以点击
        if (forLocal || recipient.isSelf) {
            startActivity(Intent(this, EditNameActivity::class.java).apply {
                putExtra(ARouterConstants.PARAM.PARAM_ADDRESS, recipient.address)
                putExtra(ARouterConstants.PARAM.ME.PROFILE_FOR_LOCAL, forLocal)
            })
        }
    }

    private fun handleAvatarEdit(forLocal: Boolean) {
        val intent = Intent(this, ImageViewActivity::class.java)
        intent.putExtra(ARouterConstants.PARAM.PARAM_ADDRESS, recipient.address)
        intent.putExtra(ARouterConstants.PARAM.ME.PROFILE_EDIT, true)
        intent.putExtra(ARouterConstants.PARAM.ME.PROFILE_FOR_LOCAL, forLocal)
        startActivity(intent)
    }

    private fun handleReset() {
        if(recipient.localName.isNullOrEmpty() && recipient.localAvatar.isNullOrEmpty()) {
            ALog.d("ProfileActivity", "handleReset do nothing, because localName and localAvatar is null")
            return
        }
        AmePopup.bottom.newBuilder()
                .withTitle(getString(R.string.me_local_profile_all_reset_notice, recipient.name))
                .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.me_local_profile_reset_button), AmeBottomPopup.PopupItem.CLR_RED) {

                    val provider = UserModuleImp()
                    provider.updateNameProfile(recipient, "") {
                        provider.updateAvatarProfile(recipient, null) {

                        }
                    }

                })
                .withDoneTitle(getString(R.string.common_cancel))
                .show(this)
    }

    private fun initProfile(recipient: Recipient) {

        profile_icon?.setPhoto(recipient, IndividualAvatarView.PROFILE_PHOTO_TYPE)
        profile_name_item?.setTip(recipient.bcmName ?: recipient.address.format())

        if (!recipient.isSelf && recipient.relationship != RecipientRepo.Relationship.STRANGER && recipient.relationship != RecipientRepo.Relationship.REQUEST) {

            if (recipient.localAvatar.isNullOrEmpty()) {
                profile_display_icon_notice?.visibility = View.VISIBLE
                profile_display_icon?.visibility = View.GONE

            }else {
                profile_display_icon_notice?.visibility = View.GONE
                profile_display_icon?.visibility = View.VISIBLE
                profile_display_icon?.setPhoto(recipient, IndividualAvatarView.LOCAL_PHOTO_TYPE)
            }
            if (recipient.localName.isNullOrEmpty()) {
                profile_display_alias_item?.setTip(getString(R.string.me_other_local_empty_action))

            }else {
                profile_display_alias_item?.setTip(recipient.localName ?: "")
            }
            if (recipient.localName.isNullOrEmpty() && recipient.localAvatar.isNullOrEmpty()) {
                profile_display_clear?.setTextColor(getColorCompat(R.color.common_color_black_30_translucent))
            }else {
                profile_display_clear?.setTextColor(getColorCompat(R.color.common_content_warning_color))

            }
        }
    }
}