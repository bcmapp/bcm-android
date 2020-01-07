package com.bcm.messenger.chats.group.setting

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.components.GroupMemberListCell
import com.bcm.messenger.chats.components.GroupSearchBar
import com.bcm.messenger.chats.components.recyclerview.SelectionDataSource
import com.bcm.messenger.chats.group.ChatGroupCreateActivity
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.chats.group.logic.viewmodel.GroupViewModel
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.CustomDataSearcher
import com.bcm.messenger.common.ui.adapter.AmeRecycleViewAdapter
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.BcmGroupNameUtil
import com.bcm.messenger.common.utils.hideKeyboard
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.messenger.utility.StringAppearanceUtil
import kotlinx.android.synthetic.main.chats_group_member_list.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

/**

 * Created by bcm.social.01 on 2018/5/25.
 */
class ChatGroupMemberListActivity : SwipeBaseActivity(), AmeRecycleViewAdapter.IViewHolderDelegate<AmeGroupMemberInfo> {
    private var editMode = false
    private var memberList: ArrayList<AmeGroupMemberInfo> = ArrayList()
    private lateinit var memberDataSource: SelectionDataSource<AmeGroupMemberInfo>
    private lateinit var groupModel: GroupViewModel

    companion object {
        const val EDIT_MODE = "member_edit_mode"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.chats_group_member_list)

        val groupId = intent.getLongExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, -1)
        val editMode = intent.getBooleanExtra(EDIT_MODE, false)

        EventBus.getDefault().register(this)
        val groupModel = GroupLogic.get(accountContext).getModel(groupId)
        if (null == groupModel) {
            finish()
            return
        }
        this.groupModel = groupModel
        initView()

        changEditingState(editMode)

        if (editMode) {
            showRightMenuByRole(AmeGroupMemberInfo.MEMBER)
        } else {
            showRightMenuByRole(groupModel.myRole())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

    override fun onPause() {
        super.onPause()
        hideKeyboard()
    }

    private fun initView() {
        initTitleBar()
        initMemberList()
    }

    private fun initTitleBar() {

        title_view.setCenterText(groupModel.getGroupInfo()?.displayName ?: "")
        title_view.setListener(listener = object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }

            override fun onClickRight() {
                changEditingState(!this@ChatGroupMemberListActivity.editMode)
            }
        })
    }

    private fun initMemberList() {
        member_list_recycler_view.layoutManager = LinearLayoutManager(this)
        memberDataSource = SelectionDataSource()

        updateMemberList()

        val adapter = AmeRecycleViewAdapter(this, memberDataSource)
        adapter.setViewHolderDelegate(this)

        member_list_recycler_view.adapter = adapter
        member_list_delete_member.setOnClickListener {
            val list = memberDataSource.selectList()
            if (list.isNotEmpty()) {
                val stringId = if (list.size > 1) {
                    R.string.chats_group_remove_users_confirm_content
                } else {
                    R.string.chats_group_remove_user_confirm_content
                }
                AmePopup.center.newBuilder()
                        .withTitle(getString(stringId))
                        .withCancelTitle(getString(R.string.common_cancel))
                        .withWarningTitle(getString(R.string.chats_group_remove_option_text))
                        .withWarningListener {
                            deleteMember(list)
                        }.show(this)
            }
        }

        member_list_delete_count.text = "0"

    }

    private fun deleteMember(list: ArrayList<AmeGroupMemberInfo>) {
        if (!AppUtil.checkNetwork()) {
            return
        }

        member_list_delete_member.isEnabled = false
        AmePopup.loading.show(this)

        groupModel.deleteMember(list) { succeed, error ->
            if (!succeed) {
                if (null != error) {
                    AmePopup.result.failure(this, error)
                }
            } else {
                member_list_delete_count.text = "0"
                memberDataSource.clearSelectList()
            }
            AmePopup.loading.dismiss()
            member_list_delete_member.isEnabled = true
        }
    }

    private fun updateMemberList() {
        val list = groupModel.getGroupMemberListWithRole()
        memberList = list
        if (editMode) {
            list.remove(AmeGroupMemberInfo.MEMBER_ADD_MEMBER)
        }
        memberDataSource.updateDataSource(list)
    }

    private fun changEditingState(editMode: Boolean) {
        this.editMode = editMode
        member_list_delete_count.text = "0"

        if (this.editMode) {
            title_view.setRightText(getString(R.string.chats_done))
            member_list_delete_member.visibility = View.VISIBLE
        } else {
            title_view.setRightText(getString(R.string.chats_user_edit))
            member_list_delete_member.visibility = View.GONE
        }
        memberDataSource.clearSelectList()

        updateMemberList()
    }

    @Subscribe
    fun onEvent(event: GroupViewModel.MyRoleChangedEvent) {
        showRightMenuByRole(event.newRole)
    }

    @Subscribe
    fun onEvent(event: GroupViewModel.MemberListChangedEvent) {
        updateMemberList()
    }

    private fun showRightMenuByRole(role: Long) {
        if (role == AmeGroupMemberInfo.OWNER) {
            title_view.setRightVisible()
        } else {
            title_view.setRightInvisible()
        }
    }

    private fun dataType2ResId(data: AmeGroupMemberInfo): Int {
        return when (data) {
            AmeGroupMemberInfo.MEMBER_SEARCH -> R.layout.chats_group_search_bar
            else -> R.layout.chats_group_member_cell
        }
    }

    override fun getViewHolderType(adapter: AmeRecycleViewAdapter<AmeGroupMemberInfo>, position: Int, data: AmeGroupMemberInfo): Int {
        return dataType2ResId(data)
    }

    override fun bindViewHolder(adapter: AmeRecycleViewAdapter<AmeGroupMemberInfo>, viewHolder: AmeRecycleViewAdapter.ViewHolder<AmeGroupMemberInfo>) {
        when (viewHolder) {
            is SearchHolder -> {
                viewHolder.searchBar.setSourceList(memberList)
            }
            is MemberHolder -> {
                val data = viewHolder.getData()
                if (data != null) {
                    viewHolder.memberView.bind(accountContext, viewHolder.getData(),
                            editMode,
                            memberDataSource.isSelected(data))
                }
            }
        }
    }

    override fun unbindViewHolder(adapter: AmeRecycleViewAdapter<AmeGroupMemberInfo>, viewHolder: AmeRecycleViewAdapter.ViewHolder<AmeGroupMemberInfo>) {
        if (viewHolder is MemberHolder) {
            viewHolder.memberView.unbind()
        }
    }

    override fun createViewHolder(adapter: AmeRecycleViewAdapter<AmeGroupMemberInfo>, inflater: LayoutInflater, parent: ViewGroup, viewType: Int): AmeRecycleViewAdapter.ViewHolder<AmeGroupMemberInfo> {
        return when (viewType) {
            R.layout.chats_group_search_bar -> {
                SearchHolder(accountContext, inflater.inflate(viewType, parent, false) as GroupSearchBar, this)
            }
            else -> MemberHolder(inflater.inflate(viewType, parent, false) as GroupMemberListCell)
        }
    }

    override fun onViewClicked(adapter: AmeRecycleViewAdapter<AmeGroupMemberInfo>, viewHolder: AmeRecycleViewAdapter.ViewHolder<AmeGroupMemberInfo>) {
        hideKeyboard()
        when (viewHolder.getData()) {
            AmeGroupMemberInfo.MEMBER_ADD_MEMBER -> {
                startBcmActivity(accountContext, Intent(this, ChatGroupCreateActivity::class.java).apply {
                    putExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, groupModel.groupId())
                    putExtra(ARouterConstants.PARAM.CONTACTS_SELECT.PARAM_SELECT_TYPE, ChatGroupCreateActivity.TYPE_SELECT_ADD_GROUP_MEMBER)
                })
            }
            else -> {
                val data = viewHolder.getData()
                if (editMode) {
                    if (null != data && data.role != AmeGroupMemberInfo.OWNER) {
                        if (memberDataSource.isSelected(data)) {
                            memberDataSource.unSelect(data)
                        } else {
                            memberDataSource.select(data)
                        }
                        member_list_delete_count.text = "${memberDataSource.selectList().size}"
                    }
                } else {
                    val uid = data?.uid
                    if (uid != null && uid.isNotEmpty()) {
                        AmeModuleCenter.contact(accountContext)?.openContactDataActivity(viewHolder.itemView.context, Address.from(accountContext, data.uid), data.gid)
                    }
                }
            }
        }
    }

    class SearchHolder(val accountContext: AccountContext, val searchBar: GroupSearchBar, val activity: ChatGroupMemberListActivity)
        : AmeRecycleViewAdapter.ViewHolder<AmeGroupMemberInfo>(searchBar) {

        init {
            this.searchBar.setOnSearchActionListener(object : CustomDataSearcher.OnSearchActionListener<AmeGroupMemberInfo>() {
                override fun onSearchResult(filter: String, results: List<AmeGroupMemberInfo>) {
                    activity.memberDataSource.updateDataSource(results)
                }

                override fun onSearchNull(results: List<AmeGroupMemberInfo>) {
                    activity.memberDataSource.updateDataSource(results)
                }

                override fun onMatch(data: AmeGroupMemberInfo, compare: String): Boolean {
                    if (data.uid?.isNotEmpty() == true) {
                        val recipient = Recipient.from(accountContext, data.uid, true)
                        val name = BcmGroupNameUtil.getGroupMemberName(recipient, data)
                        return StringAppearanceUtil.containIgnore(name, compare)
                    }
                    return true
                }

            })

        }

    }

    class MemberHolder(val memberView: GroupMemberListCell) : AmeRecycleViewAdapter.ViewHolder<AmeGroupMemberInfo>(memberView)
}