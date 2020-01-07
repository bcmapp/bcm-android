package com.bcm.messenger.wallet.presenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bcm.messenger.common.AccountContext

/**
 *
 * Created by wjh on 2020-01-07
 */
class WalletModelFactory(private val accountContext: AccountContext) : ViewModelProvider.NewInstanceFactory() {

    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WalletViewModel::class.java)) {
            return WalletViewModel(accountContext) as T
        }
        return super.create(modelClass)
    }
}