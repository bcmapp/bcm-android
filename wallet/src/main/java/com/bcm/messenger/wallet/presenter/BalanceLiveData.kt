package com.bcm.messenger.wallet.presenter

import androidx.lifecycle.MutableLiveData
import com.bcm.messenger.wallet.model.WalletDisplay

/**
 * 用于通知余额或汇率变更的监视对象
 * Created by wjh on 2018/6/1
 */
class BalanceLiveData() : MutableLiveData<WalletDisplay>()