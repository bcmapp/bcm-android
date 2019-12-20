package com.bcm.messenger.contacts.search

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.bcm.messenger.common.core.corebean.AmeGroupInfo
import com.bcm.messenger.common.finder.BcmFinderType
import com.bcm.messenger.common.finder.SearchItemData
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.RecipientAvatarView
import com.bcm.messenger.common.ui.adapter.LinearBaseAdapter
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.contacts.R
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.logger.ALog

/**
 * Created by wjh on 2019/4/3
 */
class SearchResultListAdapter(context: Context, val mListener: SearchActionListener) : LinearBaseAdapter<SearchItemData>(context) {

    interface SearchActionListener {
        fun onSelect(data: SearchItemData)
        fun onMore(data: SearchItemData)
        fun onAction(data: SearchItemData)
    }

    private val mInflater = LayoutInflater.from(context)

    private var mSearchKey: String = ""

    init {

    }

    fun setSearchResult(resultList: List<SearchItemData>, searchKey: String) {
        mSearchKey = searchKey
        setDataList(resultList)
    }


    override fun onViewRecycled(holder: ViewHolder<SearchItemData>) {
        super.onViewRecycled(holder)
        if (holder is ContentViewHolder) {
            holder.unbind()
        }
    }

    override fun onBindContentHolder(holder: ViewHolder<SearchItemData>, trueData: SearchItemData?) {
        if (holder is ContentViewHolder) {
            holder.bind()
        }
    }

    override fun onCreateContentHolder(parent: ViewGroup): ViewHolder<SearchItemData> {
        return ContentViewHolder(mInflater.inflate(R.layout.contacts_item_search_list, parent, false))
    }

    inner class ContentViewHolder(itemView: View) : ViewHolder<SearchItemData>(itemView), RecipientModifiedListener {

        private val titleView = itemView.findViewById<TextView>(R.id.search_item_title)
        private val contentLayout = itemView.findViewById<View>(R.id.search_item_content)
        private val contentLogo = itemView.findViewById<RecipientAvatarView>(R.id.search_content_photo)
        private val contentName = itemView.findViewById<TextView>(R.id.search_content_name)
        private val contentLine = itemView.findViewById<View>(R.id.search_content_line)
        private val moreLayout = itemView.findViewById<View>(R.id.search_item_more)
        private val moreDescription = itemView.findViewById<TextView>(R.id.search_more_description)
        private val moreFlag = itemView.findViewById<ImageView>(R.id.search_more_flag)
        private val actionView = itemView.findViewById<ImageView>(R.id.search_add_friend_iv)

        init {
            contentLayout.setOnClickListener {
                mListener.onSelect(data ?: return@setOnClickListener)
            }
            moreLayout.setOnClickListener {
                mListener.onMore(data ?: return@setOnClickListener)
            }
            actionView.setOnClickListener {
                mListener.onAction(data ?: return@setOnClickListener)
            }
        }

        fun bind() {
            unbind()
            val sd = this.data ?: return
            if (sd.isTop) {
                titleView.visibility = View.VISIBLE
                titleView.text = sd.title
            }else {
                titleView.visibility = View.GONE
            }
            if (sd.hasMore != true) {
                moreLayout.visibility = View.GONE
                contentLine.visibility = View.VISIBLE
            }else {
                moreLayout.visibility = View.VISIBLE
                contentLine.visibility = View.GONE
                moreFlag.setImageResource(R.drawable.common_right_arrow_icon)
                moreDescription.text = sd.moreDescription
            }

            when(sd.type) {
                BcmFinderType.ADDRESS_BOOK -> {
                    val r = sd.tag as Recipient
                    r.addListener(this)
                    if (r.isFriend) {
                        actionView.visibility = View.GONE
                    }else {
                        actionView.visibility = View.VISIBLE
                    }
                    contentLogo.showPrivateAvatar(r)
                    contentName.text = applyContentName(r.name)
                }
                BcmFinderType.GROUP -> {
                    actionView.visibility = View.GONE
                    val g = sd.tag as AmeGroupInfo
                    contentLogo.showGroupAvatar(g.gid)
                    contentName.text = applyContentName(g.displayName)
                }
                else -> {
                    ALog.d("SearchResultListAdapter", "bind nothing")
                }
            }
        }

        fun unbind() {
            val recipient = data?.tag as? Recipient ?: return
            recipient.removeListener(this)
        }

        override fun onModified(recipient: Recipient) {
            if (this.data?.tag == recipient) {
                bind()
            }
        }

        private fun applyContentName(name: CharSequence): CharSequence {
            return StringAppearanceUtil.applyFilterAppearanceIgnoreCase(name, mSearchKey, null, AppUtil.getColor(itemView.resources, R.color.common_app_primary_color))
        }
    }
}