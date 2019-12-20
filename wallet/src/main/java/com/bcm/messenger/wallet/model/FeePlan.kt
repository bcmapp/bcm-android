package com.bcm.messenger.wallet.model

import com.bcm.messenger.wallet.R
import com.bcm.messenger.wallet.utils.WalletSettings
import com.bcm.messenger.utility.AppContextHolder

/**
 * Created by wjh on 2018/5/31
 */
class FeePlan {

    companion object {

        fun getEthDefault(): List<FeePlan> {
            val context = AppContextHolder.APP_CONTEXT
            return listOf(FeePlan(WalletSettings.ETH, context.getString(R.string.wallet_transfer_fee_high_description), "200.0", WalletSettings.GAS_LIMIT_DEFAULT.toString()),
                    FeePlan(WalletSettings.ETH, context.getString(R.string.wallet_transfer_fee_middle_description), "70.0", WalletSettings.GAS_LIMIT_DEFAULT.toString()),
                    FeePlan(WalletSettings.ETH, context.getString(R.string.wallet_transfer_fee_low_description), "50.0", WalletSettings.GAS_LIMIT_DEFAULT.toString()))
        }

        fun getBtcDefault(): List<FeePlan> {
            val context = AppContextHolder.APP_CONTEXT
            return listOf(FeePlan(WalletSettings.BTC, context.getString(R.string.wallet_transfer_fee_high_description), "100000"),
                    FeePlan(WalletSettings.BTC, context.getString(R.string.wallet_transfer_fee_middle_description), "50000"),
                    FeePlan(WalletSettings.BTC, context.getString(R.string.wallet_transfer_fee_low_description), "5000"))
        }
    }


    val coinBase: String
    val name: String
    var fee: String? = null
        private set
    var gasPrice: String? = null
        private set
    var gasLimit: String? = null
        private set

    constructor(coinBase: String, name: String, fee: String?) {
        this.coinBase = coinBase
        this.name = name;
        this.fee = fee
    }

    constructor(coinBase: String, name: String, gasPrice: String?, gasLimit: String?) {
        this.coinBase = coinBase
        this.name = name
        this.gasLimit = gasLimit
        this.gasPrice = gasPrice
    }

}