package com.bcm.messenger.wallet.btc

import com.bcm.messenger.wallet.btc.jsonrpc.*
import com.bcm.messenger.wallet.btc.request.*
import com.bcm.messenger.wallet.btc.response.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import com.bcm.messenger.utility.logger.ALog
import org.bitcoinj.core.Address
import org.bitcoinj.core.Sha256Hash
import org.bitcoinj.core.Utils
import org.bitcoinj.script.ScriptBuilder
import java.util.*
import kotlin.collections.ArrayList

/**
 * This is a Wapi Client that avoids calls that require BQS by talking to ElectrumX for related calls
 */
class WapiClientElectrumX(
        endpoints: Array<TcpEndpoint>,
        logger: WapiLogger)
    : Wapi, WapiClientLifecycle, ElectrumxServersChangedListener {

    data class BlockHeader(
            val height: Int,
            val hex: String
    )

    companion object {
        private const val TAG = "WapiClientElectrumX"
        private const val LIST_UNSPENT_METHOD = "blockchain.scripthash.listunspent"
        private const val ESTIMATE_FEE_METHOD = "blockchain.estimatefee"
        private const val BROADCAST_METHOD = "blockchain.transaction.broadcast"
        private const val GET_TRANSACTION_METHOD = "blockchain.transaction.get"
        private const val FEATURES_METHOD = "server.features"
        private const val HEADRES_SUBSCRIBE_METHOD = "blockchain.headers.subscribe"
        private const val GET_HISTORY_METHOD = "blockchain.scripthash.get_history"
        private const val GET_TRANSACTION_BATCH_LIMIT = 10
        private val errorRegex = Regex("the transaction was rejected by network rules.\\n\\n([0-9]*): (.*)\\n.*")
    }

    @Volatile
    private var mBestChainHeight = -1

    private val mReceiveHeaderCallback = { response: AbstractResponse ->
        val rpcResponse = response as RpcResponse
        mBestChainHeight = if (rpcResponse.hasResult) {
            rpcResponse.getResult(BlockHeader::class.java)?.height
        } else {
            val param = rpcResponse.getParams(Array<BlockHeader>::class.java)
            if (param != null && param.isNotEmpty()) {
                param[0].height
            } else {
                0
            }
        } ?: -1
    }

    private val mWapiLogger = logger
    private val mConnectionManager = ConnectionManager(5, endpoints, logger)

    override fun setAppInForeground(isInForeground: Boolean) {
        mConnectionManager.setActive(isInForeground)
    }

    override fun setNetworkConnected(isNetworkConnected: Boolean) {
        mConnectionManager.setNetworkConnected(isNetworkConnected)
    }

    override fun serverListChanged(newEndpoints: Collection<TcpEndpoint>) {
        mConnectionManager.changeEndpoints(newEndpoints.toTypedArray())
    }

    init {
        mConnectionManager.subscribe(Subscription(HEADRES_SUBSCRIBE_METHOD, RpcParams.listParams(), mReceiveHeaderCallback))
        mConnectionManager.setNetworkConnected(true)
        mConnectionManager.setActive(true)
    }

    private fun getAddressScriptHash(address: Address): String {
//        return Sha256Hash.of(address.hash).toString()
//        return Utils.HEX.encode(Sha256Hash.of(address.hash).reversedBytes)
        return Sha256Hash.wrapReversed(Sha256Hash.hash(ScriptBuilder.createOutputScript(address).pubKeyHash)).toString()
//        return Utils.HEX.encode(address.hash)
    }

    override fun queryUnspentOutputs(request: QueryUnspentOutputsRequest): WapiResponse<QueryUnspentOutputsResponse> {
        try {
            val unspent: ArrayList<QueryUnspentOutputsResponse.UnspentOutput> = ArrayList()
            val requestsList = ArrayList<RpcRequestOut>()
            val requestsIndexesMap = HashMap<String, Int>()
            val requestAddressesList = ArrayList(request.addresses)
            requestAddressesList.forEach {
                val addrScriptHash = getAddressScriptHash(it)
                ALog.d(TAG, "queryUnspentOutputs addrScriptHash: $addrScriptHash, address: $it")
                requestsList.add(RpcRequestOut(LIST_UNSPENT_METHOD, RpcParams.listParams(addrScriptHash)))
            }
            val unspentsArray = mConnectionManager.write(requestsList).responses

            //Fill temporary indexes map in order to find right address
            requestsList.forEachIndexed { index, req ->
                requestsIndexesMap[req.id.toString()] = index
            }
            unspentsArray.forEach { response ->
                ALog.d(TAG, "queryUnsepntOutputs each reponse: $response")
                val outputs = response.getResult(Array<QueryUnspentOutputsResponse.UnspentOutput>::class.java)
                if (outputs != null) {
                    unspent.addAll(outputs)
                }
            }
            ALog.d(TAG, "queyUnspentOutputs result size: ${unspent.size}")
            return WapiResponse(QueryUnspentOutputsResponse(mBestChainHeight, unspent))
        } catch (ex: CancellationException) {
            return WapiResponse<QueryUnspentOutputsResponse>(Wapi.ERROR_CODE_NO_SERVER_CONNECTION, null)
        }
    }

    override fun queryTransactionInventory(request: QueryTransactionInventoryRequest): WapiResponse<QueryTransactionInventoryResponse> {
        try {
            val requestsList = ArrayList<RpcRequestOut>(request.addresses.size)
            request.addresses.forEach {
                val addrScripthHash = getAddressScriptHash(it)
                requestsList.add(RpcRequestOut(GET_HISTORY_METHOD, RpcParams.listParams(addrScripthHash)))
            }
            val transactionHistoryArray =
                    if (requestsList.isEmpty()) {
                        arrayOf()
                    } else {
                        mConnectionManager.write(requestsList).responses
                    }
            ALog.d(TAG, "queryTransactionInventory response size: ${transactionHistoryArray.size}")

            val txHistoryList = mutableListOf<QueryTransactionInventoryResponse.TransactionHistoryInfo>()
            transactionHistoryArray.forEach {
                val array: Array<QueryTransactionInventoryResponse.TransactionHistoryInfo>? = it.getResult(Array<QueryTransactionInventoryResponse.TransactionHistoryInfo>::class.java)
                if (array != null) {
                    txHistoryList.addAll(array)
                }
            }
            ALog.d(TAG, "queryTransactionInventory history size: ${txHistoryList.size}")
            return WapiResponse(QueryTransactionInventoryResponse(mBestChainHeight, txHistoryList))

        } catch (ex: CancellationException) {
            return WapiResponse<QueryTransactionInventoryResponse>(Wapi.ERROR_CODE_NO_SERVER_CONNECTION, null)
        }
    }

    override fun getTransactions(request: GetTransactionsRequest): WapiResponse<GetTransactionsResponse> {
        return try {
            ALog.d(TAG, "getTransactions request: ${request.txIds.joinToString { it.toString() }}")
            val transactions = getTransactionXs(request.txIds.map { it.toString() }).map { tx ->
                tx.confirmations = if (tx.confirmations > 0) mBestChainHeight - tx.confirmations + 1 else -1
                tx.time = if (tx.time == 0) (Date().time / 1000).toInt() else tx.time
                tx
            }.sortedBy {
                it.time
            }
            ALog.d(TAG, "getTransactions result: ${transactions.size}")
            WapiResponse(GetTransactionsResponse(transactions))
        } catch (ex: CancellationException) {
            WapiResponse<GetTransactionsResponse>(Wapi.ERROR_CODE_NO_SERVER_CONNECTION, null)
        }
    }

    override fun broadcastTransaction(request: BroadcastTransactionRequest): WapiResponse<BroadcastTransactionResponse> {
        val responseList = try {
            val txHex = Utils.HEX.encode(request.rawTransaction)
            mConnectionManager.broadcast(BROADCAST_METHOD, RpcParams.listParams(txHex))
        } catch (ex: CancellationException) {
            return WapiResponse<BroadcastTransactionResponse>(Wapi.ERROR_CODE_NO_SERVER_CONNECTION, null)
        }
        return handleBroadcastResponse(responseList)
    }

    private fun handleBroadcastResponse(responseList: List<RpcResponse>): WapiResponse<BroadcastTransactionResponse> {
        try {
            if (responseList.all { it.hasError }) {
                responseList.forEach { response -> mWapiLogger.logError(response.error?.toString()) }
                val firstError = responseList[0].error
                        ?: return WapiResponse<BroadcastTransactionResponse>(Wapi.ERROR_CODE_PARSING_ERROR, null)
                val (errorCode, errorMessage) = if (firstError.code > 0) {
                    val electrumError = Wapi.ElectrumxError.getErrorByCode(firstError.code)
                    Pair(electrumError.errorCode, firstError.message)
                } else {
                    // This regexp is intended to calculate error code. Error codes are defined on bitcoind side, while
                    // message is constructed on Electrumx side, so this might change one day, so this code is not perfectly failsafe.
                    val errorMessageGroups = errorRegex.matchEntire(firstError.message)?.groups
                            ?: return WapiResponse<BroadcastTransactionResponse>(Wapi.ERROR_CODE_PARSING_ERROR, null)
                    val errorCode = errorMessageGroups[1]?.value?.toInt()
                            ?: return WapiResponse<BroadcastTransactionResponse>(Wapi.ERROR_CODE_PARSING_ERROR, null)
                    val errorMessage = errorMessageGroups[2]?.value
                            ?: return WapiResponse<BroadcastTransactionResponse>(Wapi.ERROR_CODE_PARSING_ERROR, null)
                    val error = Wapi.ElectrumxError.getErrorByCode(errorCode)
                    Pair(error.errorCode, errorMessage)
                }
                return WapiResponse<BroadcastTransactionResponse>(errorCode, errorMessage, null)
            }
            val txId = responseList.filter { !it.hasError }[0].getResult(String::class.java) ?: ""
            return WapiResponse(BroadcastTransactionResponse(true, txId))
        } catch (ex: Exception) {
            return WapiResponse<BroadcastTransactionResponse>(Wapi.ERROR_CODE_PARSING_ERROR, null)
        }
    }

    override fun checkTransactions(request: CheckTransactionsRequest): WapiResponse<CheckTransactionsResponse> {
        try {
            // TODO: make the transaction "check" use blockchain.address.subscribe instead of repeated
            // polling of blockchain.transaction.get
            val transactionsArray = null
            return WapiResponse(CheckTransactionsResponse(transactionsArray))
        } catch (ex: CancellationException) {
            return WapiResponse<CheckTransactionsResponse>(Wapi.ERROR_CODE_NO_SERVER_CONNECTION, null)
        }
    }

    @Throws(CancellationException::class)
    private fun getTransactionXs(txids: Collection<String>): List<GetTransactionsResponse.TransactionX> {
        if (txids.isEmpty()) {
            return emptyList()
        }
        val requestsList = txids.map {
            RpcRequestOut(GET_TRANSACTION_METHOD,
                    RpcParams.mapParams(
                            "tx_hash" to it,
                            "verbose" to true))
        }.toList().chunked(GET_TRANSACTION_BATCH_LIMIT)
        return requestTransactionsAsync(requestsList)
    }

    /**
     * This method is inteded to request transactions from different connections using endpoints list.
     */
    @Throws(CancellationException::class)
    private fun requestTransactionsAsync(requestsList: List<List<RpcRequestOut>>): List<GetTransactionsResponse.TransactionX> {
        return requestsList.pFlatMap {
            mConnectionManager.write(it)
                    .responses
                    .mapNotNull {
                        if (it.hasError) {
                            mWapiLogger.logError("requestTransactionsAsync failed: ${it.error}")
                            null
                        } else {
                            it.getResult(GetTransactionsResponse.TransactionX::class.java)
                        }
                    }
        }
    }

    private fun <A, B> List<A>.pFlatMap(f: suspend (A) -> List<B>): List<B> = runBlocking {
        map { async(Dispatchers.Default) { f(it) } }
                .flatMap { it.await() }
    }

}

