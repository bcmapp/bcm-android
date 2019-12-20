package com.bcm.messenger.wallet.utils

import android.content.Context
import android.graphics.drawable.Drawable
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.wallet.R
import com.bcm.messenger.wallet.model.TransactionDisplay
import com.bcm.messenger.utility.AppContextHolder

/**
 * created by wjh on 2018/05/17
 */
object WalletSettings {
    const val PREFIX_AME = "AME_"
    const val PREFIX_ADDRESS = "0x"

    const val ETH = "ETH"
    const val BTC = "BTC"

    const val CNY = "CNY"
    const val USD = "USD"
    const val EUR = "EUR"
    const val GBP = "GBP"
    const val AUD = "AUD"
    const val RUB = "RUB"
    const val CHF = "CHF"
    const val CAD = "CAD"
    const val JPY = "JPY"
    /**
     * red dot tip visible control
     */
    const val PREF_BACKUP_NOTICE = "wallet_backup_notice"
    /**
     * current currency unit
     */
    const val PREF_COIN_CURRENCY = "wallet_coin_currency"
    /**
     * free plan configure
     */
    const val PREF_FEE_PLAN = "wallet_fee_plan_"

    /**
     * bitcoin version
     */
    const val AME_BTC_VERSION = "1.2"

    const val GAS_LIMIT_DEFAULT = 21000

    const val CONFIDENCE_BTC = 6
    const val CONFIDENCE_ETH = 12

    fun getCurrencyShortyUnit(currency: String): String {
        return when (currency) {
            WalletSettings.CNY -> "¥"
            WalletSettings.USD -> "$"
            WalletSettings.EUR -> "€"
            WalletSettings.GBP -> "£"
            WalletSettings.AUD -> "$"
            WalletSettings.RUB -> "р"
            WalletSettings.CHF -> "Fr"
            WalletSettings.CAD -> "C$"
            WalletSettings.JPY -> "¥"
            else -> currency
        }
    }

    fun formatDefaultName(coinType: String, accountIndex: Int = -1): String {
        val index = if(accountIndex == -1) {
            BCMWalletManager.getCurrentAccountIndex(coinType)
        }else {
            accountIndex
        }
        val isMain = isBCMDefault(index)
        return if (isMain) {
            AppContextHolder.APP_CONTEXT.getString(R.string.wallet_name_main)
        } else {
            AppContextHolder.APP_CONTEXT.getString(R.string.wallet_name_child, index.toString())
        }
    }

    fun formatWalletLogo(context: Context, coinType: String): Drawable {

        return when (coinType) {
            WalletSettings.BTC -> AppUtil.getDrawable(context.resources, R.drawable.wallet_btc_icon)
            WalletSettings.ETH -> AppUtil.getDrawable(context.resources, R.drawable.wallet_eth_icon)
            else -> AppUtil.getDrawable(context.resources, R.drawable.wallet_eth_icon)
        }
    }

    fun formatWalletLogoForDetail(context: Context, coinType: String): Drawable {

        return when (coinType) {
            WalletSettings.BTC -> AppUtil.getDrawable(context.resources, R.drawable.wallet_btc_icon)
            WalletSettings.ETH -> AppUtil.getDrawable(context.resources, R.drawable.wallet_eth_icon)
            else -> AppUtil.getDrawable(context.resources, R.drawable.wallet_btc_icon)
        }
    }

    fun saveWalletBackNotice(notice: Boolean) {
        val prefs = BCMWalletManager.getAccountPreferences(AppContextHolder.APP_CONTEXT)
        val editor = prefs.edit()
        editor.putBoolean(PREF_BACKUP_NOTICE, notice)
        editor.apply()
    }

    fun getWalletBackNotice(): Boolean {
        val prefs = BCMWalletManager.getAccountPreferences(AppContextHolder.APP_CONTEXT)
        return prefs.getBoolean(PREF_BACKUP_NOTICE, true)
    }

    fun getCurrencyList(): List<String> {

        return listOf("CNY", "USD", "EUR", "JPY", "GBP", "AUD", "RUB", "CHF", "CAD")
    }

    fun getCurrentCurrency(): String {
        val prefs = BCMWalletManager.getAccountPreferences(AppContextHolder.APP_CONTEXT)
        return prefs.getString(PREF_COIN_CURRENCY, USD) ?: ""
    }

    fun saveCurrencyCode(currencyCode: String) {
        val edit = BCMWalletManager.getAccountPreferences(AppContextHolder.APP_CONTEXT).edit()
        edit.putString(PREF_COIN_CURRENCY, currencyCode)
        edit.apply()
    }

    fun isBCMDefault(accountIndex: Int): Boolean {
        return accountIndex == 0
    }

    fun createWalletPrefix(address: String, accountIndex: Int): String {
        return if (isBCMDefault(accountIndex)) {
            PREFIX_AME
        } else {
            ""
        } + address
    }

    fun displayTransactionStatus(transaction: TransactionDisplay): CharSequence {
        val resources = AppContextHolder.APP_CONTEXT.resources
        return if (transaction.isError) {
            resources.getString(R.string.wallet_transaction_fail_description)
        } else {
            var targetConfirmation = CONFIDENCE_BTC
            var resourceId = R.string.wallet_transaction_btc_confirms_description
            if (transaction.wallet.coinType == WalletSettings.ETH) {
                targetConfirmation = CONFIDENCE_ETH
                resourceId = R.string.wallet_transaction_confirms_description
            }

            when (transaction.confirmation) {
                0, -1 -> resources.getString(R.string.wallet_transaction_pending_description)
                in 1 until targetConfirmation -> resources.getString(resourceId, transaction.confirmation)
                else -> resources.getString(R.string.wallet_transaction_success_description)
            }
        }
    }

    fun getLastFeePlanTime(coinBase: String): Long {
        val prefs = BCMWalletManager.getAccountPreferences(AppContextHolder.APP_CONTEXT)
        return prefs.getLong(PREF_FEE_PLAN + coinBase, 0)
    }

    fun saveFeePlanString(coinBase: String, time: Long) {
        val edit = BCMWalletManager.getAccountPreferences(AppContextHolder.APP_CONTEXT).edit()
        edit.putLong(PREF_FEE_PLAN + coinBase, time)
        edit.apply()
    }
}
