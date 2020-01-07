package com.bcm.messenger.chats.mediabrowser.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.mediabrowser.bean.FileBrowserData
import com.bcm.messenger.chats.mediabrowser.ui.BrowserTitleViewHolder
import com.bcm.messenger.chats.mediabrowser.ui.FileBrowserViewHolder
import com.bcm.messenger.common.AccountContext

/**
 * Created by zjl on 2018/10/16.
 */
class FileBrowserAdapter(context: Context, private val accountContext: AccountContext) : RecyclerView.Adapter<RecyclerView.ViewHolder>()  {

    private var mInflater = LayoutInflater.from(context)
    private var mDataList: MutableList<FileBrowserData> = mutableListOf()

    private var contentSize = 0

    fun getFileBrowserData(position: Int): FileBrowserData {
        return mDataList[position]
    }

    fun getContentSize(): Int {
        return contentSize
    }

    fun addTitle(data: FileBrowserData, notify: Boolean = false) {
        mDataList.add(data)
        if (notify) {
            notifyDataSetChanged()
        }
    }

    fun addContent(list: MutableList<FileBrowserData>, notify: Boolean = false) {
        contentSize += list.size
        mDataList.addAll(list)
        if (notify) {
            notifyDataSetChanged()
        }
    }

    fun clear(notify: Boolean = false) {
        contentSize = 0
        mDataList.clear()
        if (notify) {
            notifyDataSetChanged()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (mDataList[position].title != null) {
            R.layout.chats_browser_title_view
        }else {
            R.layout.chats_file_browser_view
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when(viewType) {
            R.layout.chats_file_browser_view -> {
                FileBrowserViewHolder(accountContext, mInflater.inflate(viewType, parent, false))
            }
            else -> {
                BrowserTitleViewHolder(mInflater.inflate(viewType, parent, false))
            }
        }
    }

    override fun getItemCount(): Int {
        return mDataList.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is FileBrowserViewHolder) {
            holder.bindData(mDataList[position])
        }else if (holder is BrowserTitleViewHolder) {
            holder.bindData(mDataList[position])
        }
    }
}