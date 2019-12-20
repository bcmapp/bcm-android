package com.bcm.messenger.wallet.presenter

import androidx.lifecycle.LiveData
import com.bcm.messenger.wallet.model.BCMWallet

/**
 * 用于通知钱包名称变更的监视对象
 * Created by wjh on 2018/6/1
 */
class WalletNameLiveData() : LiveData<BCMWallet>() {

    /**
     * 通知名字变更
     */
    fun changed(wallet: BCMWallet) {
        postValue(wallet)
    }
}