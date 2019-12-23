package com.bcm.messenger.wallet.model

import com.bcm.messenger.utility.proguard.NotGuard
import com.bcm.messenger.wallet.utils.WalletSettings
import java.io.Serializable

/**
 * Created by wjh on 2019/3/26
 */
class BCMWalletAccounts : Serializable, NotGuard {

    val BTC = BCMWalletAccount(WalletSettings.BTC)

    val ETH = BCMWalletAccount(WalletSettings.ETH)

    fun getAccount(coinType: String): BCMWalletAccount? {
        return when (coinType) {
            WalletSettings.BTC -> BTC
            WalletSettings.ETH -> ETH
            else -> null
        }
    }

    fun clear() {
        BTC.coinList.clear()
        ETH.coinList.clear()
    }

    fun findBCMWallet(address: String): BCMWallet? {
        var target = BTC.coinList.find { it.address == address }
        if (target == null) {
            target = ETH.coinList.find { it.address == address }
        }
        return target
    }

    fun getSupportTypes(): List<String> {
        return listOf(WalletSettings.BTC, WalletSettings.ETH)
    }

    fun removeBCMWallet(coinType: String, wallet: BCMWallet) {
        getAccount(coinType)?.coinList?.remove(wallet)
    }

    fun isAccountExist(coinType: String): Boolean {
        return getAccount(coinType)?.coinList?.isNotEmpty() == true
    }

    /**
     * refresh， remove no source file wallet
     */
    fun refresh() {

        fun check(iterator: MutableIterator<BCMWallet>) {
            var wallet: BCMWallet
            while (iterator.hasNext()) {
                wallet = iterator.next()
                if (!wallet.getSourceFile().exists()) {
                    iterator.remove()
                }
            }
        }

        check(BTC.coinList.iterator())
        check(ETH.coinList.iterator())
    }
}