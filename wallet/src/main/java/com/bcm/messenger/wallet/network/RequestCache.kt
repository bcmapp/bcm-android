package com.bcm.messenger.wallet.network

import java.util.*

/**
 * @author Aaron
 * @email aaron@magicwindow.cn
 * @date 08/03/2018 14:35
 * @description
 */
class RequestCache {

    private val map = HashMap<String, String>()

    fun put(type: String, address: String, response: String) {
        map[type + address] = response
    }

    operator fun get(type: String, address: String): String? {
        return map[type + address]
    }

    fun contains(type: String, address: String): Boolean {
        return map.containsKey(type + address)
    }

    companion object {

        val TYPE_TOKEN = "TOKEN_"
        val TYPE_TXS_NORMAL = "TXS_NORMAL_"
        val TYPE_TXS_INTERNAL = "TXS_INTERNAL_"
        val TYPE_BALANCES = "BALANCES_"

        val instance: RequestCache by lazy { RequestCache() }

    }


}
