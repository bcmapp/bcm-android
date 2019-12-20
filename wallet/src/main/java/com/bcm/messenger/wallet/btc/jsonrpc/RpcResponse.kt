package com.bcm.messenger.wallet.btc.jsonrpc

import com.bcm.messenger.utility.proguard.NotGuard
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import java.io.BufferedReader
import java.lang.reflect.Type

open class AbstractResponse : NotGuard

class RpcResponse : AbstractResponse() {
    companion object {
        fun fromJson(json: BufferedReader): RpcResponse =
                RPC.fromJson(json, RpcResponse::class.java)
//                RPC.fromJson(json, object : TypeToken<RpcResponse>(){}.type)
    }

    @SerializedName(JSON_RPC_IDENTIFIER)
    val version: String? = null

    val id: Any = NO_ID
    val method: String? = null
    val error: RpcError? = null
    val result: JsonElement? = null
    val params: JsonElement? = null

    val isVoid: Boolean
        get() = hasResult && result == null

    val hasError: Boolean
        get() = error != null

    val hasResult: Boolean
        get() = !hasError && (result != null)

    val hasParams: Boolean
        get() = !hasError && (params != null)


    fun <T> getResult(clazz: Class<T>): T? {
        return if (hasResult) {
            RPC.jsonParser.fromJson(result, clazz)
        } else null
    }

    fun <T> getResult(type: Type): T? {
        return if (hasResult) {
            RPC.jsonParser.fromJson(result, type)
        } else null
    }

    fun <T> getParams(clazz: Class<T>): T? {
        return if (hasParams) {
            RPC.jsonParser.fromJson(params, clazz)
        } else null
    }

    override fun toString() =
            "JsonRPCResponse{$JSON_RPC_IDENTIFIER=$version, $ID_KEY=$id, response=${(
                    if (hasError)
                        error.toString()
                    else
                        RPC.jsonParser.toJson(result)
                    )}}"
}

class BatchedRpcResponse(responsessArr: Array<RpcResponse>) : AbstractResponse() {
    val responses = responsessArr

    companion object {

        fun fromJson(json: BufferedReader) =
                BatchedRpcResponse(RPC.fromJson(json, Array<RpcResponse>::class.java))
//            BatchedRpcResponse(RPC.fromJson(json, object : TypeToken<Array<RpcResponse>>(){}.type))

        fun fromJson(json: String) =
                BatchedRpcResponse(RPC.fromJson(json, Array<RpcResponse>::class.java))
    }

    override fun toString(): String {
        return RPC.jsonParser.toJson(this)
    }
}
