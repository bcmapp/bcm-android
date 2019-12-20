package com.bcm.messenger.wallet.model

import com.bcm.messenger.utility.proguard.NotGuard
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.wallet.utils.*
import com.google.gson.annotations.SerializedName
import com.bcm.messenger.utility.logger.ALog
import org.bitcoinj.core.LegacyAddress
import org.web3j.utils.Numeric
import java.io.File
import java.io.Serializable

/**
 * Created by wjh on 2018/05/24
 */
data class BCMWallet(@SerializedName("address") val address: String, @SerializedName("path") val path: String, @SerializedName("coinType") val coinType: String,
                     @SerializedName("accountIndex") val accountIndex: Int, @SerializedName("addTime") val addTime: Long) : Serializable, NotGuard {
    fun freshAddress(): String {
        return if (coinType == WalletSettings.BTC) {
            BtcWalletUtils.getCurrentReceiveAddress(this)
        } else {
            EthWalletUtils.getCurrentReceiveAddress(this)
        }
    }

    fun toEmptyDisplayWallet(): WalletDisplay {
        return WalletDisplay(this, "0")
    }

    fun isMine(addressString: String): Boolean {
        return when (coinType) {
            WalletSettings.BTC -> BtcWalletUtils.getCurrentSyncHelper().getSourceWallet(this)?.isAddressMine(LegacyAddress.fromBase58(BtcWalletUtils.NETWORK_PARAMETERS, addressString)) == true
            WalletSettings.ETH -> address == Numeric.cleanHexPrefix(addressString)
            else -> false
        }
    }
    var name: String = ""
    fun getStandardAddress(): String {
        return if (Numeric.containsHexPrefix(address)) {
            address
        } else {
            WalletSettings.PREFIX_ADDRESS + address
        }
    }

    fun getSourceFile(): File {
        return File(path,
                if (AppUtil.useDevBlockChain()) {
                    WalletSettings.createWalletPrefix(address, accountIndex) + "_test.wallet"
                } else {
                    WalletSettings.createWalletPrefix(address, accountIndex) + ".wallet"
                })
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val o = other as BCMWallet
        return address == o.address && coinType == o.coinType
    }

    override fun hashCode(): Int {
        var result = address.hashCode()
        result = 31 * result + coinType.hashCode()
        return result
    }
}
