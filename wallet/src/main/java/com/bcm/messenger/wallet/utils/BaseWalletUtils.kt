package com.bcm.messenger.wallet.utils

import android.os.Bundle
import com.bcm.messenger.wallet.model.BCMWallet
import com.bcm.messenger.wallet.model.TransactionDisplay
import com.bcm.messenger.wallet.model.WalletDisplay
import org.bitcoinj.wallet.DeterministicSeed
import java.io.File

/**
 * Created by wjh on 2018/11/7
 */
interface BaseWalletUtils {

    fun getQRScheme(): String

    fun getBrowserInfoUrl(tx: String): String

    fun getCurrentAccountIndex(): Int
    fun setCurrentAccountIndex(index: Int)

    fun getCurrentReceiveAddress(wallet: BCMWallet): String

    fun getDestinationDirectory(): File

    fun buildWallet(BCMWallet: BCMWallet, seed: DeterministicSeed, password: String): Boolean

    fun checkValid(walletMap: MutableMap<String, Triple<BCMWallet, Boolean, Int>>): Int

    fun queryBalance(wallet: BCMWallet): WalletDisplay

    fun queryBalance(walletList: List<BCMWallet>): List<WalletDisplay>

    fun queryTransaction(wallet: BCMWallet): List<TransactionDisplay>

    fun broadcastTransaction(from: BCMWallet, toAddress: String, originAmount: String, toAmount: String, password: String, extra: Bundle): Pair<Boolean, String>

}