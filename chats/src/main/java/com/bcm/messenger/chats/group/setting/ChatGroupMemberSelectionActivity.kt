package com.bcm.messenger.chats.group.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.components.GroupMemberListCell
import com.bcm.messenger.chats.components.recyclerview.SelectionDataSource
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.chats.group.logic.viewmodel.GroupViewModel
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.adapter.AmeRecycleViewAdapter
import kotlinx.android.synthetic.main.chats_group_member_list.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

/**
 
 * Created by bcm.social.01 on 2018/5/25.
 */

class ChatGroupMemberSelectionActivity : SwipeBaseActivity(), AmeRecycleViewAdapter.IViewHolderDelegate<AmeGroupMemberInfo> {

    private lateinit var memberDataSource: SelectionDataSource<AmeGroupMemberInfo>
    private lateinit var groupModel: GroupViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.chats_group_member_list)
        val groupId = intent.getLongExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, -1)

        EventBus.getDefault().register(this)
        val groupModel = GroupLogic.getModel(groupId)
        if (null == groupModel) {
            finish()
            return
        }
        this.groupModel = groupModel
        initView()
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
        EventBus.getDefault().removeStickyEvent(ChatGroupChangeOwnerPopWindow.PleaseReturnTheOwnerEvent::class.java)
    }

    private fun initView() {
        initTitleBar()
        initMemberList()
    }

    private fun initTitleBar() {
        title_view.setCenterText(groupModel.getGroupInfo()?.displayName ?: "")
        title_view.setRightInvisible()

        title_view.setListener(listener = object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
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
    }

    private fun updateMemberList() {
        val list = groupModel.getGroupMemberList()
        val listExcludeSelf = ArrayList<AmeGroupMemberInfo>()
        list?.filterTo(listExcludeSelf) { it.uid.serialize() != AMESelfData.uid }
        memberDataSource.updateDataSource(listExcludeSelf)
    }

    @Subscribe
    fun onEvent(event: GroupViewModel.MemberListChangedEvent) {
        updateMemberList()
    }


    override fun getViewHolderType(adapter: AmeRecycleViewAdapter<AmeGroupMemberInfo>, position: Int, data: AmeGroupMemberInfo): Int {
        return R.layout.chats_group_member_cell
    }

    override fun bindViewHolder(adapter: AmeRecycleViewAdapter<AmeGroupMemberInfo>, viewHolder: AmeRecycleViewAdapter.ViewHolder<AmeGroupMemberInfo>) {
        val data = viewHolder.getData()
        if (null != data){
            (viewHolder as MemberHolder).memberView.bind(data, true, memberDataSource.isSelected(data))
        }
    }

    override fun unbindViewHolder(adapter: AmeRecycleViewAdapter<AmeGroupMemberInfo>, viewHolder: AmeRecycleViewAdapter.ViewHolder<AmeGroupMemberInfo>) {
        (viewHolder as MemberHolder).memberView.unbind()
    }

    override fun createViewHolder(adapter: AmeRecycleViewAdapter<AmeGroupMemberInfo>, inflater: LayoutInflater, parent: ViewGroup, viewType: Int): AmeRecycleViewAdapter.ViewHolder<AmeGroupMemberInfo> {
        return MemberHolder(inflater.inflate(viewType, parent, false) as GroupMemberListCell)
    }

    override fun onViewClicked(adapter: AmeRecycleViewAdapter<AmeGroupMemberInfo>, viewHolder: AmeRecycleViewAdapter.ViewHolder<AmeGroupMemberInfo>) {
        val event = EventBus.getDefault().removeStickyEvent(ChatGroupChangeOwnerPopWindow.PleaseReturnTheOwnerEvent::class.java)
        event?.gotIt?.invoke(viewHolder.getData())
        finish()
    }

    class MemberHolder(val memberView: GroupMemberListCell): AmeRecycleViewAdapter.ViewHolder<AmeGroupMemberInfo>(memberView)
}