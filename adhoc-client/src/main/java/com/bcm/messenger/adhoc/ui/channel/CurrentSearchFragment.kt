package com.bcm.messenger.adhoc.ui.channel

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import com.bcm.messenger.adhoc.R
import com.bcm.messenger.adhoc.component.AdHocSessionAvatar
import com.bcm.messenger.adhoc.logic.AdHocChannelLogic
import com.bcm.messenger.adhoc.logic.AdHocSession
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.BaseFragment
import com.bcm.messenger.common.api.ISearchAction
import com.bcm.messenger.common.api.ISearchCallback
import com.bcm.messenger.common.finder.BcmFinderManager
import com.bcm.messenger.common.finder.BcmFinderType
import com.bcm.messenger.common.finder.SearchItemData
import com.bcm.messenger.common.ui.adapter.LinearBaseAdapter
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.startBcmActivity
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import kotlinx.android.synthetic.main.adhoc_fragment_current_search.*

/**
 * Created by wjh on 2019/4/3
 */
class CurrentSearchFragment : BaseFragment(), ISearchAction, AdHocChannelLogic.IAdHocChannelListener {

    private val TAG = "CurrentSearchFragment"

    private var mKeyword = ""
    private var mSearchLimit = false
    private var mTypes: Array<BcmFinderType> = arrayOf(BcmFinderType.AIR_CHAT)
    private var mAdapter: LinearBaseAdapter<SearchItemData>? = null

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        ALog.i(TAG, "hidden: $hidden")
        if (hidden) {
            mKeyword = ""
            mAdapter?.setDataList(null)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        AdHocChannelLogic.get(accountContext).removeListener(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.adhoc_fragment_current_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        search_shade.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        search_list.layoutManager = LinearLayoutManager(context)

        mAdapter = object : LinearBaseAdapter<SearchItemData>(context) {

            override fun onBindContentHolder(holder: ViewHolder<SearchItemData>, trueData: SearchItemData?) {
                if (holder is ContentViewHolder) {
                    holder.bind()
                }
            }

            override fun onCreateContentHolder(parent: ViewGroup): ViewHolder<SearchItemData> {
                return ContentViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.adhoc_item_search_list, parent, false))
            }

        }
        search_list.adapter = mAdapter

        ALog.d(TAG, "onViewCreate keyword: $mKeyword searchLimit: $mSearchLimit")
        if (mKeyword.isNotEmpty() && mTypes.isNotEmpty()) {
            doSearchAction(mKeyword, mSearchLimit, mTypes)
        }

        AdHocChannelLogic.get(accountContext).addListener(this)
    }

    override fun setKeyword(keyword: String, searchLimit: Boolean) {
        ALog.d(TAG, "setCurrentKeyword keyword: $keyword searchLimit: $searchLimit")
        mKeyword = keyword
        mSearchLimit = searchLimit
        if (isAdded) {
            doSearchAction(mKeyword, mSearchLimit, mTypes)
        } else {
            ALog.d(TAG, "setCurrentKeyword fail, is not added")
        }
    }

    override fun onChannelUserChanged(sessionList: List<String>) {
        AmeDispatcher.mainThread.dispatch {
            mAdapter?.notifyMainChanged()
        }
    }

    private fun doSearchAction(keyword: String, searchLimit: Boolean, types: Array<BcmFinderType>) {
        ALog.d(TAG, "doSearchAction keyword: $keyword")
        val callback: (result: List<SearchItemData>) -> Unit = { resultList ->
            if (keyword == mKeyword) {
                ALog.i(TAG, "doSearchAction result: ${resultList.size}")
                mAdapter?.setDataList(resultList)
                search_list?.postDelayed({
                    hideLoading()
                }, 200)
            }
        }
        showLoading()
        if (searchLimit) {
            BcmFinderManager.get(accountContext).querySearchResultLimit(keyword, types, callback)
        } else {
            BcmFinderManager.get(accountContext).querySearchResult(keyword, types, callback)
        }
    }

    private fun showLoading() {
        ALog.d(TAG, "showLoading")
        try {
            search_shade?.showLoading()
        } catch (ex: Exception) {
            ALog.e(TAG, "showLoading error", ex)
        }
    }

    private fun hideLoading() {
        ALog.d(TAG, "hideLoading")
        try {
            search_shade?.hide()
        } catch (ex: Exception) {
            ALog.e(TAG, "hideLoading ex", ex)
        }
    }

    inner class ContentViewHolder(itemView: View) : LinearBaseAdapter.ViewHolder<SearchItemData>(itemView) {

        private val titleView = itemView.findViewById<TextView>(R.id.search_item_title)
        private val contentLayout = itemView.findViewById<View>(R.id.search_item_content)
        private val contentLogo = itemView.findViewById<AdHocSessionAvatar>(R.id.search_content_photo)
        private val contentName = itemView.findViewById<TextView>(R.id.search_content_name)
        private val contentLine = itemView.findViewById<View>(R.id.search_content_line)
        private val moreLayout = itemView.findViewById<View>(R.id.search_item_more)
        private val moreDescription = itemView.findViewById<TextView>(R.id.search_more_description)
        private val moreFlag = itemView.findViewById<ImageView>(R.id.search_more_flag)

        init {
            contentLayout.setOnClickListener {
                val session = this.data?.tag as? AdHocSession ?: return@setOnClickListener
                val a = activity
                if (a is ISearchCallback) {
                    a.onSelect(data?.type ?: return@setOnClickListener, session.sessionId)
                }
                a?.finish()
                startBcmActivity(Intent(it.context, AdHocConversationActivity::class.java).apply {
                    putExtra(ARouterConstants.PARAM.PARAM_ADHOC_SESSION, session.sessionId)
                })
            }

            moreLayout.setOnClickListener {
                val a = activity
                if (a is ISearchCallback) {
                    a.onMore(data?.type ?: return@setOnClickListener, mKeyword)
                }
            }
        }

        fun bind() {
            val sd = this.data ?: return
            if (sd.isTop) {
                titleView.visibility = View.VISIBLE
                titleView.text = sd.title
            } else {
                titleView.visibility = View.GONE
            }
            if (sd.hasMore != true) {
                moreLayout.visibility = View.GONE
                contentLine.visibility = View.VISIBLE
            } else {
                moreLayout.visibility = View.VISIBLE
                contentLine.visibility = View.GONE
                moreFlag.setImageResource(R.drawable.common_right_arrow_icon)
                moreDescription.text = sd.moreDescription
            }

            when (sd.type) {
                BcmFinderType.AIR_CHAT -> {
                    val session = sd.tag as AdHocSession
                    contentLogo.setSession(accountContext, session)
                    contentName.text = applyContentName(session.displayName(accountContext))
                }
                else -> {
                    ALog.d(TAG, "bind nothing")
                }
            }
        }

        private fun applyContentName(name: CharSequence): CharSequence {
            return StringAppearanceUtil.applyFilterAppearanceIgnoreCase(name, mKeyword, null, AppUtil.getColor(itemView.resources, R.color.common_app_primary_color))
        }
    }
}