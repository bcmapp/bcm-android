package com.bcm.messenger.wallet.utils

import android.os.Bundle
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.wallet.R
import com.bcm.messenger.wallet.model.BCMWallet
import com.bcm.messenger.wallet.model.FeePlan
import com.bcm.messenger.wallet.model.TransactionDisplay
import com.bcm.messenger.wallet.model.WalletDisplay
import com.bcm.messenger.wallet.network.EtherscanAPI
import org.bitcoinj.wallet.DeterministicSeed
import org.json.JSONObject
import org.spongycastle.util.encoders.Hex
import org.web3j.crypto.*
import org.web3j.protocol.ObjectMapperFactory
import org.web3j.protocol.core.methods.request.RawTransaction
import java.io.File
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by wjh on 2018/05/24
 */
class EthWalletController(private val mManager: BCMWalletManagerContainer.BCMWalletManager) : IBaseWalletController {

    companion object {
        private const val TAG = "EthWalletController"

        const val EXTRA_GAS_PRICE = "extra_gas_private"
        const val EXTRA_GAS_LIMIT = "extra_gas_limit"
        const val EXTRA_FEE = "extra_fee"
        const val EXTRA_MEMO = "extra_memo"
    }

    private val mAccountIndex = AtomicInteger(-1)

    override fun getQRScheme(): String {
        return "ethereum:"
    }

    override fun getBrowserInfoUrl(tx: String): String {
        return if (AppUtil.useDevBlockChain()) {
            "https://rinkeby.etherscan.io/tx/$tx"
        } else {
            "https://etherscan.io/tx/$tx"
        }
    }

    override fun getCurrentAccountIndex(): Int {
        var index = mAccountIndex.get()
        if (index < 0) {
            val prefs = mManager.getAccountPreferences(AppContextHolder.APP_CONTEXT)
            index = prefs.getInt(BCMWalletManagerContainer.TABLE_WALLET_ACCOUNT + WalletSettings.ETH, 0)
            mAccountIndex.compareAndSet(-1, index)
        }
        return index
    }

    override fun setCurrentAccountIndex(index: Int) {
        mAccountIndex.set(index)
        val prefs = mManager.getAccountPreferences(AppContextHolder.APP_CONTEXT)
        val edit = prefs.edit()
        edit.putInt(BCMWalletManagerContainer.TABLE_WALLET_ACCOUNT + WalletSettings.ETH, index)
        edit.apply()
    }

    override fun getCurrentReceiveAddress(wallet: BCMWallet): String {
        return wallet.address
    }

    override fun getDestinationDirectory(): File {
        val destination = File(AppContextHolder.APP_CONTEXT.filesDir, WalletSettings.ETH)
        if (!destination.exists()) {
            destination.mkdirs()
        }
        return destination
    }

    @Throws(Exception::class)
    fun loadCredentials(password: String, wallet: BCMWallet): Credentials {
        val sourceFile = wallet.getSourceFile()
        if (!sourceFile.exists()) {
            throw Exception("wallet source file not exists")
        }
        return WalletUtils.loadCredentials(password, wallet.getSourceFile())
    }

    override fun buildWallet(BCMWallet: BCMWallet, seed: DeterministicSeed, password: String): Boolean {

        try {
            val childKey = KeyChainUtils.computeMainChildKey(BCMWallet.coinType, BCMWallet.accountIndex, seed)
            val walletFile = Wallet.createStandard(password, ECKeyPair.create(childKey.privKey))
            val objectMapper = ObjectMapperFactory.getObjectMapper()
            objectMapper.writeValue(BCMWallet.getSourceFile(), walletFile)

            return true
        }catch (ex: Exception) {
            ALog.e(TAG, "buildWallet error", ex)
        }
        return false
    }

    override fun checkValid(walletMap: MutableMap<String, Triple<BCMWallet, Boolean, Int>>): Int {
        if (walletMap.isEmpty()) {
            return 0
        }
        var resultIndex = -1//当前指向的最后一个可用的钱包地址的索引
        val maxTry = 3//可重试次数
        var currentTry = 0
        while (currentTry < maxTry) {
            try {
                resultIndex = 0
                val result = EtherscanAPI.INSTANCE.getBalances(walletMap.map { it.value.first.getStandardAddress() })
                ALog.d(TAG, "checkValid: $result")
                val data = JSONObject(result).getJSONArray("result")
                if(data == null || data.length() == 0) {
                    walletMap.clear()
                }else {
                    val l = walletMap.map { it.value }
                    var balance: BigDecimal
                    for (wallet in l) {
                        balance = BigDecimal.ZERO
                        for (j in 0 until data.length()) {
                            if (data.getJSONObject(j).getString("account").equals(wallet.first.getStandardAddress(), ignoreCase = true)) {
                                balance = try {
                                    BigDecimal(data.getJSONObject(j).getString("balance"))
                                } catch (ex: Exception) {
                                    BigDecimal.ZERO
                                }
                                break
                            }
                        }
                        if (balance > BigDecimal.ZERO) {
                            if (wallet.first.accountIndex > resultIndex) {//更新当前指向的可用的钱包地址索引
                                resultIndex = wallet.first.accountIndex
                            }
                        } else {//余额为0，则表示不可用，直接删除
                            walletMap.remove(wallet.first.address)
                        }
                    }
                }
                break

            } catch (ex: Exception) {
                ALog.e(TAG, "checkValid error", ex)
                resultIndex = -1
                currentTry++
            }
        }
        if (currentTry >= maxTry) {
            //如果重试次数大于maxTry，表示检测交易的网络请求异常，这时只能把钱包map全清空
            walletMap.clear()
        }
        return resultIndex
    }

    override fun queryBalance(wallet: BCMWallet): WalletDisplay {
        val result = EtherscanAPI.INSTANCE.getBalance(wallet.getStandardAddress(), false)
        return WalletDisplay(wallet, parseBalance(result)).apply {
            setManager(mManager)
        }
    }

    override fun queryBalance(walletList: List<BCMWallet>): List<WalletDisplay> {
        val result = EtherscanAPI.INSTANCE.getBalances(walletList.map { it.getStandardAddress() })
        return parseWallets(result, walletList)
    }

    override fun queryTransaction(wallet: BCMWallet): List<TransactionDisplay> {
        val transactionList = mutableListOf<TransactionDisplay>()
        var result = EtherscanAPI.INSTANCE.getTransactions(false, wallet.getStandardAddress(), false)
        transactionList.addAll(parseTransactions(result, wallet))
        result = EtherscanAPI.INSTANCE.getTransactions(true, wallet.getStandardAddress(), false)
        transactionList.addAll(parseTransactions(result, wallet))
        return transactionList
    }

    override fun broadcastTransaction(from: BCMWallet, toAddress: String, originAmount: String, toAmount: String, password: String, extra: Bundle): Pair<Boolean, String> {

        //首先计算这次转账的数量，如果大于等于自身拥有的数量，则算上小费付出账户全部
        val originCoin = originAmount.toDouble()
        val amountCoin = toAmount.toDouble()
        val feeCoin = extra.getString(EXTRA_FEE, "0").toDouble()
        val targetAmount = if (originCoin == amountCoin || amountCoin > originCoin) {
            originCoin - feeCoin
        } else {
            amountCoin
        }
        val gasPrice = extra.getString(EXTRA_GAS_PRICE, "0")
        val gasLimit = extra.getString(EXTRA_GAS_LIMIT, "0")
        val memo = extra.getString(EXTRA_MEMO, "")

        var responseString = EtherscanAPI.INSTANCE.getNonceForAddress(from.getStandardAddress()).body()?.string()
                ?: ""
        if (responseString.isEmpty()) {
            throw Exception("broadcastTransaction fail, getNonceForAddress response null")
        }
        ALog.d(TAG, "getNonceFroAddress response: $responseString")
        val o = JSONObject(responseString)
        val nonce = BigInteger(o.getString("result").substring(2), 16)

        val tx = RawTransaction.createTransaction(
                nonce,
                BigDecimal(gasPrice).toBigInteger(),
                BigDecimal(gasLimit).toBigInteger(),
                toAddress,
                EthExchangeCalculator.convertAmountFromRead(targetAmount.toString()).toBigInteger(),
                memo
        )

        ALog.d(TAG,
                "Nonce: " + tx.nonce + "\n" +
                        "gasPrice: " + tx.gasPrice + "\n" +
                        "gasLimit: " + tx.gasLimit + "\n" +
                        "To: " + tx.to + "\n" +
                        "Amount: " + tx.value + "\n" +
                        "Data: " + tx.data
        )

        val signed = TransactionEncoder.signMessage(tx, EtherscanAPI.chainId, loadCredentials(password, from))

        responseString = EtherscanAPI.INSTANCE.forwardTransaction(WalletSettings.PREFIX_ADDRESS + Hex.toHexString(signed)).body()?.string()
                ?: ""
        if (responseString.isEmpty()) {
            throw Exception("broadcastTransaction fail, forwardTransaction response null")
        }
        ALog.d(TAG, "forwardTransaction response: $responseString")
        return try {
            val responseJson = JSONObject(responseString)
            val detail = responseJson.optString("result")
            if (detail.isNullOrEmpty()) {
                var errorMsg = responseJson.getJSONObject("error").getString("message")
                if (errorMsg.indexOf(".") > 0) {
                    errorMsg = errorMsg.substring(0, errorMsg.indexOf("."))
                }
                Pair(false, errorMsg)
            } else {
                Pair(true, detail)
            }
        } catch (ex: Exception) {
            ALog.e(TAG, "", ex)
            Pair(false, ex.message ?: "")
        }
    }

    fun findSuggestGasPrice(): List<FeePlan> {
        val result = EtherscanAPI.INSTANCE.getSuggestGasPrice()
        val planList = mutableListOf<FeePlan>()
        val context = AppContextHolder.APP_CONTEXT
        try {
            val json = JSONObject(result)

            planList.add(FeePlan(WalletSettings.ETH, context.getString(R.string.wallet_transfer_fee_high_description), json.optString("fastest", "200.0"), WalletSettings.GAS_LIMIT_DEFAULT.toString()))
            planList.add(FeePlan(WalletSettings.ETH, context.getString(R.string.wallet_transfer_fee_middle_description), json.optString("average", "70.0"), WalletSettings.GAS_LIMIT_DEFAULT.toString()))
            planList.add(FeePlan(WalletSettings.ETH, context.getString(R.string.wallet_transfer_fee_low_description), json.optString("safeLow", "50.0"), WalletSettings.GAS_LIMIT_DEFAULT.toString()))

        } catch (ex: Exception) {
            ALog.e(TAG, "findSuggestGasPrice error", ex)
            planList.clear()
            planList.add(FeePlan(WalletSettings.ETH, context.getString(R.string.wallet_transfer_fee_high_description), "200.0", WalletSettings.GAS_LIMIT_DEFAULT.toString()))
            planList.add(FeePlan(WalletSettings.ETH, context.getString(R.string.wallet_transfer_fee_middle_description), "70.0", WalletSettings.GAS_LIMIT_DEFAULT.toString()))
            planList.add(FeePlan(WalletSettings.ETH, context.getString(R.string.wallet_transfer_fee_low_description), "50.0", WalletSettings.GAS_LIMIT_DEFAULT.toString()))

        }
        return planList
    }

    private fun parseGasLimit(response: String?): BigInteger {
        try {
            val gasPrice = JSONObject(response).getString("result")
            return BigInteger(gasPrice.substring(2), 16)
        }catch (ex: Exception) {
            ALog.e(TAG, "parseGasLimit error", ex)
        }
        return BigInteger.ZERO
    }


    private fun parseWallets(response: String, walletList: List<BCMWallet>): ArrayList<WalletDisplay> {
        val displayList = ArrayList<WalletDisplay>()
        var index = 0
        try {
            val data = JSONObject(response).getJSONArray("result")
            for (i in walletList.indices) {
                index = i
                var balance = BigDecimal.ZERO.toString()
                for (j in 0 until data.length()) {
                    if (data.getJSONObject(j).getString("account").equals(walletList[i].getStandardAddress(), ignoreCase = true)) {
                        balance = try {
                            BigDecimal(data.getJSONObject(j).getString("balance")).toString()
                        } catch (ex: Exception) {
                            ALog.e(TAG, "parseWallets parse balance error", ex)
                            BigDecimal.ZERO.toString()
                        }
                        break
                    }
                }
                displayList.add(WalletDisplay(walletList[i], balance).apply {
                    setManager(mManager)
                })
            }
        }catch (ex: Exception) {
            ALog.e(TAG, "parseWallets error", ex)
            for(i in index until walletList.size) {
                displayList.add(WalletDisplay(walletList[i], BigDecimal.ZERO.toString()).apply {
                    setManager(mManager)
                })
            }
        }
        return displayList
    }

    private fun parseTransactions(response: String, wallet: BCMWallet): ArrayList<TransactionDisplay> {

        val result = ArrayList<TransactionDisplay>()
        try {
            val resultArray = JSONObject(response).getJSONArray("result")
            for (i in 0 until resultArray.length()) {
                val resultJson = resultArray.optJSONObject(i) ?: continue
                val from = resultJson.optString("from")
                val to = resultJson.optString("to")
                var flag = "+"
                if (wallet.getStandardAddress().equals(from, ignoreCase = true)) {
                    flag = "-"
                }
                val input = resultJson.optString("input")
                result.add(TransactionDisplay(wallet, flag + resultJson.optString("value"),
                        from,
                        to,
                        resultJson.optInt("confirmations", 13),
                        resultJson.optLong("timestamp", 0) * 1000,
                        resultJson.optString("hash"),
                        resultJson.optString("nonce", "0"),
                        resultJson.optString("blockNumber", "0"),
                        (resultJson.optLong("gasUsed") * resultJson.optLong("gasPrice")).toString(),
                        resultJson.optInt("isError") == 1,
                        input
                ))
            }

        }catch (ex: Exception) {
            ALog.e(TAG, "parseTransactions error", ex)
        }
        return result
    }

    private fun parseBalance(response: String): String {
        try {
            return BigDecimal(JSONObject(response).getString("result")).toString()
        }catch (ex: Exception) {
            ALog.e(TAG, "parseBalance error", ex)
        }
        return BigDecimal.ZERO.toString()
    }

    private fun parseGasPrice(response: String): BigInteger {
        try {
            val gasPrice = JSONObject(response).getString("result")
            return BigInteger(gasPrice.substring(2), 16)
        }catch (ex: Exception) {
            ALog.e(TAG, "parseGasPrice", ex)
        }
        return BigInteger.ZERO
    }
}