package com.bcm.messenger.wallet.model

/**
 * Created by wjh on 2018/6/4
 */
data class BCMWalletAccountDisplay(val coinType: String,
                                   val coinList: MutableList<WalletDisplay> = mutableListOf()) {

}