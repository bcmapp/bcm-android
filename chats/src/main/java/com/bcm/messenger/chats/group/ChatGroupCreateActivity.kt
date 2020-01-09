package com.bcm.messenger.chats.group

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import android.widget.EditText
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountSwipeBaseActivity
import com.bcm.messenger.common.api.IContactsAction
import com.bcm.messenger.common.api.IContactsCallback
import com.bcm.messenger.common.core.corebean.AmeGroupInfo
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.event.HomeTopEvent
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.IAmeAppModule
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.common.utils.setStatusBarLightMode
import com.bcm.netswitchy.configure.AmeConfigure
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.chats_group_select_activity.*
import java.lang.ref.WeakReference
import java.util.*
import kotlin.collections.ArrayList

@Route(routePath = ARouterConstants.Activity.CHAT_GROUP_CREATE)
class ChatGroupCreateActivity : AccountSwipeBaseActivity(), IContactsCallback {

    companion object {
        private const val TAG = "ChatTTCreateActivity"
        const val TYPE_CREATE_GROUP = 1
        const val TYPE_SELECT_ADD_GROUP_MEMBER = 2
    }

    private lateinit var chatSelectFragment: Fragment
    private lateinit var contactsSelectAction: IContactsAction

    private var selectRecipients = HashSet<Recipient>()
    private var multiSelect: Boolean = true
    private var groupId: Long = -1L
    private var type = 1


    override fun onCreate(savedInstanceState: Bundle?) {
        intent.putExtra(ARouterConstants.PARAM.PARAM_ENTER_ANIM, R.anim.common_slide_from_bottom_fast)
        intent.putExtra(ARouterConstants.PARAM.PARAM_EXIT_ANIM, R.anim.common_slide_to_bottom_fast)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chats_group_select_activity)
        initializeResources()

        setSwipeBackEnable(false)
        window?.setStatusBarLightMode()
    }

    override fun onSelect(recipient: Recipient) {
        selectRecipients.add(recipient)

        if (selectRecipients.isNotEmpty()) {
            title_bar?.setRightText(getString(R.string.chats_select_contact_done) + "(" + selectRecipients.size + ")")
            title_bar?.setRightTextColor(getColorCompat(R.color.common_app_primary_color))
            title_bar?.setRightClickable(true)
        } else {
            title_bar?.setRightText(getString(R.string.chats_select_contact_done))
            title_bar?.setRightTextColor(getColorCompat(R.color.common_content_second_color))
            title_bar?.setRightClickable(false)
        }
    }

    override fun onDeselect(recipient: Recipient) {
        selectRecipients.remove(recipient)
        if (selectRecipients.isEmpty()) {
            title_bar?.setRightText(getString(R.string.chats_select_contact_done))
            title_bar?.setRightTextColor(getColorCompat(R.color.common_content_second_color))
            title_bar?.setRightClickable(false)

        } else {
            title_bar?.setRightText(getString(R.string.chats_select_contact_done) + "(" + selectRecipients.size + ")")
            title_bar?.setRightTextColor(getColorCompat(R.color.common_app_primary_color))
            title_bar?.setRightClickable(true)
        }
    }

    private fun initializeResources() {

        groupId = intent.getLongExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, -1)
        type = intent.getIntExtra(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_SELECT_TYPE, 1)

        title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                groupMemberSelectDone()
            }
        })
        title_bar.setRightClickable(false)

        val arg = Bundle()
        val bundle = intent.extras
        arg.putAll(bundle)
        arg.putBoolean(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_MULTI_SELECT, multiSelect)
        if (type == TYPE_CREATE_GROUP) {
            title_bar.setCenterText(getString(R.string.chats_create_group_title))
        } else {
            title_bar.setCenterText(getString(R.string.chats_select_contact_title))
            arg.putLong(ARouterConstants.PARAM.PARAM_GROUP_ID, groupId)
        }
        initFragment(R.id.chats_group_select_container, arg)

    }

    @SuppressLint("CheckResult")
    private fun initFragment(@IdRes container: Int, extras: Bundle?) {
        chatSelectFragment = BcmRouter.getInstance().get(ARouterConstants.Fragment.SELECT_SINGLE).navigationWithCast()
        contactsSelectAction = chatSelectFragment as IContactsAction
        contactsSelectAction.setContactSelectCallback(this)

        contactsSelectAction.addSearchBar(this)
        contactsSelectAction.addEmptyShade(this)

        val args = Bundle()
        if (extras != null) {
            args.putAll(extras)
        }

        val weakSelf = WeakReference(this)
        AmeConfigure.queryGroupSecureV3Enable()
                .observeOn(Schedulers.io())
                .subscribe {
                    val checker = if (type == TYPE_SELECT_ADD_GROUP_MEMBER) {
                        val groupInfo = GroupLogic.get(accountContext).getGroupInfo(groupId)
                        val newGroup = groupInfo?.newGroup ?: true
                        if (newGroup) {
                            ARouterConstants.PARAM.CONTACTS_SELECT.ENABLE_CHECKER.CHECKER_GROUP_V3
                        } else {
                            ARouterConstants.PARAM.CONTACTS_SELECT.ENABLE_CHECKER.CHECKER_DEFAULT
                        }
                    } else {
                        if (it) {
                            ARouterConstants.PARAM.CONTACTS_SELECT.ENABLE_CHECKER.CHECKER_GROUP_V3
                        } else {
                            ARouterConstants.PARAM.CONTACTS_SELECT.ENABLE_CHECKER.CHECKER_DEFAULT
                        }
                    }

                    args.putString(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_ENABLE_CHECKER, checker)

                    if (weakSelf.get()?.isFinishing == false) {
                        val fragment = weakSelf.get()?.chatSelectFragment ?: return@subscribe
                        fragment.arguments = args
                        weakSelf.get()?.supportFragmentManager?.beginTransaction()
                                ?.replace(container, fragment)
                                ?.commitAllowingStateLoss()
                    }

                }
    }

    private fun groupMemberSelectDone() {
        if (!AppUtil.checkNetwork()) {
            return
        }
        if (type == TYPE_CREATE_GROUP) {
            if (selectRecipients.size < 2) {
                AmePopup.result.failure(this, getString(R.string.chats_group_create_lack_description), true)
                return
            }
            AmePopup.loading.show(this)
            val weakThis = WeakReference(this)
            GroupLogic.get(accountContext).createGroup("", "", false, "", selectRecipients.toList()) { groupInfo: AmeGroupInfo?, succeed: Boolean, error: String? ->
                AmePopup.loading.dismiss()
                if (succeed && null != groupInfo && null != groupInfo.gid) {
                    val activity = weakThis.get()
                    if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                        AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)?.gotoHome(accountContext, HomeTopEvent(true,
                                HomeTopEvent.ConversationEvent.fromGroupConversation(groupInfo.gid)))
                    }
                } else {
                    AmePopup.result.failure(this, error
                            ?: resources.getString(R.string.chats_unknown), true)
                }
            }
        } else if (type == TYPE_SELECT_ADD_GROUP_MEMBER) {
            val groupModel = GroupLogic.get(accountContext).getModel(groupId) ?: return
            when {
                groupModel.getGroupInfo().role == AmeGroupMemberInfo.OWNER -> {
                    AmePopup.loading.show(this)
                    title_bar?.setRightInvisible()
                    groupModel.inviteMember(ArrayList(selectRecipients)) { succeed, result ->
                        AmePopup.loading.dismiss()
                        if (succeed) {
                            AmePopup.result.succeed(this@ChatGroupCreateActivity, result) {
                                finish()
                            }
                        } else {
                            AmePopup.result.failure(this@ChatGroupCreateActivity, result)
                        }
                        title_bar?.setRightVisible()
                    }
                }
                groupModel.isNeedOwnerJoinConfirm() -> AmePopup.center.newBuilder().withContent(getString(R.string.chats_group_invite_confirm_title))
                        .withCancelTitle(getString(R.string.common_cancel))
                        .withOkTitle(getString(R.string.chats_group_join_request_action))
                        .withOkListener {
                            AmePopup.loading.show(this)
                            title_bar?.setRightInvisible()
                            groupModel.inviteMember(ArrayList(selectRecipients)) { succeed, resultMessage ->
                                AmePopup.loading.dismiss()
                                if (succeed) {
                                    AmePopup.result.succeed(this@ChatGroupCreateActivity, resultMessage) {
                                        finish()
                                    }
                                } else {
                                    AmePopup.result.failure(this@ChatGroupCreateActivity, resultMessage)
                                }
                                title_bar?.setRightVisible()
                            }
                        }
                        .show(this)
                else -> {
                    AmePopup.loading.show(this)
                    title_bar?.setRightInvisible()
                    groupModel.inviteMember(ArrayList(selectRecipients)) { succeed, resultMessage ->
                        AmePopup.loading.dismiss()
                        if (succeed) {
                            AmePopup.result.succeed(this@ChatGroupCreateActivity, resultMessage) {
                                finish()
                            }
                        } else {
                            AmePopup.result.failure(this@ChatGroupCreateActivity, resultMessage)
                        }
                        title_bar?.setRightVisible()
                    }
                }
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                val v = currentFocus
                if (v is EditText) {
                    AppUtil.hideKeyboard(this, ev, v)
                }
            }
            else -> {
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}
