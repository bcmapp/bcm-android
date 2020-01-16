package com.bcm.messenger.wallet.model

import android.os.Parcel
import android.os.Parcelable
import com.bcm.messenger.wallet.utils.BCMWalletManagerContainer
import com.bcm.messenger.wallet.utils.BtcExchangeCalculator
import com.bcm.messenger.wallet.utils.EthExchangeCalculator
import com.bcm.messenger.wallet.utils.WalletSettings
import com.orhanobut.logger.Logger
import java.math.BigDecimal

/**
 * Created by wjh on 2018/05/25
 */
class WalletDisplay(baseWallet: BCMWallet, amount: String, type: Byte = NORMAL) : Comparable<WalletDisplay>, Parcelable {

    @Transient
    private var mManager: BCMWalletManagerContainer.BCMWalletManager? = null

    fun setManager(manager: BCMWalletManagerContainer.BCMWalletManager?) {
        mManager = manager
        if (manager != null) {
            baseWallet.setManager(manager)
        }
    }

    fun freshAddress(): String {
        return baseWallet.freshAddress()
    }

    fun standardAddress(): String {
        return baseWallet.getStandardAddress()
    }

    var baseWallet: BCMWallet
        private set

    var amount: String

    var type: Byte
        private set

    init {
        this.baseWallet = baseWallet
        this.amount = amount
        this.type = type
        Logger.d("WalletDisplay init, coinType: ${baseWallet.coinType}, amount:$amount")
    }

    companion object {

        val NORMAL: Byte = 0
        val WATCH_ONLY: Byte = 1
        val CONTACT: Byte = 2

        @JvmField
        val CREATOR: Parcelable.Creator<WalletDisplay> = object : Parcelable.Creator<WalletDisplay> {
            override fun createFromParcel(`in`: Parcel): WalletDisplay {
                return WalletDisplay(`in`)
            }

            override fun newArray(size: Int): Array<WalletDisplay?> {
                return arrayOfNulls(size)
            }
        }

        fun countCoinBalanceDisplay(coinBase: String, walletList: List<WalletDisplay>): CharSequence {
            return when (coinBase) {
                WalletSettings.BTC -> {
                    BtcExchangeCalculator.convertAmountDisplay(coin = *(walletList.map { it.amount }.toTypedArray()))
                }
                WalletSettings.ETH -> {
                    EthExchangeCalculator.convertAmountDisplay(ether = *(walletList.map { it.amount }.toTypedArray()))
                }
                else -> BigDecimal.ZERO.toString()
            }
        }

        fun countMoneyBalanceDisplayWithCurrency(coinBase: String, walletList: List<WalletDisplay>): CharSequence {
            return "≈" + countMoneyBalanceDisplay(coinBase, walletList)
        }

        private fun countMoneyBalanceDisplay(coinBase: String, walletList: List<WalletDisplay>): CharSequence {
            return when (coinBase) {
                WalletSettings.BTC -> {
                    BtcExchangeCalculator.convertMoneyDisplay(coin = *(walletList.map { it.amount }.toTypedArray()))
                }
                WalletSettings.ETH -> {
                    EthExchangeCalculator.convertMoneyDisplay(coin = *(walletList.map { it.amount }.toTypedArray()))
                }
                else -> ""
            }
        }

    }

    fun displayName(): CharSequence {
        val name = baseWallet.name
        return if(name.isEmpty()) {
            mManager?.formatDefaultName(baseWallet.coinType, baseWallet.accountIndex) ?: ""
        }else {
            name
        }
    }

    fun getCoinAmount(): BigDecimal {
        return when (baseWallet.coinType) {
            WalletSettings.BTC -> {
                BtcExchangeCalculator.convertAmount(amount)
            }
            WalletSettings.ETH -> {
                EthExchangeCalculator.convertAmount(amount)
            }
            else -> BigDecimal.ZERO
        }
    }

    fun displayCoinAmount(): CharSequence {
        return when (baseWallet.coinType) {
            WalletSettings.BTC -> {
                BtcExchangeCalculator.convertAmountDisplay(coin = *Array<String>(1, { amount }))
            }
            WalletSettings.ETH -> {
                EthExchangeCalculator.convertAmountDisplay(ether = *Array<String>(1, { amount }))
            }
            else -> BigDecimal.ZERO.toString()
        }
    }

    fun getMoneyAmount(): BigDecimal {
        return when (baseWallet.coinType) {
            WalletSettings.BTC -> BtcExchangeCalculator.convertMoney(amount)
            WalletSettings.ETH -> {
                EthExchangeCalculator.convertMoney(amount)
            }
            else -> BigDecimal.ZERO
        }
    }

    fun displayMoneyAmount(): CharSequence {
        return when (baseWallet.coinType) {
            WalletSettings.BTC -> BtcExchangeCalculator.convertMoneyDisplay(coin = *Array<String>(1, { amount }))
            WalletSettings.ETH -> {
                EthExchangeCalculator.convertMoneyDisplay(coin = *Array<String>(1, { amount }))
            }
            else -> BigDecimal.ZERO.toString()
        }
    }


    internal constructor(`in`: Parcel) : this(`in`.readSerializable() as BCMWallet, `in`.readString() ?: "", `in`.readByte()) {
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeSerializable(baseWallet)
        dest.writeString(amount)
        dest.writeByte(type)
    }

    override fun describeContents(): Int {
        return 0
    }

    override operator fun compareTo(other: WalletDisplay): Int {
        return baseWallet.address.compareTo(other.baseWallet.address)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WalletDisplay

        if (baseWallet != other.baseWallet) return false

        return true
    }

    override fun hashCode(): Int {
        return baseWallet.hashCode()
    }


}
