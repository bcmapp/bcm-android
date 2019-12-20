package com.bcm.messenger.chats.mediabrowser.bean

import com.bcm.messenger.chats.mediabrowser.BaseMediaBrowserViewModel
import com.bcm.messenger.chats.mediabrowser.MediaBrowseData

/**
 * Created by zjl on 2018/10/17.
 */

interface DeleteFileCallBack {
    fun delete(list: List<MediaBrowseData>)
    fun multiSelect()
}

data class FileBrowserData(val title: String?, val count: Int, var data: MediaBrowseData?, var mediaBrowser: BaseMediaBrowserViewModel?, var isInSelecting: Boolean = false, val deleteCallback: DeleteFileCallBack? = null)