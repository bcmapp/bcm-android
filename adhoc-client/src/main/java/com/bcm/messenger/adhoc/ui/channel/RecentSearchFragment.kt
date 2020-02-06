package com.bcm.messenger.adhoc.ui.channel

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.adhoc.R
import com.bcm.messenger.adhoc.component.AdHocSessionAvatar
import com.bcm.messenger.adhoc.logic.AdHocChannelLogic
import com.bcm.messenger.adhoc.logic.AdHocSession
import com.bcm.messenger.adhoc.logic.AdHocSessionLogic
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.BaseFragment
import com.bcm.messenger.common.api.ISearchCallback
import com.bcm.messenger.common.finder.BcmFinderManager
import com.bcm.messenger.common.finder.BcmFinderType
import com.bcm.messenger.common.finder.SearchRecordDetail
import com.bcm.messenger.common.ui.adapter.LinearBaseAdapter
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import kotlinx.android.synthetic.main.adhoc_fragment_recent_search.*

/**
 * Created by wjh on 2019/4/3
 */
class RecentSearchFragment : BaseFragment(), AdHocChannelLogic.IAdHocChannelListener {

    private var mChecker = object : BcmFinderManager.SearchRecordChecker {
        override fun isValid(record: SearchRecordDetail): Boolean {
            if (record.tag == null || record.type != BcmFinderType.AIR_CHAT) {
                return false
            }
            return if (record.type == BcmFinderType.AIR_CHAT) {
                val session = AdHocSessionLogic.get(accountContext).getSession(record.tag as String)
                session != null
            } else {
                true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        AdHocChannelLogic.get(accountContext).removeListener(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.adhoc_fragment_recent_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recent_list.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        val adapter = RecentAdapter(context ?: return)
        recent_list.adapter = adapter

        BcmFinderManager.get(accountContext).querySearchRecord(mChecker) {
            recent_title_tv?.visibility = if (it.isEmpty()) View.GONE else View.VISIBLE
            ALog.i("RecentSearchFragment", "querySearchRecord list: ${it.size}")
            adapter.setDataList(it)
        }

        AdHocChannelLogic.get(accountContext).addListener(this)
    }

    override fun onChannelUserChanged(sessionList: List<String>) {
        AmeDispatcher.mainThread.dispatch {
            recent_list?.adapter?.notifyDataSetChanged()
        }
    }

    private fun handleSelect(type: BcmFinderType, data: Any?) {
        val a = activity
        when (type) {
            BcmFinderType.AIR_CHAT -> {
                val s = data as AdHocSession
                if (a is ISearchCallback) {
                    a.onSelect(type, s.sessionId)
                }
                a?.finish()
                startActivity(Intent(a, AdHocConversationActivity::class.java).apply {
                    putExtra(ARouterConstants.PARAM.PARAM_ADHOC_SESSION, s.sessionId)
                })
            }
            else -> {
            }
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
            return RecentViewHolder(mInflater.inflate(R.layout.adhoc_item_recent_search, parent, false))
        }

    }

    inner class RecentViewHolder(itemView: View) : LinearBaseAdapter.ViewHolder<SearchRecordDetail>(itemView) {

        private val photoView = itemView.findViewById<AdHocSessionAvatar>(R.id.recent_content_photo)
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
            when (d.type) {
                BcmFinderType.AIR_CHAT -> {
                    val s = AdHocSessionLogic.get(accountContext).getSession(d.tag as String)
                            ?: return
                    photoView.setSession(accountContext, s)
                    nameView.text = s.displayName(accountContext)
                    mActualData = s
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