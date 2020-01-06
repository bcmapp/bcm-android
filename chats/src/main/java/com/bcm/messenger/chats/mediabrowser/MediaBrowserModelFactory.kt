package com.bcm.messenger.chats.mediabrowser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bcm.messenger.common.AccountContext

/**
 * Created by Kin on 2020/1/6
 */
class MediaBrowserModelFactory(private val accountContext: AccountContext) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        when {
            modelClass.isAssignableFrom(PrivateMediaBrowseModel::class.java) -> return PrivateMediaBrowseModel(accountContext) as T
            modelClass.isAssignableFrom(GroupMediaBrowserViewModel::class.java) -> return GroupMediaBrowserViewModel(accountContext) as T
        }
        return super.create(modelClass)
    }
}