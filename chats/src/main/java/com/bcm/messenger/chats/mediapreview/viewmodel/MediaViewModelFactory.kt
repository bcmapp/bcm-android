package com.bcm.messenger.chats.mediapreview.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bcm.messenger.common.AccountContext

/**
 * Created by Kin on 2020/1/6
 */
class MediaViewModelFactory(private val accountContext: AccountContext) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        when {
            modelClass.isAssignableFrom(MediaViewPrivateViewModel::class.java) -> return MediaViewPrivateViewModel(accountContext) as T
            modelClass.isAssignableFrom(MediaViewGroupViewModel2::class.java) -> return MediaViewGroupViewModel2(accountContext) as T
            modelClass.isAssignableFrom(MediaViewHistoryViewModel::class.java) -> return MediaViewHistoryViewModel(accountContext) as T
        }
        return super.create(modelClass)
    }
}