package com.bcm.messenger.contacts.search

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.api.ISearchCallback
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.core.corebean.AmeGroupInfo
import com.bcm.messenger.common.event.HomeTopEvent
import com.bcm.messenger.common.finder.BcmFinderManager
import com.bcm.messenger.common.finder.BcmFinderType
import com.bcm.messenger.common.finder.SearchRecordDetail
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.IAmeAppModule
import com.bcm.messenger.common.provider.accountmodule.IGroupModule
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.RecipientAvatarView
import com.bcm.messenger.common.ui.adapter.LinearBaseAdapter
import com.bcm.messenger.contacts.R
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.contacts_fragment_recent_search.*

/**
 * Created by wjh on 2019/4/3
 */
@SuppressLint("ValidFragment")
class RecentSearchFragment() : Fragment() {

    private var mGroupProvider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_GROUP_BASE).navigationWithCast<IGroupModule>()
    private var mChecker = object : BcmFinderManager.SearchRecordChecker {
        override fun isValid(record: SearchRecordDetail): Boolean {
            if (record.tag == null || (record.type != BcmFinderType.ADDRESS_BOOK && record.type != BcmFinderType.GROUP)) {
                return false
            }
            return if (record.type == BcmFinderType.GROUP) {
                val groupInfo = mGroupProvider.getGroupInfo((record.tag as String).toLong())
                groupInfo != null
            }
            else {
                true
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.contacts_fragment_recent_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recent_list.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        val adapter = RecentAdapter(context ?: return)
        recent_list.adapter = adapter

        BcmFinderManager.get().querySearchRecord(mChecker) {
            recent_title_tv?.visibility = if (it.isEmpty()) View.GONE else View.VISIBLE
            adapter.setDataList(it)
        }
    }

    private fun handleSelect(type: BcmFinderType, data: Any?) {
        val a = activity
        when(type) {
            BcmFinderType.ADDRESS_BOOK -> {
                val r = data as Recipient
                if (a is ISearchCallback) {
                    a.onSelect(type, r.address.serialize())
                }
                AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)?.gotoHome(HomeTopEvent(true,
                        HomeTopEvent.ConversationEvent.fromPrivateConversation(r.address, true)))

            }
            BcmFinderType.GROUP -> {
                val g = data as AmeGroupInfo
                if (a is ISearchCallback) {
                    a.onSelect(type, g.gid.toString())
                }
                AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)?.gotoHome(HomeTopEvent(true,
                        HomeTopEvent.ConversationEvent.fromGroupConversation(null, g.gid)))
            }
            else -> {}
        }
    }

    inner class RecentAdapter(context: Context) : LinearBaseAdapter<SearchRecordDetail>(context) {

        private val mInflater = LayoutInflater.from(context)

        override fun onViewRecycled(holder: ViewHolder<SearchRecordDetail>) {
            super.onViewRecycled(holder)
            if (holder is RecentViewHolder) {
                holder.unbind()
            }
        }

        override fun onBindContentHolder(holder: ViewHolder<SearchRecordDetail>, trueData: SearchRecordDetail?) {
            if (holder is RecentViewHolder) {
                holder.bind()
            }
        }

        override fun onCreateContentHolder(parent: ViewGroup): ViewHolder<SearchRecordDetail> {
            return RecentViewHolder(mInflater.inflate(R.layout.contacts_item_recent_search, parent, false))
        }

    }

    inner class RecentViewHolder(itemView: View) : LinearBaseAdapter.ViewHolder<SearchRecordDetail>(itemView) {

        private val photoView = itemView.findViewById<RecipientAvatarView>(R.id.recent_content_photo)
        private val nameView = itemView.findViewById<TextView>(R.id.recent_content_name)
        private var mActualData: Any? = null

        init {
            itemView.setOnClickListener {
                val d = this.data ?: return@setOnClickListener
                handleSelect(d.type, mActualData)
            }
        }

        fun bind() {
            val d = this.data ?: return
            when(d.type) {
                BcmFinderType.ADDRESS_BOOK -> {
                    val r = Recipient.from(AppContextHolder.APP_CONTEXT, Address.fromSerialized(d.tag as String), true)
                    photoView.showPrivateAvatar(r)
                    nameView.text = r.name
                    mActualData = r
                }
                BcmFinderType.GROUP -> {
                    val groupId = (d.tag as String).toLong()
                    val g = mGroupProvider.getGroupInfo(groupId) ?: return
                    photoView.showGroupAvatar(g.gid)
                    nameView.text = g.displayName
                    mActualData = g
                }
                else -> {
                    photoView.visibility = View.GONE
                    nameView.text = ""
                }
            }
        }

        fun unbind() {

        }
    }

}