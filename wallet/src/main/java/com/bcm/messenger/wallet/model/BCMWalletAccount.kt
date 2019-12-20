package com.bcm.messenger.wallet.model

import com.bcm.messenger.utility.proguard.NotGuard
import com.google.gson.annotations.SerializedName

/**
 * Created by wjh on 2018/6/4
 */
data class BCMWalletAccount(@SerializedName("coinType") val coinType: String,
                            @SerializedName("coinList") val coinList: MutableList<BCMWallet> = mutableListOf()) : NotGuard {
    fun toTypeDisplay(): BCMWalletAccountDisplay {
        return BCMWalletAccountDisplay(coinType, coinList.map { it.toEmptyDisplayWallet() }.toMutableList())
    }


}