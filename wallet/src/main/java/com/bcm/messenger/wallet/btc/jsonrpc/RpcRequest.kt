package com.bcm.messenger.wallet.btc.jsonrpc

import com.google.gson.annotations.SerializedName


class RpcRequestOut(
        @SerializedName(METHOD_KEY)
        val methodName: String,
        @SerializedName(PARAMS_KEY)
        val params: RpcParams = RpcNoParams
) {
    @SerializedName(ID_KEY)
    var id: Any = NO_ID

    @SerializedName(JSON_RPC_IDENTIFIER)
    var version = JSON_RPC_VERSION

    fun toJson(): String = RPC.toJson(this)
}
