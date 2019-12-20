package com.bcm.messenger.wallet.utils

import android.annotation.SuppressLint
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.wallet.model.CurrencyEntry
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import okhttp3.Request
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Created by wjh on 2018/11/9
 */
abstract class BaseExchangeCalculator {

    companion object {
        const val INDEX_ETH = 0
        const val INDEX_BTC = 1
        const val INDEX_MONEY = 2
    }

    private var lastUpdateTimestamp: Long = 0

    protected val conversionNames = arrayOf(CurrencyEntry(WalletSettings.ETH, 0.0, "Ξ"),
            CurrencyEntry(WalletSettings.BTC, 0.0, "฿"), CurrencyEntry(WalletSettings.USD, 1.0, "$"))

    protected var index = INDEX_BTC

    val current: CurrencyEntry
        get() = conversionNames[index]

    val mainCurrency: CurrencyEntry
        get() = conversionNames[INDEX_MONEY]

    val etherCurrency: CurrencyEntry
        get() = conversionNames[INDEX_ETH]

    val bitcoinCurrency: CurrencyEntry
        get() = conversionNames[INDEX_BTC]

    val currencyShort: String
        get() = conversionNames[index].shorty

    val usdRate: Double
        get() = conversionNames[INDEX_MONEY].rate

    val btcRate: Double
        get() = conversionNames[INDEX_BTC].rate

    val ethRate: Double
        get() = conversionNames[INDEX_ETH].rate

    fun next(): CurrencyEntry {
        index = (index + 1) % conversionNames.size
        return conversionNames[index]
    }

    fun previous(): CurrencyEntry {
        index = if (index > 0) index - 1 else conversionNames.size - 1
        return conversionNames[index]
    }

    abstract fun convertAmountDisplay(sign: Boolean = false, vararg coin: String): CharSequence

    abstract fun convertMoneyDisplay(sign: Boolean = false, vararg coin: String): CharSequence

    abstract fun convertMoney(coin: String): BigDecimal

    abstract fun convertAmount(coin: String): BigDecimal

    abstract fun convertAmountFromRead(amount: String): BigDecimal

    abstract fun getCoinBase(): String

    fun updateExchangeRates(currency: String, callback: (success: Boolean) -> Unit) {
        //40分钟之类不需要再重新刷新
        if (lastUpdateTimestamp + 40 * 60 * 1000 > System.currentTimeMillis() && currency == conversionNames[INDEX_MONEY].name) { // Dont refresh if not older than 40 min and currency hasnt changed
            callback.invoke(true)
            return
        }
        lastUpdateTimestamp = System.currentTimeMillis()
        queryExchangeRate(getCoinBase(), currency) {result ->
            if (result != null) {
                if (currency != conversionNames[INDEX_MONEY].name) {
                    conversionNames[INDEX_MONEY].name = currency
                    conversionNames[INDEX_MONEY].shorty = WalletSettings.getCurrencyShortyUnit(currency)
                }
                conversionNames[INDEX_MONEY].rate = result
                callback.invoke(true)
            }else {
                //请求汇率失败，则不改变当前currency
                lastUpdateTimestamp = 0
                callback.invoke(false)
            }
        }
    }

    @SuppressLint("CheckResult")
    fun queryExchangeRate(coinBase: String, currency: String, callback: (result: Double?) -> Unit) {

        Observable.create(ObservableOnSubscribe<Double> {

            val request = Request.Builder()
            request.url("https://apiv2.bitcoinaverage.com/indices/global/ticker/short?crypto=" + coinBase + "&fiat=" + currency)

            val response = BCMWalletManager.provideHttpClient().newCall(request.build()).execute()
            if(response.isSuccessful) {
                val responseString = response.body()?.string()
                ALog.d("BaseExchangeCalculator", "findExchangeRate response: $responseString")
                val responseJson = JSONObject(responseString)
                val rateJson = responseJson.optJSONObject(coinBase + currency)
                var rateResult = 1.0
                if (rateJson != null) {
                    rateResult = rateJson.optJSONObject("averages")?.optDouble("day") ?: 1.0
                }
                it.onNext(rateResult)

            }else {
                it.onError(Exception("findExchangeRate response not success: ${response.code()}"))
            }
            it.onComplete()

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    callback.invoke(result)
                }, { ex ->
                    ALog.e("BaseExchangeCalculator", "findExchangeRate error", ex)
                    callback.invoke(null)
                })

    }

}