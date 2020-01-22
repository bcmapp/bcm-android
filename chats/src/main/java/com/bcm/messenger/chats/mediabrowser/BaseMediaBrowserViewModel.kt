package com.bcm.messenger.chats.mediabrowser

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.bcm.messenger.common.AccountContext


/**
 * media view modle
 * Created by wjh on 2018/10/15
 */
abstract class BaseMediaBrowserViewModel(protected val accountContext: AccountContext) : ViewModel() {

    companion object {
        const val TYPE_MEDIA = 1
        const val TYPE_FILE = 2
        const val TYPE_LINK = 3

        const val FORMAT_DATE_TITLE = "MMMM yyyy"
    }

    val mediaListLiveData = MutableLiveData<Map<String, MutableList<MediaBrowseData>>>()

    abstract fun loadMedia(browseType: Int, callback: (result: Map<String, MutableList<MediaBrowseData>>) -> Unit)

    abstract fun download(mediaDataList: List<MediaBrowseData>, callback: (success: List<String>, fail: List<MediaBrowseData>) -> Unit)

    abstract fun delete(mediaDataList: List<MediaBrowseData>, callback: (fail: List<MediaBrowseData>) -> Unit)
}
