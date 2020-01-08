package com.bcm.messenger.wallet.utils

import android.os.Bundle
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.wallet.btc.*
import com.bcm.messenger.wallet.model.BCMWallet
import com.bcm.messenger.wallet.model.TransactionDisplay
import com.bcm.messenger.wallet.model.WalletDisplay
import okhttp3.Request
import org.bitcoinj.core.Coin
import org.bitcoinj.core.LegacyAddress
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.TransactionConfidence
import org.bitcoinj.params.AbstractBitcoinNetParams
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.script.Script
import org.bitcoinj.utils.MonetaryFormat
import org.bitcoinj.wallet.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicInteger


/**
 * created by wangjianhong on 2018/07/17
 */
class BtcWalletController(private val mManager: BCMWalletManagerContainer.BCMWalletManager) : IBaseWalletController {

    companion object {
        private const val TAG = "BtcWalletController"

        const val EXTRA_FEE = "extra_fee"
        const val EXTRA_MEMO = "extra_memo"

        /** Network this wallet is on (e.g. testnet or mainnet).  */
        val NETWORK_PARAMETERS = if (AppUtil.useDevBlockChain()) TestNet3Params.get() else MainNetParams.get()
    }

    private var mConfiguration: BitcoinConfiguration? = null

    private var mApi: Wapi? = null

    private val mUseMyceliumHttp = true//是否采用http接口方案

    private val mAccountIndex = AtomicInteger(-1)

    init {
        var configuration = mConfiguration
        if (configuration == null) {
            configuration = BitcoinConfiguration(BCMWalletManagerContainer.getSuperPreferences(AppContextHolder.APP_CONTEXT))
            mConfiguration = configuration
        }
        if (mApi == null) {

            mApi = if (!mUseMyceliumHttp) {
                val client = WapiClientElectrumX(configuration.getElectrumEndpoints().toTypedArray(), object : WapiLogger {
                    override fun logError(message: String?, e: java.lang.Exception?) {
                        ALog.e(TAG, "wapi call error: $message", e)
                    }

                    override fun logError(message: String?) {
                        ALog.w(TAG, "wapi call warning: $message")
                    }

                    override fun logInfo(message: String?) {
                    }

                })
                configuration.setTcpEndpoinsListChangedListener(client)
                client

            } else {

                val client = WapiClientNormal(configuration.getNormalApiEndPoints(), object : WapiLogger {
                    override fun logError(message: String?, e: java.lang.Exception?) {
                        ALog.e(TAG, "wapi call error: $message", e)
                    }

                    override fun logError(message: String?) {
                        ALog.w(TAG, "wapi call warning: $message")
                    }

                    override fun logInfo(message: String?) {
                    }

                })
                configuration.setHttpEndpointsListChangedListener(client)
                client

            }

        }
    }

    private var mSyncer: BtcWalletSyncHelper? = null

    fun clearPeerSyncHelper() {
        mSyncer?.clearWallet()
        mSyncer = null
    }

    fun checkMyceliumConfig() {
        if (mConfiguration?.isLastFetchError == true) {
            mConfiguration?.updateConfig()
        }
    }

    fun getClient(): Wapi? = mApi

    override fun getQRScheme(): String {
        return AbstractBitcoinNetParams.BITCOIN_SCHEME + ":"
    }

    private fun getBlockChainUrl(): String {
        return if (AppUtil.useDevBlockChain()) {
            "https://testnet.blockchain.info/"
        } else {
            "https://blockchain.info/"
        }
    }

    override fun getBrowserInfoUrl(tx: String): String {
        return if (!AppUtil.useDevBlockChain()) {
            "https://www.blockchain.com/btc/tx/$tx"
        } else {
            "https://www.blockchain.com/btctest/tx/$tx"
        }
    }

    override fun getCurrentAccountIndex(): Int {
        var index = mAccountIndex.get()
        if (index < 0) {
            val prefs = mManager.getAccountPreferences(AppContextHolder.APP_CONTEXT)
            index = prefs.getInt(BCMWalletManagerContainer.TABLE_WALLET_ACCOUNT + WalletSettings.BTC, 0)
            mAccountIndex.compareAndSet(-1, index)
        }
        return index
    }

    override fun setCurrentAccountIndex(index: Int) {
        mAccountIndex.set(index)
        val prefs = mManager.getAccountPreferences(AppContextHolder.APP_CONTEXT)
        val edit = prefs.edit()
        edit.putInt(BCMWalletManagerContainer.TABLE_WALLET_ACCOUNT + WalletSettings.BTC, index)
        edit.apply()
    }

    override fun getCurrentReceiveAddress(wallet: BCMWallet): String {
        val kit = getCurrentSyncHelper()
        try {
            return kit.getSourceWallet(wallet)?.currentReceiveAddress()?.toString()
                    ?: wallet.address

        } catch (ex: Exception) {
            ALog.e(TAG, "getCurrentReceiveAddress error", ex)
        }
        return wallet.address
    }

    override fun getDestinationDirectory(): File {
        val destination = File(AppContextHolder.APP_CONTEXT.filesDir, WalletSettings.BTC)
        if (!destination.exists()) {
            destination.mkdirs()
        }
        return destination
    }

    @Synchronized
    fun getCurrentSyncHelper(): BtcWalletSyncHelper {
        var kit = mSyncer
        return if (kit == null) {
            kit = BtcWalletSyncHelper(mManager)
            mSyncer = kit
            kit
        } else {
            kit
        }
    }

    @Synchronized
    fun startSync(restart: Boolean): Boolean {
        return false
    }

    @Synchronized
    fun stopSync(await: Boolean = false) {
    }

    override fun buildWallet(BCMWallet: BCMWallet, seed: DeterministicSeed, password: String): Boolean {

        try {
            ALog.i(TAG, "buildWallet address: ${BCMWallet.address}, account index: ${BCMWallet.accountIndex}")

            val keyChain = DeterministicKeyChain.builder().seed(seed).accountPath(KeyChainUtils.buildAccountPath(BCMWallet.coinType, BCMWallet.accountIndex))
                    .outputScriptType(Script.ScriptType.P2PKH).build()
            val keyChainGroup = KeyChainGroup.builder(NETWORK_PARAMETERS).build()
            keyChainGroup.addAndActivateHDChain(keyChain)

            val wallet = WalletEx(NETWORK_PARAMETERS, keyChainGroup, mManager, mApi)

            wallet.encrypt(password)
            if (!BCMWallet.getSourceFile().exists()) {
                wallet.saveToFile(BCMWallet.getSourceFile())
            }
            return true

        } catch (ex: Exception) {
            ALog.e(TAG, "buildNewWallet fail", ex)
        }
        return false
    }

    override fun checkValid(walletMap: MutableMap<String, Triple<BCMWallet, Boolean, Int>>): Int {
        if (walletMap.isEmpty()) {
            return 0
        }
        //最终达到的钱包账号索引（指向最后的有交易记录的真实存在的钱包的索引）
        var resultIndex = -1
        val maxTry = 3//最大尝试次数3次
        var currentTry = 0
        while (currentTry < maxTry) {
            try {
                resultIndex = 0
                val request = Request.Builder()
                val urlBuilder = StringBuilder(getBlockChainUrl() + "multiaddr?active=")
                walletMap.forEach {
                    urlBuilder.append(it.key)
                    urlBuilder.append("|")
                }
                if (urlBuilder.isNotEmpty()) {
                    urlBuilder.deleteCharAt(urlBuilder.length - 1)
                }
                urlBuilder.append("&n=1")
                request.url(urlBuilder.toString())
                ALog.i(TAG, "checkValid url: ${urlBuilder.toString()}")
                val response = BCMWalletManagerContainer.provideHttpClient().newCall(request.build()).execute()
                if (response.isSuccessful) {
                    val responseString = response.body()!!.string()
                    ALog.i(TAG, "checkValid btc response: $responseString")
                    val responseJson = JSONObject(responseString)
                    val addressArray = responseJson.optJSONArray("addresses")
                    if (addressArray != null && addressArray.length() > 0) {
                        for (i in 0 until addressArray.length()) {
                            val json = addressArray.optJSONObject(i)
                            if (json.optInt("n_tx") <= 0) {
                                //如果不存在记录，则删除
                                walletMap.remove(json.optString("address"))
                            } else {
                                val w = walletMap[json.optString("address")]?.first
                                //如果存在记录，则更新目前的索引
                                if (w != null && w.accountIndex > resultIndex) {
                                    resultIndex = w.accountIndex
                                }
                            }
                        }
                    } else {
                        walletMap.clear()
                    }
                    break

                } else {
                    throw Exception("checkValid not success: ${response.code()}")
                }

            } catch (ex: Exception) {
                ALog.e(TAG, "checkValid error", ex)
                resultIndex = -1
                currentTry++
            }
        }
        if (currentTry >= maxTry) {
            //如果重试次数大于maxTry，表示检测交易的网络请求异常，这时只能清空
            walletMap.clear()
        }
        return resultIndex

    }

    override fun queryBalance(wallet: BCMWallet): WalletDisplay {

        // 测试加载数据
        val walletEx = getCurrentSyncHelper().addWallet(wallet)
        walletEx.callSynchronization()
        val balance = walletEx.getBalance(Wallet.BalanceType.AVAILABLE) ?: Coin.ZERO
        return WalletDisplay(wallet, balance.value.toString()).apply {
            setManager(mManager)
        }
    }

    override fun queryBalance(walletList: List<BCMWallet>): List<WalletDisplay> {

        return walletList.map { queryBalance(it) }

    }

    override fun queryTransaction(wallet: BCMWallet): List<TransactionDisplay> {
        val sourceWallet = getCurrentSyncHelper().addWallet(wallet)
        val transactionList = sourceWallet.getTransactions(true)
        val resultList = transactionList.map { tran ->
            toTransactionDisplay(wallet, sourceWallet, tran)
        }.toMutableList()

        //按时间排序，最近的时间优先
        resultList.sortByDescending {
            it.date
        }

        return resultList
    }

    override fun broadcastTransaction(from: BCMWallet, toAddress: String, originAmount: String, toAmount: String, password: String, extra: Bundle): Pair<Boolean, String> {

        val originCoin = Coin.parseCoin(originAmount)
        val amountCoin = Coin.parseCoin(toAmount)
        val feeCoin = Coin.parseCoin(extra.getString(EXTRA_FEE, "0"))
        val memo = extra.getString(EXTRA_MEMO, "")

        ALog.i(TAG, "broadcastTransaction originCoin:${MonetaryFormat.BTC.format(originCoin)}, amountCoin:${MonetaryFormat.BTC.format(amountCoin)}" +
                ", feeCoin:${MonetaryFormat.BTC.format(feeCoin)}")

        //首先计算这次转账的数量，如果大于等于自身拥有的数量，则算上小费付出账户全部
        val request = if (amountCoin.isGreaterThan(originCoin) || amountCoin.equals(originCoin)) {
            SendRequest.emptyWallet(LegacyAddress.fromBase58(BtcWalletController.NETWORK_PARAMETERS, toAddress))
        } else {
            SendRequest.to(LegacyAddress.fromBase58(BtcWalletController.NETWORK_PARAMETERS, toAddress), amountCoin)
        }
        request.feePerKb = feeCoin
        request.memo = memo

        val walletEx = getCurrentSyncHelper().getSourceWallet(from)
                ?: throw Exception("broadcastTransaction fail, not found wallet")
        val keyParameter = if (password.isEmpty()) null else walletEx.keyCrypter?.deriveKey(password)
        if (keyParameter != null) {
            request.aesKey = keyParameter
        }
        val txId = walletEx.broadcastTransaction(request)
        return Pair(true, txId)
    }

    fun toTransactionDisplay(BCMWallet: BCMWallet, btcWallet: Wallet, tran: Transaction): TransactionDisplay {
        val amount = tran.getValue(btcWallet) ?: Coin.ZERO
        val sent = amount.isNegative
        val txId = tran.txId.toString()
        val fromAddress = if (sent) {
            getAddressOfInputs(tran, btcWallet, true)
        } else {
            getAddressOfInputs(tran, btcWallet, false)
        }
        val toAddress = if (sent) {
            getAddressOfOutputs(tran, btcWallet, false)
        } else {
            getAddressOfOutputs(tran, btcWallet, true)
        }
        val fee = tran.fee ?: Coin.ZERO
        val isError = tran.confidence.confidenceType == TransactionConfidence.ConfidenceType.DEAD
        return TransactionDisplay(BCMWallet, amount.value.toString(), fromAddress, toAddress, tran.confidence.depthInBlocks, tran.updateTime.time,
                txId, tran.params?.genesisBlock?.nonce?.toString(),
                if (tran.confidence.confidenceType == TransactionConfidence.ConfidenceType.BUILDING) {
                    tran.confidence.appearedAtChainHeight.toString()
                } else {
                    null
                }, fee.value.toString(), isError, tran.memo)
    }

    private fun getAddressOfOutputs(tx: Transaction, wallet: Wallet, isMine: Boolean): String {
        val addressList = mutableListOf<String>()
        for (output in tx.outputs) {
            try {
                val choose = if (isMine) output.isMine(wallet) else !output.isMine(wallet)
                if (choose) {
                    val script = output.scriptPubKey
                    val str = script.getToAddress(NETWORK_PARAMETERS, true).toString()
                    if (isMine) {
                        return str
                    } else {
                        addressList.add(str)
                    }
                }

            } catch (x: Exception) {
                // swallow
            }
        }
        return addressList.joinToString()
    }

    private fun getAddressOfInputs(tx: Transaction, wallet: Wallet, isMine: Boolean): String {
        val addressList = mutableListOf<String>()
        for (output in tx.inputs.mapNotNull { it.outpoint?.connectedOutput }) {
            try {
                val choose = if (isMine) output.isMine(wallet) else !output.isMine(wallet)
                if (choose) {
                    val script = output.scriptPubKey
                    val str = script.getToAddress(NETWORK_PARAMETERS, true).toString()
                    if (isMine) {
                        return str
                    } else {
                        addressList.add(str)
                    }
                }
            } catch (x: Exception) {
                // swallow
            }
        }
        return addressList.joinToString()
    }

}


