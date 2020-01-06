package com.bcm.messenger.chats.thread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bcm.messenger.common.AccountContext

/**
 * Created by Kin on 2020/1/6
 */
class ThreadModelFactory(private val accountContext: AccountContext) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ThreadListViewModel::class.java)) {
            return ThreadListViewModel(accountContext) as T
        }
        return super.create(modelClass)
    }
}