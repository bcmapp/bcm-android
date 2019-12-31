package com.bcm.messenger.wallet.utils

import com.bcm.messenger.utility.logger.ALog
import java.math.BigDecimal
import java.text.DecimalFormat

/**
 * 以太币与其他币种以及金额的汇率转换器
 * Created by wjh on 2018/5/16
 */
object EthExchangeCalculator : BaseExchangeCalculator() {

    private val TAG = "EthExchangeCalculator"

    val ONE_ETHER = BigDecimal("1000000000000000000")
    val ONE_GAS = BigDecimal("1000000000")

    val FormatterMoney = DecimalFormat("#,###,##0.00")
    val FormatterAmount = DecimalFormat("#,###,##0.0000")
    val FormatterAmountExact = DecimalFormat("#,###,##0.000000")

    init {
        index = INDEX_BTC
    }

    /**
     * 根据值生成正负符号
     */
    private fun getSign(value: BigDecimal): Char {
        return if (value.signum() >= 0) {
            '+'
        } else {
            '-'
        }
    }

    override fun getCoinBase(): String {
        return WalletSettings.ETH
    }

    /**
     * 转换人类可识别的值展示形式
     * @param sign 是否带符号
     * @param ether 多个ether值，全部相加
     */
    override fun convertAmountDisplay(sign: Boolean, vararg ether: String): CharSequence {
        val result = try {
            val coin = ether.fold(BigDecimal.ZERO, { total, next -> total.add(convertAmount(next)) }).setScale(6, BigDecimal.ROUND_HALF_UP)
            if (sign) {
                getSign(coin) + FormatterAmountExact.format(coin.abs())

            } else {
                FormatterAmountExact.format(coin.abs())
            }

        } catch (ex: Exception) {
            ALog.e(TAG, "convertAmountDisplay error", ex)
            FormatterAmountExact.format(BigDecimal.ZERO)
        }
        return result.toString() + " " + WalletSettings.ETH
    }

    /**
     * 转换成法币展示形式
     * @param sign 是否带正负符号
     * @param coin 多个ether值，全部相加
     */
    override fun convertMoneyDisplay(sign: Boolean, vararg coin: String): CharSequence {
        val result = try {
            val moneyCoin = coin.fold(BigDecimal.ZERO, { total, next -> total.add(convertMoney(next)) }).setScale(2, BigDecimal.ROUND_HALF_UP)
            if (sign) {
                getSign(moneyCoin) + FormatterMoney.format(moneyCoin.abs())

            } else {
                FormatterMoney.format(moneyCoin.abs())
            }
        } catch (ex: Exception) {
            ALog.e(TAG, "convertMoneyDisplay error", ex)
            FormatterMoney.format(BigDecimal.ZERO)
        }
        return result + " " + mainCurrency.name
    }

    /**
     * 转换成人类可识别的值
     */
    override fun convertAmount(coin: String): BigDecimal {
        return BigDecimal(coin).divide(ONE_ETHER, 8, BigDecimal.ROUND_UP)
    }

    /**
     * 转成法币值
     */
    override fun convertMoney(ether: String): BigDecimal {
        return BigDecimal(ether).divide(ONE_ETHER, 8, BigDecimal.ROUND_UP).multiply(BigDecimal(conversionNames[INDEX_MONEY].rate))
    }

    /**
     * 从人类可识别的值转成实际以太币值
     */
    override fun convertAmountFromRead(ether: String): BigDecimal {
        return BigDecimal(ether).multiply(ONE_ETHER)
    }

    /**
     * 从实际的gas值转成人类可识别的值
     */
    fun convertGas(gas: String): BigDecimal {
        return BigDecimal(gas).divide(ONE_GAS, 6, BigDecimal.ROUND_DOWN)
    }

    /**
     * 从gas ether转成法币值
     */
    fun convertGasMoney(gasEther: String): BigDecimal {
        return BigDecimal(gasEther).divide(ONE_GAS, 6, BigDecimal.ROUND_DOWN).multiply(BigDecimal(conversionNames[INDEX_MONEY].rate))
    }

    /**
     * 转换人类可识别的gas值展示形式
     * @param sign 是否带符号
     * @param gasEther 多个ether值，全部相加
     */
    fun convertGasDisplay(sign: Boolean = false, vararg gasEther: String): CharSequence {
        val result = try {
            val coin = gasEther.fold(BigDecimal.ZERO, { total, next -> total.add(convertGas(next)) })
            if (sign) {
                getSign(coin) + FormatterAmountExact.format(coin)

            } else {
                FormatterAmountExact.format(coin)
            }

        } catch (ex: Exception) {
            ALog.e(TAG, "convertGasDisplay error", ex)
            FormatterAmountExact.format(BigDecimal.ZERO)
        }
        return result + " " + WalletSettings.ETH
    }

    /**
     * 转换从gas ether转成法币值展示形式
     */
    fun convertGasMoneyDisplay(sign: Boolean = false, vararg gasEther: String): CharSequence {

        val result = try {
            val moneyCoin = gasEther.fold(BigDecimal.ZERO, { total, next -> total.add(convertGasMoney(next)) })
            if (sign) {
                getSign(moneyCoin) + FormatterMoney.format(moneyCoin)

            } else {
                FormatterMoney.format(moneyCoin)
            }
        } catch (ex: Exception) {
            ALog.e(TAG, "convertGasMoneyDisplay error", ex)
            FormatterMoney.format(BigDecimal.ZERO)
        }
        return result + " " + mainCurrency.name
    }

}
