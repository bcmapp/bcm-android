package com.bcm.messenger.adhoc.ui.channel

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.bcm.imcore.im.ChannelUserInfo
import com.bcm.messenger.adhoc.R
import com.bcm.messenger.adhoc.logic.AdHocChannelLogic
import com.bcm.messenger.chats.components.recyclerview.SelectionDataSource
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.SwipeBaseActivity
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.common.ui.IndividualAvatarView
import com.bcm.messenger.common.ui.adapter.AmeRecycleViewAdapter
import com.bcm.messenger.common.ui.emoji.EmojiTextView
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.QuickOpCheck
import kotlinx.android.synthetic.main.adhoc_channel_member_list_activity.*

/**
 *
 */
class AdHocChannelMemberListActivity: SwipeBaseActivity(), AmeRecycleViewAdapter.IViewHolderDelegate<ChannelUserInfo> {
    private val dataSource = object : SelectionDataSource<ChannelUserInfo>() {
        override fun getItemId(position: Int): Long {
            return EncryptUtils.byteArrayToLong(getData(position).uid.toByteArray())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.adhoc_channel_member_list_activity)
        adhoc_member_list_toolbar.setListener(object :CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }
        })

        val sessionId = intent.getStringExtra(ARouterConstants.PARAM.PARAM_ADHOC_SESSION) ?: ""

        adhoc_member_list_list.layoutManager = LinearLayoutManager(this)
        val adapter = AmeRecycleViewAdapter(this,dataSource)
        adapter.setViewHolderDelegate(this)
        adapter.setHasStableIds(true)
        adhoc_member_list_list.adapter = adapter

        dataSource.updateDataSource(AdHocChannelLogic.get(accountContext).getChannelUserList(sessionId))
    }

    override fun createViewHolder(adapter: AmeRecycleViewAdapter<ChannelUserInfo>, inflater: LayoutInflater, parent: ViewGroup, viewType: Int): AmeRecycleViewAdapter.ViewHolder<ChannelUserInfo> {
        val view = inflater.inflate(R.layout.adhoc_channel_member_list_item, parent, false)
        return MemberHolder(accountContext, view)
    }

    override fun onViewClicked(adapter: AmeRecycleViewAdapter<ChannelUserInfo>, viewHolder: AmeRecycleViewAdapter.ViewHolder<ChannelUserInfo>) {
        if (QuickOpCheck.getDefault().isQuick) {
            return
        }
        val holder = viewHolder as MemberHolder
        val address = Address.from(accountContext, holder.getData()?.uid ?: return)
        if (address.isCurrentLogin) {
            return
        }
        AmeModuleCenter.contact(accountContext)?.openContactDataActivity(this, address, holder.getData()?.name)
    }

    inner class MemberHolder(private val accountContext: AccountContext, view: View):AmeRecycleViewAdapter.ViewHolder<ChannelUserInfo>(view) {
        private val avatar = view.findViewById<IndividualAvatarView>(R.id.adhoc_member_item_avatar)
        private val name = view.findViewById<EmojiTextView>(R.id.adhoc_member_item_name)
        override fun setData(data: ChannelUserInfo) {
            super.setData(data)
            avatar.setPhoto(Recipient.from(accountContext, data.uid, true), data.name, IndividualAvatarView.DEFAULT_PHOTO_TYPE)
            name.text = data.name
        }
    }
}