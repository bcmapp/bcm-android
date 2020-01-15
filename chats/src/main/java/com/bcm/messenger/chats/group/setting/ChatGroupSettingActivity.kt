package com.bcm.messenger.chats.group.setting

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.NestedScrollView
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.components.GroupMemberView
import com.bcm.messenger.chats.components.recyclerview.WrapContentGridLayoutManager
import com.bcm.messenger.chats.group.ChatGroupCreateActivity
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.chats.group.logic.viewmodel.GroupViewModel
import com.bcm.messenger.chats.mediabrowser.ui.MediaBrowserActivity
import com.bcm.messenger.chats.thread.ThreadListViewModel
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.core.corebean.BcmReviewGroupJoinRequest
import com.bcm.messenger.common.event.GroupNameOrAvatarChanged
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.adapter.AmeRecycleViewAdapter
import com.bcm.messenger.common.ui.adapter.ListDataSource
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.setDrawableRight
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.chats_group_setting_activity.*
import org.greenrobot.eventbus.Subscribe
import java.lang.ref.WeakReference

/**
 * Created by bcm.social.01 on 2018/6/1.
 */
class ChatGroupSettingActivity : AccountSwipeBaseActivity(), AmeRecycleViewAdapter.IViewHolderDelegate<AmeGroupMemberInfo> {
    companion object {
        private const val TAG = "ChatGroupSetting"
    }

    private lateinit var mGroupModel: GroupViewModel
    private lateinit var mDataSource: ListDataSource<AmeGroupMemberInfo>

    private var lightMode = false
    private var isBgLight = true

    override fun onCreate(savedInstanceState: Bundle?) {
        ALog.i(TAG, "onCreate")
        disableStatusBarLightMode()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.chats_group_setting_activity)
        val groupId = intent.getLongExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, -1)
        val model = GroupLogic.get(accountContext).getModel(groupId)

        if (model == null) {
            finish()
            return
        }

        this.mGroupModel = model

        mGroupModel.setThreadId(intent.getLongExtra(ARouterConstants.PARAM.PARAM_THREAD, -1))
        initView()
    }

    private fun initView() {

        initNavigationBar()
        initMemberViews()
        updateGroupInfoViews()
        initStickOnTopView()

        clear_history_item.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            AmePopup.bottom.newBuilder()
                    .withTitle(getString(R.string.chats_user_clear_history_title, mGroupModel.groupName()))
                    .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.chats_clear), AmeBottomPopup.PopupItem.CLR_RED) {

                        ThreadListViewModel.getCurrentThreadModel()?.deleteGroupConversation(mGroupModel.groupId(), mGroupModel.threadId()) {
                            if (it) {
                                RxBus.post(ChatGroupContentClear(mGroupModel.groupId()))
                                ToastUtil.show(this@ChatGroupSettingActivity, getString(R.string.chats_clean_succeed))
                            }
                        }
                    })
                    .withDoneTitle(getString(R.string.chats_cancel))
                    .show(this)
        }


        leave_group_item.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }

            checkBeforeLeaveGroup()
        }

        chats_group_media_browser.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            MediaBrowserActivity.router(accountContext, GroupUtil.addressFromGid(accountContext, mGroupModel.groupId()))
        }

        chats_group_notice_item.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            goToNotice(mGroupModel.groupId())
        }

        chats_group_notice_item.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            goToNotice(mGroupModel.groupId())
        }

        chats_group_notification_switch.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            AmePopup.loading.show(this)
            val enable = !mGroupModel.isMute()
            mGroupModel.mute(enable) { succeed, error ->
                AmePopup.loading.dismiss()
                if (succeed) {
                    chats_group_notification_switch.setSwitchStatus(!enable)
                    AmePopup.result.succeed(this, getString(R.string.chats_group_setting_notification_success))
                } else {
                    AmePopup.result.failure(this, error
                            ?: getString(R.string.chats_group_setting_notification_fail))
                }
            }
        }

        invite_join_item.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            startBcmActivity(accountContext, Intent(this, GroupShareSettingsActivity::class.java).apply {
                putExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, mGroupModel.groupId())
            })
        }

        joining_request_item.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            BcmRouter.getInstance().get(ARouterConstants.Activity.GROUP_JOIN_CHECK).putLong(ARouterConstants.PARAM.PARAM_GROUP_ID, mGroupModel.groupId()).startBcmActivity(accountContext, this)
        }

        approval_joining_item.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            fun doSetOwnerJoinConfirm(switch: Boolean) {
                mGroupModel.setNeedOwnerJoinConfirm(switch) { succeed, error ->
                    ALog.d(TAG, "setNeedOwnerJoinConfirm success: $succeed, error: $error")
                    AmeAppLifecycle.hideLoading()
                    if (succeed) {
                        approval_joining_item.setSwitchStatus(switch)
                        updateJoiningRequestsView()
                        AmeAppLifecycle.succeed(getString(R.string.chats_group_setting_join_need_review_success), true)
                    } else {
                        AmeAppLifecycle.failure(getString(R.string.chats_group_setting_join_need_review_fail), true)
                    }
                }
            }

            val switch = !approval_joining_item.getSwitchStatus()
            if (switch) {
                AmeAppLifecycle.showLoading()
                doSetOwnerJoinConfirm(switch)
            } else {
                val joiningRequest = mGroupModel.getJoinRequestList()
                if (joiningRequest.isEmpty()) {
                    AmeAppLifecycle.showLoading()
                    doSetOwnerJoinConfirm(switch)
                } else {
                    val oneText = StringAppearanceUtil.applyAppearance(getString(R.string.chats_group_share_noapproval_pending_request_confirm_approve), color = getColorCompat(R.color.common_content_warning_color))
                    val twoText = StringAppearanceUtil.applyAppearance(getString(R.string.chats_group_share_noapproval_pending_request_confirm_reject), color = getColorCompat(R.color.common_content_warning_color))
                    var thirdText = StringAppearanceUtil.applyAppearance(getString(R.string.common_later), color = getColorCompat(R.color.common_color_black))
                    thirdText = StringAppearanceUtil.applyAppearance(this, thirdText, true)
                    AlertDialog.Builder(this).setTitle(getString(R.string.chats_group_share_noapproval_pending_request_confirm_title))
                            .setItems(arrayOf(oneText, twoText, thirdText)) { dialog, which ->
                                when (which) {
                                    0 -> {
                                        AmeAppLifecycle.showLoading()
                                        mGroupModel.reviewJoinRequests(joiningRequest.map {
                                            BcmReviewGroupJoinRequest(it.uid, it.reqId, true)
                                        }) { succeed, error ->
                                            ALog.d(TAG, "setNotApproval switch: $switch, review success: $succeed, error: $error")
                                            if (succeed) {
                                                doSetOwnerJoinConfirm(switch)
                                            } else {
                                                AmeAppLifecycle.hideLoading()
                                                AmeAppLifecycle.failure(getString(R.string.chats_group_setting_join_need_review_fail), true)
                                            }
                                        }
                                    }
                                    1 -> {
                                        AmeAppLifecycle.showLoading()
                                        mGroupModel.reviewJoinRequests(joiningRequest.map {
                                            BcmReviewGroupJoinRequest(it.uid, it.reqId, false)
                                        }) { succeed, error ->
                                            ALog.d(TAG, "setNotApproval switch: $switch, review success: $succeed, error: $error")
                                            if (succeed) {
                                                doSetOwnerJoinConfirm(switch)
                                            } else {
                                                AmeAppLifecycle.hideLoading()
                                                AmeAppLifecycle.failure(getString(R.string.chats_group_setting_join_need_review_fail), true)
                                            }
                                        }
                                    }
                                    else -> dialog.cancel()
                                }
                            }.create().show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mGroupModel.refreshGroupAvatar()
    }

    private fun initNavigationBar() {
        window.setTransparencyBar(false)

        chats_group_setting_title.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                onBackPressed()
            }
        })

        val scrollHeight = 160.dp2Px()
        chats_group_setting_title.setTitleBarAlpha(0f)
        chats_group_scroll_view.setOnScrollChangeListener { _: NestedScrollView?, _, scrollY, _, _ ->
            val alpha = if (scrollY >= scrollHeight) 1f else scrollY / scrollHeight.toFloat()
            chats_group_setting_title.setTitleBarAlpha(alpha)

            if (alpha >= 0.5f && !lightMode) {
                lightMode = true
                window.setStatusBarLightMode()
                chats_group_setting_title.setLeftIcon(R.drawable.common_back_arrow_black_icon)
            } else if (alpha < 0.5f && lightMode) {
                lightMode = false
                if (isBgLight) {
                    window.setStatusBarLightMode()
                } else {
                    window.setStatusBarDarkMode()
                    chats_group_setting_title.setLeftIcon(R.drawable.common_back_arrow_white_icon)
                }
            }
        }
    }


    private fun initMemberViews() {
        group_member_recycler_view.layoutManager = WrapContentGridLayoutManager(this, 5)

        mDataSource = object : ListDataSource<AmeGroupMemberInfo>() {

            override fun updateDataSource(datalist: List<AmeGroupMemberInfo>) {
                ALog.i(TAG, "updateDataSource: ${datalist.size}")
                val originCount = this.datalist.size
                this.datalist = datalist
                itemUpdateCallback.invoke(0, originCount)
            }
        }

        val adapter = AmeRecycleViewAdapter(this, mDataSource)
        adapter.setViewHolderDelegate(this)
        group_member_recycler_view.adapter = adapter

        chats_group_members_item.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            goToMemberList(mGroupModel.groupId())
        }

        mDataSource.updateDataSource(mGroupModel.getGroupControlMemberList())
    }

    private fun updateGroupInfoViews() {
        group_control_name?.text = mGroupModel.groupName()
        if (lightMode) {
            group_control_name.setTextColor(getColorCompat(R.color.common_color_black))
            group_control_name.setDrawableRight(R.drawable.common_right_arrow_black_icon)
        } else {
            group_control_name.setTextColor(getColorCompat(R.color.common_color_white))
            group_control_name.setDrawableRight(R.drawable.common_right_arrow_white_icon)
        }
        chats_group_setting_title.setCenterText(mGroupModel.groupName() ?: "")
        chats_group_control_avatar.setLoadCallback { bitmap ->
            if (bitmap != null)
                chats_group_header_layout.setGradientBackground(bitmap) {
                    isBgLight = it
                    if (it) {
                        window.setStatusBarLightMode()
                        group_control_name.setTextColor(getColorCompat(R.color.common_color_black))
                        group_control_name.setDrawableRight(R.drawable.common_right_arrow_black_icon)
                    } else if (!lightMode) {
                        chats_group_setting_title.setLeftIcon(R.drawable.common_back_arrow_white_icon)
                        group_control_name.setTextColor(getColorCompat(R.color.common_color_white))
                        group_control_name.setDrawableRight(R.drawable.common_right_arrow_white_icon)
                    }
                }
        }
        chats_group_control_avatar.showGroupAvatar(accountContext, mGroupModel.groupId())

        chats_group_member_count.text = mGroupModel.memberCount().toString()

        group_control_name.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            goToGroupInfoEdit(mGroupModel.groupId(), mGroupModel.myRole())
        }
        chats_group_control_avatar.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            goToGroupInfoEdit(mGroupModel.groupId(), mGroupModel.myRole())
        }

        if (mGroupModel.myRole() == AmeGroupMemberInfo.OWNER) {
            invite_join_item.visibility = View.VISIBLE
        } else {
            invite_join_item.visibility = View.GONE
        }

        leave_group_item.setName(resources.getString(R.string.chats_group_setting_leave_head))

        leave_group_item.visibility = when (mGroupModel.myRole()) {
            AmeGroupMemberInfo.OWNER -> View.VISIBLE
            AmeGroupMemberInfo.ADMIN -> View.VISIBLE
            AmeGroupMemberInfo.MEMBER -> View.VISIBLE
            else -> View.GONE
        }

        updateNotificationSwitchStatus()

        updateApprovalJoiningView()
        updateJoiningRequestsView()
    }


    private fun updateNotificationSwitchStatus() {
        chats_group_notification_switch.setSwitchStatus(mGroupModel.isMute())
        chats_group_notification_switch.setSwitchEnable(false)
    }

    private fun initStickOnTopView() {
        stick_on_top_switch.setSwitchEnable(false)
        stick_on_top_switch.setOnClickListener {
            val switch = !stick_on_top_switch.getSwitchStatus()
            stick_on_top_switch.setSwitchStatus(switch)
            ThreadListViewModel.getCurrentThreadModel()?.setPin(mGroupModel.threadId(), switch) {}
        }
        stick_on_top_switch?.setSwitchStatus(false)
        val weakThis = WeakReference(this)
        ThreadListViewModel.getCurrentThreadModel()?.checkPin(mGroupModel.threadId()) {
            weakThis.get()?.stick_on_top_switch?.setSwitchStatus(it)
        }
    }


    private fun updateJoiningRequestsView() {
        if (mGroupModel.myRole() == AmeGroupMemberInfo.OWNER) {
            if (mGroupModel.isNeedOwnerJoinConfirm()) {
                joining_request_item.visibility = View.VISIBLE
                val count = mGroupModel.getJoinRequestCount()
                val unread = mGroupModel.getJoinRequestUnreadCount()
                if (count > 0) {
                    val tipIcon = if (unread > 0) {
                        R.drawable.common_red_dot
                    } else {
                        0
                    }
                    val tipContent = if (count > 0) {
                        count.toString()
                    } else {
                        ""
                    }
                    joining_request_item.setTip(tipContent, tipIcon, getColorCompat(R.color.common_content_second_color))

                } else {
                    joining_request_item.setTip("", 0, getColorCompat(R.color.common_content_second_color))
                }
            } else {
                joining_request_item.visibility = View.GONE
            }
        } else {
            joining_request_item.visibility = View.GONE
        }
    }

    private fun updateApprovalJoiningView() {
        if (mGroupModel.myRole() == AmeGroupMemberInfo.OWNER) {
            approval_joining_item.visibility = View.VISIBLE

            approval_joining_item.setSwitchEnable(false)
            approval_joining_item.setSwitchStatus(mGroupModel.isNeedOwnerJoinConfirm())
        } else {
            approval_joining_item.visibility = View.GONE
        }
    }

    private fun checkBeforeLeaveGroup() {
        val joiningRequest = mGroupModel.getJoinRequestList()
        if (joiningRequest.isEmpty()) {
            leaveGroup()
        } else {
            val oneText = StringAppearanceUtil.applyAppearance(getString(R.string.chats_group_share_leave_pending_request_confirm_approve), color = getColorCompat(R.color.common_content_warning_color))
            val twoText = StringAppearanceUtil.applyAppearance(getString(R.string.chats_group_share_leave_pending_request_confirm_reject), color = getColorCompat(R.color.common_content_warning_color))
            var thirdText = StringAppearanceUtil.applyAppearance(getString(R.string.common_later), color = getColorCompat(R.color.common_color_black))
            thirdText = StringAppearanceUtil.applyAppearance(this, thirdText, true)
            AlertDialog.Builder(this).setTitle(getString(R.string.chats_group_share_leave_pending_request_confirm_title))
                    .setItems(arrayOf(oneText, twoText, thirdText)) { dialog, which ->
                        when (which) {
                            0 -> {
                                AmeAppLifecycle.showLoading()
                                mGroupModel.reviewJoinRequests(joiningRequest.map {
                                    BcmReviewGroupJoinRequest(it.uid, it.reqId, true)
                                }) { succeed, error ->
                                    ALog.d(TAG, "checkBeforeLeaveGroup review success: $succeed, error: $error")
                                    AmeAppLifecycle.hideLoading()
                                    if (succeed) {
                                        AmeAppLifecycle.succeed(getString(R.string.chats_group_join_approve_success), true)
                                    } else {
                                        AmeAppLifecycle.failure(getString(R.string.chats_group_join_approve_fail), true)
                                    }
                                }
                            }
                            1 -> {
                                AmeAppLifecycle.showLoading()
                                mGroupModel.reviewJoinRequests(joiningRequest.map {
                                    BcmReviewGroupJoinRequest(it.uid, it.reqId, false)
                                }) { succeed, error ->
                                    ALog.d(TAG, "checkBeforeLeaveGroup review success: $succeed, error: $error")
                                    AmeAppLifecycle.hideLoading()
                                    if (succeed) {
                                        AmeAppLifecycle.succeed(getString(R.string.chats_group_join_reject_success), true)

                                    } else {
                                        AmeAppLifecycle.failure(getString(R.string.chats_group_join_reject_fail), true)
                                    }
                                }
                            }
                            else -> dialog.cancel()
                        }
                    }.create().show()
        }
    }

    private fun leaveGroup() {
        val result: (succeed: Boolean, error: String) -> Unit = { succeed, error ->
            AmePopup.center.dismiss()
            AmePopup.loading.dismiss()
            if (succeed) {
                leaveSucceedAndFinish(getString(R.string.chats_group_setting_leave_succeed))
            } else {
                AmePopup.result.failure(this@ChatGroupSettingActivity, error)
            }
        }
        if (mGroupModel.myRole() == AmeGroupMemberInfo.OWNER) {
            val newOwner: AmeGroupMemberInfo? = mGroupModel.randomGetGroupMember()
            if (null != newOwner) {
                ChatGroupChangeOwnerPopWindow.show(accountContext, this@ChatGroupSettingActivity, mGroupModel.groupId(), newOwner) {
                    AmePopup.loading.show(this@ChatGroupSettingActivity)
                    mGroupModel.leaveGroup(it.uid, result)
                }
                return
            }
        }
        AmePopup.bottom.newBuilder()
                .withTitle(getString(R.string.chats_group_setting_leave_content))
                .withPopItem(AmeBottomPopup.PopupItem(getString(R.string.chats_group_setting_leave), AmeBottomPopup.PopupItem.CLR_RED) {
                    AmePopup.loading.show(this@ChatGroupSettingActivity)
                    mGroupModel.leaveGroup(null, result)
                })
                .withDoneTitle(getString(R.string.chats_call_cancel_text))
                .show(this)
    }


    private fun leaveSucceedAndFinish(reason: String) {
        AmePopup.result.succeed(this@ChatGroupSettingActivity, reason) {
            setResult(Activity.RESULT_OK)
            this@ChatGroupSettingActivity.finish()
        }
    }

    private fun goToMemberList(groupId: Long, editMode: Boolean = false) {
        startBcmActivity(accountContext, Intent(this, ChatGroupMemberListActivity::class.java).apply {
            putExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, groupId)
            putExtra(ChatGroupMemberListActivity.EDIT_MODE, editMode)
        })
    }

    private fun goToGroupInfoEdit(groupId: Long, role: Long) {
        startBcmActivity(accountContext, Intent(this, ChatGroupProfileActivity::class.java).apply {
            putExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, groupId)
            putExtra(ChatGroupProfileActivity.ROLE, role)
        })
    }

    private fun goToNotice(groupId: Long) {
        startBcmActivity(accountContext, Intent(this, ChatGroupNoticeActivity::class.java).apply {
            putExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, groupId)
        })
    }


    @Subscribe
    fun onEvent(event: GroupViewModel.GroupInfoChangedEvent) {
        updateGroupInfoViews()
        mDataSource.updateDataSource(mGroupModel.getGroupControlMemberList())
    }


    @Subscribe
    fun onEvent(event: GroupViewModel.GroupMuteEnableEvent) {
        updateNotificationSwitchStatus()
    }

    @Subscribe
    fun onEvent(event: GroupViewModel.MemberListChangedEvent) {
        chats_group_member_count.text = mGroupModel.memberCount().toString()
        mDataSource.updateDataSource(mGroupModel.getGroupControlMemberList())
    }

    @Subscribe
    fun onEvent(event: GroupViewModel.MyRoleChangedEvent) {
        updateGroupInfoViews()
    }

    @Subscribe
    fun onEvent(event: GroupViewModel.JoinRequestListChanged) {
        if (mGroupModel.groupId() == event.groupId) {
            updateJoiningRequestsView()
        }
    }

    @Subscribe
    fun onEvent(event: GroupNameOrAvatarChanged) {
        if (event.gid == mGroupModel.groupId()) {
            if (event.name.isNotBlank()) {
                group_control_name?.text = event.name
                chats_group_setting_title.setCenterText(event.name)
            }
            if (event.avatarPath.isNotBlank()) {
                chats_group_control_avatar.showGroupAvatar(accountContext, mGroupModel.groupId(), path = event.avatarPath)
            }
        }
    }

    override fun getViewHolderType(adapter: AmeRecycleViewAdapter<AmeGroupMemberInfo>, position: Int, data: AmeGroupMemberInfo): Int {
        return when (data) {
            AmeGroupMemberInfo.MEMBER_ADD_MEMBER, AmeGroupMemberInfo.MEMBER_REMOVE -> R.layout.chats_group_controll_option_view
            else -> R.layout.chats_group_member_view
        }
    }

    override fun bindViewHolder(adapter: AmeRecycleViewAdapter<AmeGroupMemberInfo>, viewHolder: AmeRecycleViewAdapter.ViewHolder<AmeGroupMemberInfo>) {
        when (viewHolder.getData()) {
            AmeGroupMemberInfo.MEMBER_ADD_MEMBER -> {
                (viewHolder as OptionHolder).imageView.setImageResource(R.drawable.chats_group_member_invite_icon)
            }
            AmeGroupMemberInfo.MEMBER_REMOVE -> {
                (viewHolder as OptionHolder).imageView.setImageResource(R.drawable.chats_group_member_remove_icon)
            }
            else -> {
                val data = viewHolder.getData()
                if (null != data) {
                    (viewHolder as? MemberHolder)?.memberView?.bind(accountContext, data)
                }
            }
        }
    }

    override fun unbindViewHolder(adapter: AmeRecycleViewAdapter<AmeGroupMemberInfo>, viewHolder: AmeRecycleViewAdapter.ViewHolder<AmeGroupMemberInfo>) {
        when (viewHolder) {
            is OptionHolder -> {
                viewHolder.imageView.setImageDrawable(null)
            }
            is MemberHolder -> {
                viewHolder.memberView.unbind()
            }
        }
    }

    override fun createViewHolder(adapter: AmeRecycleViewAdapter<AmeGroupMemberInfo>, inflater: LayoutInflater, parent: ViewGroup, viewType: Int): AmeRecycleViewAdapter.ViewHolder<AmeGroupMemberInfo> {
        return when (viewType) {
            R.layout.chats_group_controll_option_view -> OptionHolder(inflater.inflate(viewType, parent, false))
            else -> MemberHolder(inflater.inflate(viewType, parent, false) as GroupMemberView)
        }
    }


    override fun onViewClicked(adapter: AmeRecycleViewAdapter<AmeGroupMemberInfo>, viewHolder: AmeRecycleViewAdapter.ViewHolder<AmeGroupMemberInfo>) {
        when (viewHolder.getData()) {
            AmeGroupMemberInfo.MEMBER_ADD_MEMBER -> {
                startBcmActivity(accountContext, Intent(this, ChatGroupCreateActivity::class.java).apply {
                    putExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, mGroupModel.groupId())
                    putExtra(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_SELECT_TYPE, ChatGroupCreateActivity.TYPE_SELECT_ADD_GROUP_MEMBER)
                })
            }
            AmeGroupMemberInfo.MEMBER_REMOVE -> {
                goToMemberList(mGroupModel.groupId(), true)
            }
            else -> {
                val data = viewHolder.getData()
                if (data != null && data.uid != null) {
                    AmeModuleCenter.contact(accountContext)?.openContactDataActivity(this, Address.from(accountContext, data.uid), data.gid)
                }
            }
        }
    }

    class OptionHolder(view: View) : AmeRecycleViewAdapter.ViewHolder<AmeGroupMemberInfo>(view) {
        val imageView = view.findViewById<ImageView>(R.id.group_control_option)
    }

    class MemberHolder(val memberView: GroupMemberView) : AmeRecycleViewAdapter.ViewHolder<AmeGroupMemberInfo>(memberView)
}