package com.bcm.messenger.chats.group.setting

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import androidx.appcompat.app.AlertDialog
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.chats.group.logic.viewmodel.GroupViewModel
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.core.AmeFileUploader
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.core.corebean.BcmReviewGroupJoinRequest
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.utility.QREncoder
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.api.BcmRouter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.chats_activity_group_share_settings.*
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/**
 *
 * Created by wjh on 2019/6/6
 */
class GroupShareSettingsActivity : AccountSwipeBaseActivity() {

    private val TAG = "GroupShareSettingsActivity"
    private lateinit var mGroupModel: GroupViewModel
    private var mGroupShareContent: AmeGroupMessage.GroupShareContent? = null
    private var mGroupChangedEventHandling = AtomicBoolean(false)
    private var mShareQRLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chats_activity_group_share_settings)

        val groupId = intent.getLongExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, -1)

        val groupModel = GroupLogic.get(accountContext).getModel(groupId)
        if (null == groupModel) {
            finish()
            return
        }
        mGroupModel = groupModel
        initView()
    }

    private fun initView() {
        title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                onBackPressed()
            }

            override fun onClickRight() {
                ALog.d(TAG, "do revoke")
                if (mGroupModel.myRole() == AmeGroupMemberInfo.OWNER) {
                    doForRevoke()
                }
            }
        })

        if (mGroupModel.myRole() == AmeGroupMemberInfo.OWNER) {
            group_share_enable_item.setSwitchEnable(false)
            group_share_enable_item.setSwitchStatus(mGroupModel.isShareGroupEnable())
            group_share_enable_item.setOnClickListener {
                val switch = !group_share_enable_item.getSwitchStatus()
                doForShareEnable(switch)
            }
        } else {
            group_share_enable_item.visibility = View.GONE
        }


        group_share_name.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            goToGroupInfoEdit(mGroupModel.groupId(), mGroupModel.myRole())

        }
        group_share_arrow.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            goToGroupInfoEdit(mGroupModel.groupId(), mGroupModel.myRole())

        }

        group_share_forward_btn.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            val shareContent = mGroupShareContent
            if (shareContent?.shareLink == null) {
                AmePopup.result.failure(this@GroupShareSettingsActivity, getString(R.string.chats_group_share_forward_fail), true)
                return@setOnClickListener
            }
            BcmRouter.getInstance().get(ARouterConstants.Activity.GROUP_SHARE_FORWARD)
                    .putLong(ARouterConstants.PARAM.PARAM_GROUP_ID, mGroupModel.groupId())
                    .putString(ARouterConstants.PARAM.GROUP_SHARE.GROUP_SHARE_CONTENT, shareContent.toString())
                    .startBcmActivity(accountContext, this)
        }
        group_share_copy_btn.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            val link = mGroupShareContent?.shareLink
            if (link == null) {
                AmePopup.result.failure(this@GroupShareSettingsActivity, getString(R.string.chats_group_share_copy_fail), true)
                return@setOnClickListener
            }
            ALog.d(TAG, "group_share_link: $link")
            AppUtil.saveCodeToBoard(this@GroupShareSettingsActivity, link)
            AmePopup.result.succeed(this@GroupShareSettingsActivity, getString(R.string.chats_group_share_copy_success), true)
        }

        group_share_save_btn.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            if (mGroupShareContent?.shareLink == null) {
                AmeAppLifecycle.failure(getString(R.string.chats_group_share_save_fail), true)
                return@setOnClickListener
            }
            doForSaveShareUrl()
        }

        initGroupInfoView()
        switchGroupShareQR(mGroupModel.isShareGroupEnable())
    }

    private fun initGroupInfoView() {
        val groupInfo = mGroupModel.getGroupInfo()
        group_share_name.text = if (groupInfo.name.isNullOrEmpty()) {
            getString(R.string.common_chats_group_default_name)
        }else {
            groupInfo.name
        }
        group_share_logo.showGroupAvatar(accountContext, mGroupModel.groupId(), false)
    }

    private fun switchGroupShareQR(toShow: Boolean) {
        group_share_forward_btn.isEnabled = toShow
        group_share_copy_btn.isEnabled = toShow
        group_share_save_btn.isEnabled = toShow

        if (mGroupModel.myRole() == AmeGroupMemberInfo.OWNER) {
            group_admin_disable_tip.visibility = View.GONE
            if (toShow) {
                group_share_qr_layout?.visibility = View.VISIBLE
                group_share_action_layout?.visibility = View.VISIBLE
                title_bar.setRightText(getString(R.string.chats_group_share_revoke_action))
                checkLoadShareQr()

            } else {
                mGroupShareContent = null
                group_share_qr_layout?.visibility = View.GONE
                group_share_action_layout?.visibility = View.GONE
                title_bar.hideRightViews()
            }
        } else {
            title_bar.hideRightViews()
            group_share_action_layout?.visibility = View.VISIBLE
            group_share_qr_layout?.visibility = View.VISIBLE
            if (toShow) {
                group_admin_disable_tip.visibility = View.GONE
                checkLoadShareQr()
            } else {
                group_admin_disable_tip.visibility = View.VISIBLE
                mGroupShareContent = null
            }
        }

    }

    private fun checkLoadShareQr() {
        val groupInfo = mGroupModel.getGroupInfo()
        if (mShareQRLoading) {
            return
        }
        mShareQRLoading = true
        group_share_loading.visibility = View.VISIBLE
        group_share_loading.setOnClickListener(null)
        val anim = RotateAnimation(0f, 359f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        anim.interpolator = LinearInterpolator()
        anim.repeatCount = Animation.INFINITE
        anim.duration = 2000
        anim.fillAfter = true
        group_share_loading.clearAnimation()
        group_share_loading.startAnimation(anim)

        val weakSelf = WeakReference(this)
        ALog.d(TAG, "switchGroupShareQR")


        mGroupModel.getGroupShareData(groupInfo.gid) { shareContent ->
            ALog.d(TAG, "switchGroupShareQR shareLink: ${shareContent?.shareLink}")
            if (shareContent?.shareLink == weakSelf.get()?.mGroupShareContent?.shareLink) {
                if (!shareContent?.shareLink.isNullOrEmpty()) {
                    weakSelf.get()?.group_share_loading?.clearAnimation()
                    weakSelf.get()?.group_share_loading?.visibility = View.GONE
                }
                return@getGroupShareData
            }

            weakSelf.get()?.mGroupShareContent = shareContent
            AmeDispatcher.io.dispatch {
                weakSelf.get()?.mShareQRLoading = false
                val bitmap = getBitmap(shareContent?.shareLink ?: return@dispatch)
                AmeDispatcher.mainThread.dispatch {
                    weakSelf.get()?.setQrComplete(bitmap)
                }
            }
        }
    }

    private fun setQrComplete(qr: Bitmap?) {
        group_share_qr.visibility = View.VISIBLE
        group_share_loading.clearAnimation()
        if (qr != null) {
            group_share_qr.setImageBitmap(qr)
            group_share_loading.visibility = View.GONE
        } else {
            group_share_qr.setImageResource(0)
            group_share_loading.setImageResource(R.drawable.common_refresh_black_icon)
            group_share_loading.setOnClickListener {
                if (QuickOpCheck.getDefault().isQuick) {
                    return@setOnClickListener
                }
                switchGroupShareQR(mGroupModel.isShareGroupEnable())
            }
        }
    }

    private fun getBitmap(shareLink: String): Bitmap {
        val qrEncoder = QREncoder(shareLink, dimension = 250.dp2Px(), charset = "utf-8")
        return qrEncoder.encodeAsBitmap()
    }

    private fun doForSaveShareUrl() {
        val bitmap = group_share_qr_layout.createScreenShot()
        Observable.create<String> {
            val path = BcmFileUtils.saveBitmap2File(bitmap, "BCM_GROUP_SHARE_CARD_${mGroupModel.groupId()}.jpg", AmeFileUploader.DCIM_DIRECTORY)
            if (path == null) {
                it.onError(Exception("Save QR code error"))
                return@create
            }
            it.onNext(path)
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    MediaScannerConnection.scanFile(this, arrayOf(it), arrayOf(BcmFileUtils.IMAGE_PNG), null)
                    AmeAppLifecycle.succeed(getString(R.string.chats_group_share_save_success), true)
                }, {
                    AmeAppLifecycle.failure(getString(R.string.chats_group_share_save_fail), true)
                })
    }

    private fun doForRevoke() {
        val joiningRequest = mGroupModel.getJoinRequestList()
        if (joiningRequest.isEmpty()) {
            AmePopup.bottom.newBuilder().withTitle(getString(R.string.chats_group_share_revoke_confirm_title))
                    .withCancelable(true)
                    .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.chats_group_share_revoke_confirm_content), getColorCompat(R.color.common_content_warning_color)) {
                        AmeAppLifecycle.showLoading()
                        mGroupModel.refreshShareData { succeed, error ->
                            ALog.d(TAG, "doForRevoke success: $succeed, error: $error")
                            AmeAppLifecycle.hideLoading()
                            if (succeed) {
                                AmeAppLifecycle.succeed(getString(R.string.chats_group_share_revoke_success), true)
                                switchGroupShareQR(mGroupModel.isShareGroupEnable())

                            } else {
                                AmeAppLifecycle.succeed(getString(R.string.chats_group_share_revoke_fail), true)

                            }
                        }
                    })
                    .withDoneTitle(getString(R.string.common_cancel))
                    .show(this)
        } else {
            val oneText = StringAppearanceUtil.applyAppearance(getString(R.string.chats_group_share_revoke_pending_request_confirm_approve), color = getColorCompat(R.color.common_content_warning_color))
            val twoText = StringAppearanceUtil.applyAppearance(getString(R.string.chats_group_share_revoke_pending_request_confirm_reject), color = getColorCompat(R.color.common_content_warning_color))
            var thirdText = StringAppearanceUtil.applyAppearance(getString(R.string.common_later), color = getColorCompat(R.color.common_color_black))
            thirdText = StringAppearanceUtil.applyAppearance(this, thirdText, true)
            AlertDialog.Builder(this).setTitle(getString(R.string.chats_group_share_revoke_pending_request_confirm_title))
                    .setItems(arrayOf(oneText, twoText, thirdText)) { dialog, which ->
                        when (which) {
                            0 -> {
                                AmeAppLifecycle.showLoading()
                                mGroupModel.reviewJoinRequests(joiningRequest.map {
                                    BcmReviewGroupJoinRequest(it.uid, it.reqId, true)
                                }) { succeed, error ->
                                    ALog.d(TAG, "doForRevoke review true success: $succeed, error: $error")
                                    if (succeed) {
                                        mGroupModel.refreshShareData { succeed, error ->
                                            ALog.d(TAG, "doForRevoke success: $succeed, error: $error")
                                            AmeAppLifecycle.hideLoading()
                                            if (succeed) {
                                                AmeAppLifecycle.succeed(getString(R.string.chats_group_share_revoke_success), true)
                                                switchGroupShareQR(mGroupModel.isShareGroupEnable())
                                            } else {
                                                AmeAppLifecycle.failure(getString(R.string.chats_group_share_revoke_fail), true)
                                            }
                                        }
                                    } else {
                                        AmeAppLifecycle.hideLoading()
                                        AmeAppLifecycle.failure(getString(R.string.chats_group_share_revoke_fail), true)
                                    }
                                }
                            }
                            1 -> {
                                AmeAppLifecycle.showLoading()
                                mGroupModel.reviewJoinRequests(joiningRequest.map {
                                    BcmReviewGroupJoinRequest(it.uid, it.reqId, false)
                                }) { succeed, error ->
                                    ALog.d(TAG, "doForRevoke review false success: $succeed, error: $error")
                                    if (succeed) {
                                        mGroupModel.refreshShareData { succeed, error ->
                                            ALog.d(TAG, "doForRevoke success: $succeed, error: $error")
                                            AmeAppLifecycle.hideLoading()
                                            if (succeed) {
                                                AmeAppLifecycle.succeed(getString(R.string.chats_group_share_revoke_success), true)
                                                switchGroupShareQR(mGroupModel.isShareGroupEnable())
                                            } else {
                                                AmeAppLifecycle.failure(getString(R.string.chats_group_share_revoke_fail), true)
                                            }
                                        }
                                    } else {
                                        AmeAppLifecycle.hideLoading()
                                        AmeAppLifecycle.failure(getString(R.string.chats_group_share_revoke_fail), true)
                                    }
                                }
                            }
                            else -> dialog.cancel()
                        }
                    }.create().show()
        }
    }

    private fun doForShareEnable(enable: Boolean) {
        if (enable) {
            AmeAppLifecycle.showLoading()
            mGroupModel.enableShareGroup { succeed, error ->
                ALog.d(TAG, "enableShareGroup success: $succeed, error: $error")
                AmeAppLifecycle.hideLoading()
                if (succeed) {
                    AmeAppLifecycle.succeed(getString(R.string.chats_group_setting_share_enable_success), true)
                    group_share_enable_item.setSwitchStatus(enable)
                    switchGroupShareQR(true)
                } else {
                    AmeAppLifecycle.failure(getString(R.string.chats_group_setting_share_enable_fail), true)
                }
            }
        } else {
            val joiningRequest = mGroupModel.getJoinRequestList()
            if (joiningRequest.isEmpty()) {
                AmePopup.bottom.newBuilder().withTitle(getString(R.string.chats_group_share_disable_confirm_title))
                        .withCancelable(true)
                        .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.chats_group_share_disable_confirm_content), getColorCompat(R.color.common_content_warning_color)) {
                            AmeAppLifecycle.showLoading()
                            mGroupModel.disableShareGroup { succeed, error ->
                                ALog.d(TAG, "disableShareGroup success: $succeed, error: $error")
                                AmeAppLifecycle.hideLoading()
                                if (succeed) {
                                    AmeAppLifecycle.succeed(getString(R.string.chats_group_setting_share_enable_success), true)
                                    group_share_enable_item.setSwitchStatus(enable)
                                    switchGroupShareQR(false)
                                } else {
                                    AmeAppLifecycle.failure(getString(R.string.chats_group_setting_share_enable_fail), true)
                                }
                            }
                        })
                        .withDoneTitle(getString(R.string.common_cancel))
                        .show(this)
            } else {
                val oneText = StringAppearanceUtil.applyAppearance(getString(R.string.chats_group_share_disable_pending_request_confirm_approve), color = getColorCompat(R.color.common_content_warning_color))
                val twoText = StringAppearanceUtil.applyAppearance(getString(R.string.chats_group_share_disable_pending_request_confirm_reject), color = getColorCompat(R.color.common_content_warning_color))
                var thirdText = StringAppearanceUtil.applyAppearance(getString(R.string.common_later), color = getColorCompat(R.color.common_color_black))
                thirdText = StringAppearanceUtil.applyAppearance(this, thirdText, true)
                AlertDialog.Builder(this).setTitle(getString(R.string.chats_group_share_disable_pending_request_confirm_title))
                        .setItems(arrayOf(oneText, twoText, thirdText)) { dialog, which ->
                            when (which) {
                                0 -> {
                                    AmeAppLifecycle.showLoading()
                                    mGroupModel.reviewJoinRequests(joiningRequest.map {
                                        BcmReviewGroupJoinRequest(it.uid, it.reqId, true)
                                    }) { succeed, error ->
                                        ALog.d(TAG, "doForShareEnable enable: $enable, review success: $succeed, error: $error")
                                        if (succeed) {
                                            mGroupModel.disableShareGroup { succeed, error ->
                                                ALog.d(TAG, "disableShareGroup success: $succeed, error: $error")
                                                AmeAppLifecycle.hideLoading()
                                                if (succeed) {
                                                    AmeAppLifecycle.succeed(getString(R.string.chats_group_setting_share_enable_success), true)
                                                    group_share_enable_item.setSwitchStatus(enable)
                                                    switchGroupShareQR(false)

                                                } else {
                                                    AmeAppLifecycle.failure(getString(R.string.chats_group_setting_share_enable_fail), true)
                                                }
                                            }
                                        } else {
                                            AmeAppLifecycle.hideLoading()
                                            AmeAppLifecycle.failure(getString(R.string.chats_group_setting_share_enable_fail), true)
                                        }
                                    }
                                }
                                1 -> {
                                    AmeAppLifecycle.showLoading()
                                    mGroupModel.reviewJoinRequests(joiningRequest.map {
                                        BcmReviewGroupJoinRequest(it.uid, it.reqId, false)
                                    }) { succeed, error ->
                                        ALog.d(TAG, "doForShareEnable enable: $enable, review success: $succeed, error: $error")
                                        if (succeed) {
                                            mGroupModel.disableShareGroup { succeed, error ->
                                                ALog.d(TAG, "disableShareGroup success: $succeed, error: $error")
                                                AmeAppLifecycle.hideLoading()
                                                if (succeed) {
                                                    AmeAppLifecycle.succeed(getString(R.string.chats_group_setting_share_enable_success), true)
                                                    group_share_enable_item.setSwitchStatus(enable)
                                                    switchGroupShareQR(false)

                                                } else {
                                                    AmeAppLifecycle.failure(getString(R.string.chats_group_setting_share_enable_fail), true)
                                                }
                                            }
                                        } else {
                                            AmeAppLifecycle.hideLoading()
                                            AmeAppLifecycle.failure(getString(R.string.chats_group_setting_share_enable_fail), true)
                                        }
                                    }
                                }
                                else -> dialog.cancel()
                            }
                        }.create().show()
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: GroupViewModel.GroupInfoChangedEvent) {
        ALog.d(TAG, "onGroupInfoChangedEvent")
        if (mGroupChangedEventHandling.compareAndSet(false, true)) {
            initGroupInfoView()
            switchGroupShareQR(mGroupModel.isShareGroupEnable())
            mGroupChangedEventHandling.compareAndSet(true, false)
        }
    }

    private fun goToGroupInfoEdit(groupId: Long, role: Long) {
        startBcmActivity(accountContext, Intent(this, ChatGroupProfileActivity::class.java).apply {
            putExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, groupId)
            putExtra(ChatGroupProfileActivity.ROLE, role)
        })
    }
}