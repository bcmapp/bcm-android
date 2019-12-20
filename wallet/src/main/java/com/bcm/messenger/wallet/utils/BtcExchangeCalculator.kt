package com.bcm.messenger.wallet.utils

import com.orhanobut.logger.Logger
import org.bitcoinj.core.Coin
import org.bitcoinj.utils.ExchangeRate
import org.bitcoinj.utils.Fiat
import org.bitcoinj.utils.MonetaryFormat
import java.math.BigDecimal

/**
 * Created by wjh on 2018/5/25
 */
object BtcExchangeCalculator : BaseExchangeCalculator() {

    init {
        index = INDEX_ETH
    }
    private val AMOUNT_FORMAT = MonetaryFormat().shift(0).minDecimals(6).noCode()
    private val MONEY_FORMAT = MonetaryFormat().shift(0).minDecimals(2).noCode()

    override fun getCoinBase(): String {
        return WalletSettings.BTC
    }

    override fun convertAmountDisplay(sign: Boolean, vararg coin: String): CharSequence {
        val result = try {
            val newCoin = coin.fold(Coin.ZERO, { total, next -> total.add(Coin.valueOf(next.toLong())) })
            if (sign) {
                AMOUNT_FORMAT.negativeSign('-').positiveSign('+').format(newCoin)
            } else {
                AMOUNT_FORMAT.format(newCoin)
            }

        } catch (ex: Exception) {
            Logger.e(ex, "BtcExchangeCalculator convertAmountDisplay error")
            AMOUNT_FORMAT.format(Coin.ZERO)
        }
        return result.toString() + " " + WalletSettings.BTC
    }

    override fun convertMoneyDisplay(sign: Boolean, vararg coin: String): CharSequence {
        val result = try {
            val moneyCoin = coin.fold(Coin.ZERO, { total, next -> total.add(Coin.valueOf(next.toLong())) })
            val fiat = parseFiatInexact(conversionNames[INDEX_MONEY].name, conversionNames[INDEX_MONEY].rate.toString())
            val exchangeRate = ExchangeRate(fiat)
            if (sign) {
                MONEY_FORMAT.negativeSign('-').positiveSign('+').format(exchangeRate.coinToFiat(moneyCoin))
            } else {
                MONEY_FORMAT.format(exchangeRate.coinToFiat(moneyCoin))
            }

        } catch (ex: Exception) {
            Logger.e(ex, "BtcExchangeCalculator convertMoneyDisplay error")
            MONEY_FORMAT.format(Coin.ZERO)
        }
        return result.toString() + " " + BtcExchangeCalculator.mainCurrency.name
    }

    override fun convertMoney(moneySatoshi: String): BigDecimal {
        val fiat = parseFiatInexact(conversionNames[INDEX_MONEY].name, conversionNames[INDEX_MONEY].rate.toString())
        val exchangeRate = ExchangeRate(fiat)
        val shatoshi = exchangeRate.coinToFiat(Coin.valueOf(moneySatoshi.toLong()))
        Logger.d("BtcExchangeCalculator convertMoney: ${MonetaryFormat.FIAT.format(shatoshi)}")
        return BigDecimal(shatoshi.longValue()).movePointLeft(Fiat.SMALLEST_UNIT_EXPONENT)
    }

    override fun convertAmountFromRead(amount: String): BigDecimal {
        return Coin.parseCoin(amount).value.toBigDecimal()
    }

    override fun convertAmount(satoshi: String): BigDecimal {
        return BigDecimal(satoshi).movePointLeft(Coin.SMALLEST_UNIT_EXPONENT)
    }

    private fun parseFiatInexact(currencyCode: String, fiatRate: String): Fiat {
        val `val` = BigDecimal(fiatRate).movePointRight(Fiat.SMALLEST_UNIT_EXPONENT).toLong()
        return Fiat.valueOf(currencyCode, `val`)
    }

}