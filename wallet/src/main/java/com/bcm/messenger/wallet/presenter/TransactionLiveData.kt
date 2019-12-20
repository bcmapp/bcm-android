package com.bcm.messenger.wallet.presenter

import androidx.lifecycle.MutableLiveData
import com.bcm.messenger.wallet.model.TransactionDisplay

/**
 * 用于监视交易记录变更的监视对象
 * Created by wjh on 2018/6/1
 */
class TransactionLiveData : MutableLiveData<TransactionDisplay>()