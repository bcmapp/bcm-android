package com.bcm.messenger.contacts.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.ConvenientRecyclerView
import com.bcm.messenger.common.ui.adapter.LinearBaseAdapter
import com.bcm.messenger.contacts.R
import kotlinx.android.synthetic.main.contacts_item_selection_horizontal.view.*

/**
 * Created by wjh on 2019/7/12
 */
class RecipientSelectionView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConvenientRecyclerView<Recipient>(context, attrs, defStyleAttr)  {
    interface OnContactsActionListener {
        fun onDeselected(recipient: Recipient)
        fun onSelected(recipient: Recipient)
    }

    private var mListener: OnContactsActionListener? = null
    fun setOnContactsActionListener(listener: OnContactsActionListener) {
        mListener = listener
    }

    override fun showSideBar(): Boolean {
        return false
    }

    override fun hasStableIds(): Boolean {
        return false
    }

    override fun isVertical(): Boolean {
        return false
    }

    override fun getLetter(data: Recipient): String {
        return ""
    }

    override fun onCreateDataHolder(parent: ViewGroup): LinearBaseAdapter.ViewHolder<Recipient> {
        return SelectionViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.contacts_item_selection_horizontal, parent, false))
    }

    override fun onBindViewHolder(holder: LinearBaseAdapter.ViewHolder<Recipient>, trueData: Recipient?) {
        if (holder is SelectionViewHolder) {
            holder.bind()
        }
    }

    override fun onViewRecycled(holder: LinearBaseAdapter.ViewHolder<Recipient>) {
        super.onViewRecycled(holder)
        if (holder is SelectionViewHolder) {
            holder.unbind()
        }
    }

    fun clearSelection() {
        mAdapter.setDataList(null)
    }

    fun addSelection(recipient: Recipient) {
        if (!mAdapter.getTrueDataList().contains(recipient)) {
            mAdapter.addData(recipient)
            mListener?.onSelected(recipient)

            scrollToBottom()
        }
    }

    fun removeSelection(recipient: Recipient) {
        if (mAdapter.removeData(recipient)) {
            mListener?.onDeselected(recipient)

            scrollToBottom()
        }
    }

    override fun getItemId(allPosition: Int): Long {
        return allPosition.toLong()
    }

    private fun scrollToBottom(smooth: Boolean = true) {
        post {
            val position = mAdapter.itemCount - 1
            if (position in 0 until mAdapter.itemCount) {
                if (smooth) {
                    smoothScrollToPosition(position)
                } else {
                    scrollToPosition(position)
                }
            }
        }
    }

    inner class SelectionViewHolder(itemView: View) : LinearBaseAdapter.ViewHolder<Recipient>(itemView), RecipientModifiedListener {

        init {
            itemView.contacts_select?.setOnClickListener {
                removeSelection(data ?: return@setOnClickListener)
            }
        }

        override fun onModified(recipient: Recipient) {
            if (this.data == recipient) {
                bind()
            }
        }

        fun bind() {
            val recipient = this.data ?: return
            recipient.addListener(this)
            itemView.contacts_logo_iv.showPrivateAvatar(recipient)
            itemView.contacts_name_tv.text = recipient.name
        }

        fun unbind() {
            this.data?.removeListener(this)
        }

    }
}