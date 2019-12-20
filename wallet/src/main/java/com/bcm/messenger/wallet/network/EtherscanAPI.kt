package com.bcm.messenger.wallet.network

import com.bcm.messenger.common.utils.AppUtil
import com.orhanobut.logger.Logger
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.wallet.utils.BCMWalletManager
import java.io.IOException
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody

/**
 * ETH 平台接口api类
 * Created by wjh on 2018/05/19
 */
class EtherscanAPI private constructor() {

    companion object {

        private val TAG = "EtherscanAPI"

        private val OFFICIAL_HOST = "https://api.etherscan.io/"
        private val RINKEBY_HOST = "https://api-rinkeby.etherscan.io/"
//                "http://rinkeby.etherscan.io/"

        val INSTANCE: EtherscanAPI by lazy { EtherscanAPI() }

        val chainId: Byte
            get() = if (AppUtil.useDevBlockChain()) {
                4.toByte()
            } else {
                1.toByte()
            }
    }

    private val token: String
    private val currentHost: String//当前域名

    init {
        token = "UGDGCQ1J92XXTUYKFEMEZWJZZFCNTP9A54"
        currentHost = if (AppUtil.useDevBlockChain()) RINKEBY_HOST else OFFICIAL_HOST
    }

    fun getPriceChart(startTime: Long, period: Int, usd: Boolean, b: Callback) {
        request(true, "http://poloniex.com/public?command=returnChartData&currencyPair=" + (if (usd) "USDT_ETH" else "BTC_ETH") + "&start=" + startTime + "&end=9999999999&period=" + period, b)
    }

    fun getPriceChart(startTime: Long, period: Int, usd: Boolean): Response {
        return request(true, "http://poloniex.com/public?command=returnChartData&currencyPair=" + (if (usd) "USDT_ETH" else "BTC_ETH") + "&start=" + startTime + "&end=9999999999&period=" + period)
    }

    /**
     * 查询账号余额
     */
    fun getBalance(address: String, b: Callback) {
        request("api?module=account&action=balance&address=$address&apikey=$token", b)
    }

    fun getBalance(address: String, useCache: Boolean = false): String {
        //首先判断是否强制刷新，如果不是可以从缓存读取
        if(useCache && RequestCache.instance.contains(RequestCache.TYPE_BALANCES, address)) {
            return RequestCache.instance.get(RequestCache.TYPE_BALANCES, address) ?: ""
        }
        val response = request(false, "api?module=account&action=balance&address=$address&apikey=$token")
        return if(response.isSuccessful) {
            val result = response.body()?.string() ?: ""
            //更新缓存内容
            RequestCache.instance.put(RequestCache.TYPE_BALANCES, address, result)
            result

        }else {
            throw Exception("getBalance fail: ${response.code()}")
        }
    }

    /**
     * 查询多个地址的余额
     */
    fun getBalances(addresses: List<String>, b: Callback) {
        val urlBuilder = StringBuilder("api?module=account&action=balancemulti&address=")
        for (address in addresses) {
            urlBuilder.append(address)
            urlBuilder.append(",")
        }
        if (!addresses.isEmpty()) {
            urlBuilder.deleteCharAt(urlBuilder.length - 1)
        }
        // remove last , AND add token
        urlBuilder.append("&tag=latest&apikey=")
        urlBuilder.append(token)
        request(urlBuilder.toString(), b)
    }

    fun getBalances(addresses: List<String>): String {
        val urlBuilder = StringBuilder("api?module=account&action=balancemulti&address=")
        for (address in addresses) {
            urlBuilder.append(address)
            urlBuilder.append(",")
        }
        if (!addresses.isEmpty()) {
            urlBuilder.deleteCharAt(urlBuilder.length - 1)
        }
        // remove last , AND add token
        urlBuilder.append("&tag=latest&apikey=")
        urlBuilder.append(token)
        val response= request(false, urlBuilder.toString())
        return if(response.isSuccessful) {
            response.body()?.string() ?: ""
        }else {
            throw Exception("getBalances fail: ${response.code()}")
        }
    }

    /**
     * Retrieve all internal transactions from address like contract calls, for normal transactions @see rehanced.com.simpleetherwallet.network.EtherscanAPI#getNormalTransactions() )
     *
     * @param address Ether address
     * @param b       Network callback to @see rehanced.com.simpleetherwallet.fragments.FragmentTransactions#update() or @see rehanced.com.simpleetherwallet.fragments.FragmentTransactionsAll#update()
     * @param force   Whether to force (true) a network call or use cache (false). Only true if user uses swiperefreshlayout
     * @throws IOException Network exceptions
     */
    fun getInternalTransactions(address: String, b: Callback, useCache: Boolean) {
        var cacheString: String? = null
        if (useCache && RequestCache.instance.contains(RequestCache.TYPE_TXS_INTERNAL, address)) {
            cacheString = RequestCache.instance.get(RequestCache.TYPE_TXS_INTERNAL, address)
        }
        request("api?module=account&action=txlistinternal&address=$address&startblock=0&endblock=99999999&sort=asc&apikey=$token", b, cacheString)
    }

    fun getTransactions(normal: Boolean, address: String, useCache: Boolean = false): String {
        val urlPart = if(normal) {
            "api?module=account&action=txlist&address=$address&startblock=0&endblock=99999999&sort=desc&apikey=$token"
        }else {
            "api?module=account&action=txlistinternal&address=$address&startblock=0&endblock=99999999&sort=desc&apikey=$token"
        }
        val type = if(normal) RequestCache.TYPE_TXS_NORMAL else RequestCache.TYPE_TXS_INTERNAL
        if(useCache && RequestCache.instance.contains(type, address)) {//首先判断是否强制刷新，如果不是可以直接从缓存读取内容
            return RequestCache.instance.get(type, address) ?: ""
        }
        val response = request(false, urlPart)
        return if(response.isSuccessful) {
            val result = response.body()?.string() ?: ""
            //更新缓存内容
            RequestCache.instance.put(type, address, result)
            result
        }else {
            throw Exception("getInternalTransactions fail: ${response.code()}")
        }
    }

    /**
     * Retrieve all normal ether transactions from address (excluding contract calls etc, @see rehanced.com.simpleetherwallet.network.EtherscanAPI#getInternalTransactions() )
     *
     * @param address Ether address
     * @param b       Network callback to @see rehanced.com.simpleetherwallet.fragments.FragmentTransactions#update() or @see rehanced.com.simpleetherwallet.fragments.FragmentTransactionsAll#update()
     * @param force   Whether to force (true) a network call or use cache (false). Only true if user uses swiperefreshlayout
     * @throws IOException Network exceptions
     */
    fun getNormalTransactions(address: String, b: Callback, useCache: Boolean) {
        var cacheString: String? = null
        if (useCache && RequestCache.instance.contains(RequestCache.TYPE_TXS_NORMAL, address)) {
            cacheString = RequestCache.instance.get(RequestCache.TYPE_TXS_NORMAL, address)
        }
        request("api?module=account&action=txlist&address=$address&startblock=0&endblock=99999999&sort=asc&apikey=$token", b, cacheString)
    }

    /**
     * 发送交易
     */
    fun forwardTransaction(raw: String, b: Callback) {
        request("api?module=proxy&action=eth_sendRawTransaction&hex=$raw&apikey=$token", b)
    }

    fun forwardTransaction(raw: String): Response {
        return request(false, "api?module=proxy&action=eth_sendRawTransaction&hex=$raw&apikey=$token")
    }

    fun getEtherPrice(b: Callback) {
        request("api?module=stats&action=ethprice&apikey=$token", b)
    }

    fun getEtherPrice(): Response {
        return request(false, "api?module=stats&action=ethprice&apikey=$token")
    }

    fun getGasPrice(b: Callback) {
        request("api?module=proxy&action=eth_gasPrice&apikey=$token", b)
    }

    fun getGasPrice(): Response {
        return request(false, "api?module=proxy&action=eth_gasPrice&apikey=$token")
    }

    /**
     * Get token balances via ethplorer.io
     *
     * @param address Ether address
     * @param b       Network callback to @see rehanced.com.simpleetherwallet.fragments.FragmentDetailOverview#update()
     * @param force   Whether to force (true) a network call or use cache (false). Only true if user uses swiperefreshlayout
     * @throws IOException Network exceptions
     */
    fun getTokenBalances(address: String, b: Callback, force: Boolean) {
        var cacheString: String? = null
        if (!force && RequestCache.instance.contains(RequestCache.TYPE_TOKEN, address)) {
            cacheString = RequestCache.instance.get(RequestCache.TYPE_TOKEN, address)
        }
        request("http://api.ethplorer.io/getAddressInfo/$address?apiKey=freekey", b, cacheString)

    }

    fun getTokenBalances(address: String): Response {
        return request(true, "http://api.ethplorer.io/getAddressInfo/$address?apiKey=freekey")
    }

    /**
     * 查询小费gasLimit
     */
    fun getGasLimitEstimate(to: String, b: Callback) {
        request("api?module=proxy&action=eth_estimateGas&to=$to&apikey=$token", b)
    }

    fun getGasLimitEstimate(to: String): Response {
        return request(false, "api?module=proxy&action=eth_estimateGas&to=$to&apikey=$token")
    }

    /**
     * 查询最合适的gasPrice
     */
    fun getSuggestGasPrice(b: Callback) {
        request(true, "http://ethgasstation.info/json/ethgasAPI.json", b)
    }

    fun getSuggestGasPrice(): String {
        val response = request(true, "http://ethgasstation.info/json/ethgasAPI.json")
        return if(response.isSuccessful) {
            response.body()?.string() ?: ""
        }else {
            throw Exception("getSuggestGasPrice fail: ${response.code()}")
        }
    }

    /**
     * 查询nonce
     */
    fun getNonceForAddress(address: String, b: Callback) {
        request("api?module=proxy&action=eth_getTransactionCount&address=$address&tag=latest&apikey=$token", b)
    }

    fun getNonceForAddress(address: String): Response {
        return request(false, "api?module=proxy&action=eth_getTransactionCount&address=$address&tag=latest&apikey=$token")
    }

    /**
     * 查询汇率
     */
    fun getPriceConversionRates(currencyConversion: String, b: Callback) {
        request(true, "https://api.fixer.io/latest?base=USD&symbols=$currencyConversion", b)
    }

    fun getPriceConversationRates(currencyConversion: String): Response {
        return request(true, "https://api.fixer.io/latest?base=USD&symbols=$currencyConversion")
    }


    private fun request(url: String, b: Callback, cacheString: String? = null) {
        request(false, url, b, cacheString)
    }

    private fun request(useFullUrl: Boolean, url: String, b: Callback, cacheString: String? = null) {
        val fullUrl = if (useFullUrl) url else currentHost + url
        val request = Request.Builder()
                .url(fullUrl)
                .build()
        val call = BCMWalletManager.provideHttpClient().newCall(request)
        if (!cacheString.isNullOrEmpty()) {
            Logger.d("EtherscanAPI直接从缓存获取数据")
            b.onResponse(call, Response.Builder().code(200).message("").request(Request.Builder()
                    .url(fullUrl).build()).protocol(Protocol.HTTP_1_0).body(ResponseBody.create(MediaType.parse("JSON"), cacheString)).build())
            return
        } else {
            call.enqueue(b)
        }
    }

    private  fun request(useFullUrl: Boolean, url: String): Response {
        val fullUrl = if (useFullUrl) url else currentHost + url
        val request = Request.Builder()
                .url(fullUrl)
                .build()
        ALog.d(TAG, "request url: $fullUrl")
        val client = BCMWalletManager.provideHttpClient()
        return client.newCall(request).execute()
    }



}
