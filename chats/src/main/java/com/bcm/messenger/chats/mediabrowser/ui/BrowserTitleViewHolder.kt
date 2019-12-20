package com.bcm.messenger.chats.mediabrowser.ui

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.chats.mediabrowser.bean.FileBrowserData
import kotlinx.android.synthetic.main.chats_browser_title_view.view.*

/**
 * Created by zjl on 2018/10/16.
 */
class BrowserTitleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private var mData: FileBrowserData? = null

    fun bindData(data: FileBrowserData) {
        mData = data
        itemView.browser_title_name.text = data.title
        itemView.browser_file_size.text = data.count.toString()
    }

}