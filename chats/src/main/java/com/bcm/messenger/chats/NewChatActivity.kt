package com.bcm.messenger.chats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.chats.thread.ThreadListViewModel
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.api.IContactsAction
import com.bcm.messenger.common.api.IContactsCallback
import com.bcm.messenger.common.core.corebean.AmeGroupInfo
import com.bcm.messenger.common.event.HomeTopEvent
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.IAmeAppModule
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.getColorCompat
import com.bcm.messenger.common.utils.setStatusBarLightMode
import com.bcm.messenger.utility.logger.ALog
import com.bcm.netswitchy.configure.AmeConfigure
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.chats_activity_new_chat.*
import java.lang.ref.WeakReference

/**
 * Create a new chat
 */
class NewChatActivity : SwipeBaseActivity() {

    private lateinit var mContactSelection: IContactsAction

    private var mSelectSet: MutableSet<Recipient> = mutableSetOf()

    private var mCreateGroupMode: Boolean = false//创建群模式

    private var mGroupsView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        intent.putExtra(ARouterConstants.PARAM.PARAM_ENTER_ANIM, R.anim.common_slide_from_bottom_fast)
        intent.putExtra(ARouterConstants.PARAM.PARAM_EXIT_ANIM, R.anim.common_slide_to_bottom_fast)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chats_activity_new_chat)
        initView()

        window?.setStatusBarLightMode()
    }

    override fun onBackPressed() {
        if (mCreateGroupMode) {
            mContactSelection.setMultiMode(false)
        }else {
            super.onBackPressed()
        }
    }

    private fun initView() {
        ALog.i("NewChatActivity", "initView")
        new_chat_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                if (mCreateGroupMode) {
                    mContactSelection.setMultiMode(false)
                }else {
                    finish()
                }
            }

            override fun onClickRight() {
                if (mCreateGroupMode) {
                    //创建群
                    if (!AppUtil.checkNetwork()) {
                        return
                    }
                    val targetSet = mSelectSet.filter { !it.isSelf }
                    if (targetSet.size < 2) {
                        AmePopup.result.failure(this@NewChatActivity, getString(R.string.chats_group_create_lack_description), true)
                        return
                    }
                    AmePopup.loading.show(this@NewChatActivity)
                    val weakThis = WeakReference(this@NewChatActivity)
                    GroupLogic.createGroup("", "", false, "", targetSet) { groupInfo: AmeGroupInfo?, succeed: Boolean, error: String? ->
                        AmePopup.loading.dismiss()
                        if (succeed && null != groupInfo && null != groupInfo.gid) {
                            val activity = weakThis.get()
                            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                                val recipient = Recipient.recipientFromNewGroup(activity, groupInfo)
                                AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)?.gotoHome(HomeTopEvent(true,
                                        HomeTopEvent.ConversationEvent.fromGroupConversation(recipient.address, groupInfo.gid)))
                            }
                        } else {
                            AmePopup.result.failure(this@NewChatActivity,error ?: resources.getString(R.string.chats_unknown), true)
                        }
                    }

                }else {
                    mContactSelection.setMultiMode(true)
                }
            }
        })

        mGroupsView = LayoutInflater.from(this).inflate(R.layout.chats_layout_new_chat_group, null)
        mGroupsView?.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        mGroupsView?.setOnClickListener {
            BcmRouter.getInstance().get(ARouterConstants.Activity.GROUP_CONTACT_MAIN).navigation(this)
        }

        val f = BcmRouter.getInstance().get(ARouterConstants.Fragment.SELECT_SINGLE).navigationWithCast<Fragment>()

        mContactSelection = f as IContactsAction
        mContactSelection.addSearchBar(this)
        mGroupsView?.let {
            mContactSelection.addHeader(it)

        }
        mContactSelection.addEmptyShade(this)
        mContactSelection.setContactSelectCallback(selectCallback)

        val weakSelf = WeakReference(this)
        AmeConfigure.queryGroupSecureV3Enable()
                .subscribe {
                    val args = Bundle()
                    args.putBoolean(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_MULTI_SELECT, false)
                    args.putBoolean(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_INCLUDE_ME, true)
                    args.putBoolean(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_CHANGE_MODE, true)
                    val checker = if (it) {
                        ARouterConstants.PARAM.CONTACTS_SELECT.ENABLE_CHECKER.CHECKER_GROUP_V3
                    } else {
                        ARouterConstants.PARAM.CONTACTS_SELECT.ENABLE_CHECKER.CHECKER_DEFAULT
                    }

                    args.putString(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_ENABLE_CHECKER, checker)

                    if (weakSelf.get()?.isFinishing == false) {
                        weakSelf.get()?.initFragment(R.id.new_chat_container, f, args)
                    }
                }

    }

    private val selectCallback = object : IContactsCallback {
        override fun onSelect(recipient: Recipient) {

            if (mCreateGroupMode) {
                mSelectSet.add(recipient)
                new_chat_title_bar.setRightText(getString(R.string.chats_select_contact_done) + "(" + mSelectSet.size + ")")

            }else {
                AmePopup.loading.show(this@NewChatActivity)
                ThreadListViewModel.getExistThreadId(recipient) {
                    AmePopup.loading.dismiss()

                    finish()

                    BcmRouter.getInstance().get(ARouterConstants.Activity.CHAT_CONVERSATION_PATH)
                            .putParcelable(ARouterConstants.PARAM.PARAM_ADDRESS, recipient.address)
                            .putLong(ARouterConstants.PARAM.PARAM_THREAD, it)
                            .navigation()

                }
            }
        }

        override fun onDeselect(recipient: Recipient) {
            if (mCreateGroupMode) {
                mSelectSet.remove(recipient)
                new_chat_title_bar.setRightText(getString(R.string.chats_select_contact_done) + "(" + mSelectSet.size + ")")
            }
        }

        override fun onModeChanged(multiSelect: Boolean) {
            mCreateGroupMode = multiSelect
            mSelectSet.clear()
            if (multiSelect) { //多选表示进入创建群模式
                new_chat_title_bar.setRightText(getString(R.string.chats_select_contact_done) + "(" + mSelectSet.size + ")")
                new_chat_title_bar.setRightTextColor(getColorCompat(R.color.common_app_primary_color))
                new_chat_title_bar.setLeftText(getString(R.string.chats_cancel))
                new_chat_title_bar.setLeftTextColor(getColorCompat(R.color.common_color_black))
                new_chat_title_bar.setLeftTextSize(15.0f)

                try {
                    //自己是不需要再选的
                    mContactSelection.setFixedSelected(listOf(Recipient.major()))
                }catch (ex: Exception) {
                    ex.printStackTrace()
                }

            }else {
                new_chat_title_bar.setRightText(getString(R.string.chats_new_group_chat))
                new_chat_title_bar.setRightTextColor(getColorCompat(R.color.common_color_black))
                new_chat_title_bar.setLeftIcon(R.drawable.common_close_black_m_icon)

            }

            mGroupsView?.let {
                mContactSelection.showHeader(it, !multiSelect)
            }
        }
    }
}
