package com.bcm.messenger.wallet.btc

import com.bcm.messenger.utility.Base64
import com.bcm.messenger.wallet.btc.net.FeedbackEndpoint
import com.bcm.messenger.wallet.btc.net.ServerEndpoints
import com.bcm.messenger.wallet.btc.request.*
import com.bcm.messenger.wallet.btc.response.*
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonMappingException
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.bcm.messenger.utility.logger.ALog
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.lang.reflect.Type
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Created by wjh on 2019/3/24
 */
class WapiClientNormal(endpoints: ServerEndpoints, logger: WapiLogger)
    : Wapi, WapiClientLifecycle, NormalServersChangedListener {

    companion object {
        private const val TAG = "WapiClientNormal"

        private val VERY_LONG_TIMEOUT_MS = 50 * 1000 // 50s
        private val LONG_TIMEOUT_MS = 25 * 1000 // 20s
        private val MEDIUM_TIMEOUT_MS = 12 * 1000 // 12s
        private val SHORT_TIMEOUT_MS = 4 * 1000 // 4s
        private val SHORT_TO_LONG_TIMEOUTS_MS = intArrayOf(SHORT_TIMEOUT_MS, MEDIUM_TIMEOUT_MS, LONG_TIMEOUT_MS, VERY_LONG_TIMEOUT_MS)

        val MYCELIUM_VERSION_HEADER = "MyceliumVersion"
        val MYCELIUM_VERSION_VALUE = "2120023"

        val QUERY_UNSPENT_OUTPUTS = "queryUnspentOutputs"
        val QUERY_TRANSACTION_INVENTORY = "queryTransactionInventory"
        val GET_TRANSACTIONS = "getTransactions"
        val BROADCAST_TRANSACTION = "broadcastTransaction"
        val CHECK_TRANSACTIONS = "checkTransactions"

        private var sMinTimeout = 0
    }

    private val mWapiLogger = logger
    private var mWapiEndpoints: ServerEndpoints = endpoints
    private val mGson = Gson()

    @Volatile
    private var mBestChainHeight = 0

    override fun queryUnspentOutputs(request: QueryUnspentOutputsRequest): WapiResponse<QueryUnspentOutputsResponse> {
        val response: WapiResponse<QueryUnspentOutputsResponse> = sendRequest(QUERY_UNSPENT_OUTPUTS, request, object : TypeToken<WapiResponse<QueryUnspentOutputsResponse>>() {}.type)
        if (response.result.height > 0) {
            mBestChainHeight = response.result.height
        }
        response.result.unspent?.forEach {
            val outpoint = it.outPoint
            if (!outpoint.isNullOrEmpty()) {
                it.txHash = outpoint.split(":")[0]
            }
        }
        return response
    }

    override fun queryTransactionInventory(request: QueryTransactionInventoryRequest): WapiResponse<QueryTransactionInventoryResponse> {
        val response: WapiResponse<QueryTransactionInventoryResponse> =
                if (request.addresses.isEmpty()) {
                    WapiResponse(QueryTransactionInventoryResponse(mBestChainHeight, listOf()))
                } else {
                    sendRequest(QUERY_TRANSACTION_INVENTORY, request, object : TypeToken<WapiResponse<QueryTransactionInventoryResponse>>() {}.type)
                }
        if (response.result.height > 0) {
            mBestChainHeight = response.result.height
        }
        val transactionHistoryList = response.result.txIds?.map {
            val t = QueryTransactionInventoryResponse.TransactionHistoryInfo()
            t.txHash = it
            t
        } ?: listOf()

        return WapiResponse(response.errorCode, QueryTransactionInventoryResponse(response.result.height, transactionHistoryList))
    }

    override fun getTransactions(request: GetTransactionsRequest): WapiResponse<GetTransactionsResponse> {

        val response: WapiResponse<GetTransactionsResponse> = if (request.txIds.isEmpty()) {
            WapiResponse(GetTransactionsResponse(listOf()))
        } else {
            sendRequest(GET_TRANSACTIONS, request, object : TypeToken<WapiResponse<GetTransactionsResponse>>() {}.type)
        }
        response.result.transactions?.forEach {
            ALog.d(TAG, "getTransactions tx: ${it.txid}, confirmation: ${it.height}")
            it.confirmations = if (it.height > 0) {
                mBestChainHeight - it.height + 1
            } else -1
            it.time = if (it.time == 0) (Date().time / 1000).toInt() else it.time
            it.hex = it.binary
        }
        response.result.transactions?.sortedBy { it.time }
        return response
    }

    override fun broadcastTransaction(request: BroadcastTransactionRequest): WapiResponse<BroadcastTransactionResponse> {

        val newRequest = BroadcastTransactionBase64Request(request.version, Base64.encodeBytes(request.rawTransaction))
        return sendRequest(BROADCAST_TRANSACTION, newRequest, object : TypeToken<WapiResponse<BroadcastTransactionResponse>>() {}.type)
    }

    override fun checkTransactions(request: CheckTransactionsRequest): WapiResponse<CheckTransactionsResponse> {
        return sendRequest(CHECK_TRANSACTIONS, request, object : TypeToken<WapiResponse<CheckTransactionsResponse>>() {}.type)
    }

    override fun setAppInForeground(isInForeground: Boolean) {
    }

    override fun setNetworkConnected(isNetworkConnected: Boolean) {
    }

    override fun serverListChanged(newEndpoints: ServerEndpoints) {
        ALog.d(TAG, "serverListChanged")
        mWapiEndpoints = newEndpoints
    }

    private fun <T> sendRequest(function: String, request: Any?, type: Type): WapiResponse<T> {

        try {
            val requestBody = getPostBody(request)
            ALog.d(TAG, "sendRequest method: $function, body: $requestBody")

            val response = getConnectionAndSendRequest(function, requestBody)
                    ?: return WapiResponse<T>(Wapi.ERROR_CODE_NO_SERVER_CONNECTION, null)
            val responseString = response.body()?.string() ?: ""

            ALog.d(TAG, "sendRequest method: $function, response: $responseString")
            return mGson.fromJson(responseString, type)

        } catch (e: JsonParseException) {
            mWapiLogger.logError("sendRequest failed with Json parsing error.", e)
            return WapiResponse<T>(Wapi.ERROR_CODE_INTERNAL_CLIENT_ERROR, null)
        } catch (e: JsonMappingException) {
            mWapiLogger.logError("sendRequest failed with Json mapping error.", e)
            return WapiResponse<T>(Wapi.ERROR_CODE_INTERNAL_CLIENT_ERROR, null)
        } catch (e: IOException) {
            mWapiLogger.logError("sendRequest failed IO exception.", e)
            return WapiResponse<T>(Wapi.ERROR_CODE_INTERNAL_CLIENT_ERROR, null)
        } catch (e: Exception) {
            mWapiLogger.logError("sendRequest fail other exception", e)
            return WapiResponse<T>(Wapi.ERROR_CODE_INTERNAL_CLIENT_ERROR, null)
        }

    }

    /**
     * Attempt to connect and send to a URL in our list of URLS, if it fails try
     * the next until we have cycled through all URLs. If this fails with a short
     * timeout, retry all servers with a medium timeout, followed by a retry with
     * long timeout.
     */
    private fun getConnectionAndSendRequest(function: String, request: String): Response? {
        for (timeout in SHORT_TO_LONG_TIMEOUTS_MS) {
            if (timeout < sMinTimeout) {
                // if some timeout was too short for all servers, maybe we hit all of them but were slow ourselves
                continue
            }
            val response = getConnectionAndSendRequestWithTimeout(request, function, timeout.toLong())
            if (response != null) {
                if (timeout > sMinTimeout) {
                    sMinTimeout = timeout
                }
                return response
            }
        }
        return null
    }

    /**
     * Attempt to connect and send to a URL in our list of URLS, if it fails try
     * the next until we have cycled through all URLs. timeout.
     */
    private fun getConnectionAndSendRequestWithTimeout(request: String, function: String, timeout: Long): Response? {
        val originalConnectionIndex = mWapiEndpoints.currentEndpointIndex
        while (true) {
            // currently active server-endpoint
            val serverEndpoint = mWapiEndpoints.currentEndpoint
            try {
                val builder = serverEndpoint.client.newBuilder()
                mWapiLogger.logInfo("Connecting to " + serverEndpoint.baseUrl + " (" + mWapiEndpoints.currentEndpointIndex + ") ")

                builder.connectTimeout(timeout, TimeUnit.MILLISECONDS)
                builder.readTimeout(timeout, TimeUnit.MILLISECONDS)
                builder.writeTimeout(timeout, TimeUnit.MILLISECONDS)

                val callStart = System.currentTimeMillis()
                // build request
                val rq = Request.Builder()
                        .addHeader(MYCELIUM_VERSION_HEADER, MYCELIUM_VERSION_VALUE)
                        .post(RequestBody.create(MediaType.parse("application/json"), request))
                        .url(serverEndpoint.getUri("/wapi", function).toString())
                        .build()

                // execute request
                val response = builder.build().newCall(rq).execute()
                mWapiLogger.logInfo(String.format(Locale.ENGLISH, "Wapi %s finished (%dms)", function, System.currentTimeMillis() - callStart))

                // Check for status code 2XX
                if (response.isSuccessful()) {
                    if (serverEndpoint is FeedbackEndpoint) {
                        (serverEndpoint as FeedbackEndpoint).onSuccess()
                    }
                    return response

                } else {
                    // If the status code is not 200 we cycle to the next server
                    mWapiLogger.logInfo(String.format(Locale.ENGLISH, "Http call to %s failed with %d %s", function, response.code(), response.message()))
                    // throw...
                }
            } catch (e: IOException) {
                mWapiLogger.logError("IOException when sending request $function", e)
                if (serverEndpoint is FeedbackEndpoint) {
                    mWapiLogger.logInfo("Resetting tor")
                    (serverEndpoint as FeedbackEndpoint).onError()
                }
            } catch (e: RuntimeException) {
                mWapiLogger.logError("Send request fail", e)
            }

            // Try the next server
            mWapiEndpoints.switchToNextEndpoint()

            if (mWapiEndpoints.getCurrentEndpointIndex() == originalConnectionIndex) {
                // We have tried all URLs
                return null
            }
        }
    }

    private fun getPostBody(request: Any?): String {
        if (request == null) {
            return ""
        }
        try {
            return request.toString()
        } catch (e: JsonProcessingException) {
            ALog.e(TAG, "getPostBody error", e)
            throw RuntimeException(e)
        }

    }
}