package com.bcm.messenger.wallet.model

import com.bcm.messenger.wallet.utils.BtcExchangeCalculator
import com.bcm.messenger.wallet.utils.EthExchangeCalculator
import com.bcm.messenger.wallet.utils.WalletSettings
import com.bcm.messenger.utility.logger.ALog
import org.bitcoinj.core.Coin
import java.io.Serializable
import java.math.BigDecimal

/**
 * Created by wjh on 2018/5/28
 */
data class TransactionDisplay(val wallet: BCMWallet, val amount: String, val fromAddress: String?, val toAddress: String?,
                              val confirmation: Int, val date: Long, val txhash: String,
                              val nounce: String?, val block: String?, val fee: String?,
                              val isError: Boolean, val memo: String?) : Serializable {

    init {
        ALog.d("TransactionDisplay", "TransactionDisplay init: amount:$amount, fromAddress:$fromAddress, toAddress:$toAddress, confirmation:$confirmation, txhash:$txhash, fee: $fee, block:$block, memo:$memo")
    }

    fun isPending(): Boolean {
        if (isError) {
            return false
        }
        return when (wallet.coinType) {
            WalletSettings.BTC -> {
                confirmation < 6
            }
            WalletSettings.ETH -> {
                confirmation < 12
            }
            else -> false
        }
    }

    fun isSent(): Boolean {
        return when (wallet.coinType) {
            WalletSettings.BTC -> {
                Coin.valueOf(amount.toLong()).isNegative
            }
            WalletSettings.ETH -> {
                BigDecimal(amount).signum() < 0
            }
            else -> false
        }
    }

    fun getTransactionAmount(): BigDecimal {
        return when (wallet.coinType) {
            WalletSettings.BTC -> {
                BtcExchangeCalculator.convertAmount(amount)
            }
            WalletSettings.ETH -> {
                EthExchangeCalculator.convertAmount(amount)
            }
            else -> BigDecimal.ZERO
        }
    }

    fun getTransactionMoney(): BigDecimal {
        return when (wallet.coinType) {
            WalletSettings.BTC -> {
                BtcExchangeCalculator.convertMoney(amount)
            }
            WalletSettings.ETH -> {
                EthExchangeCalculator.convertMoney(amount)
            }
            else -> BigDecimal.ZERO
        }
    }

    fun displayTransactionAmount(): CharSequence {
        return when (wallet.coinType) {
            WalletSettings.BTC -> {
                BtcExchangeCalculator.convertAmountDisplay(true, amount)
            }
            WalletSettings.ETH -> {
                EthExchangeCalculator.convertAmountDisplay(true, amount)
            }
            else -> BigDecimal.ZERO.toString()
        }
    }

    fun displayTransactionMoney(): CharSequence {
        return when (wallet.coinType) {
            WalletSettings.BTC -> {
                BtcExchangeCalculator.convertMoneyDisplay(true, amount)
            }
            WalletSettings.ETH -> {
                EthExchangeCalculator.convertMoneyDisplay(true, amount)
            }
            else -> BigDecimal.ZERO.toString()
        }
    }

    fun getTransactionFee(): BigDecimal {
        if (fee == null) {
            return BigDecimal.ZERO
        }
        return when (wallet.coinType) {
            WalletSettings.BTC -> {
                BtcExchangeCalculator.convertAmount(fee)
            }
            WalletSettings.ETH -> {
                EthExchangeCalculator.convertAmount(fee)
            }
            else -> BigDecimal.ZERO
        }
    }

    fun getTransactionFeeMoney(): BigDecimal {
        if (fee == null) {
            return BigDecimal.ZERO
        }
        return when (wallet.coinType) {
            WalletSettings.BTC -> {
                BtcExchangeCalculator.convertMoney(fee)
            }
            WalletSettings.ETH -> {
                EthExchangeCalculator.convertMoney(fee)
            }
            else -> BigDecimal.ZERO
        }
    }

    fun displayTransactionFee(): CharSequence {
        if (fee == null) {
            return BigDecimal.ZERO.toString()
        }
        return when (wallet.coinType) {
            WalletSettings.BTC -> {
                BtcExchangeCalculator.convertAmountDisplay(false, fee)
            }
            WalletSettings.ETH -> {
                EthExchangeCalculator.convertAmountDisplay(false, fee)
            }
            else -> BigDecimal.ZERO.toString()
        }
    }

    fun displayTransactionFeeMoney(): CharSequence {
        if (fee == null) {
            return BigDecimal.ZERO.toString()
        }
        return when (wallet.coinType) {
            WalletSettings.BTC -> {
                BtcExchangeCalculator.convertMoneyDisplay(false, fee)
            }
            WalletSettings.ETH -> {
                EthExchangeCalculator.convertMoneyDisplay(false, fee)
            }
            else -> BigDecimal.ZERO.toString()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransactionDisplay

        if (wallet != other.wallet || txhash != other.txhash) return false

        return true
    }

    override fun hashCode(): Int {
        var result = wallet.hashCode()
        result = 31 * result + txhash.hashCode()
        return result
    }
}