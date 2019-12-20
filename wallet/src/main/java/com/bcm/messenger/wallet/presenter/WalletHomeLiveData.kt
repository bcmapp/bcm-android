package com.bcm.messenger.wallet.presenter

import androidx.lifecycle.MutableLiveData
import com.bcm.messenger.utility.logger.ALog

/**
 * 用于通知钱包首页是否存在已激活钱包列表的监视对象
 * Created by wjh on 2018/6/1
 */
class WalletHomeLiveData : MutableLiveData<Boolean>() {

    init {
        ALog.d("WalletHomeLiveData", "init")
    }

    override fun onActive() {
        ALog.d("WalletHomeLiveData", "onActive")
        value = true
    }

    override fun onInactive() {
        ALog.d("WalletHomeLiveData", "onInactive")
        value = false
    }
}