package com.bcm.netswitchy.proxy.proxyconfig

import com.bcm.messenger.utility.logger.ALog
import java.net.URI

class Socks5Params(val authName: String, val pass: String) : ProxyParams(Type.SOCKS5, "", 0) {
    companion object {
        private val TAG = "Socks5Params"

        fun parse(uri: URI): ProxyParams? {
            try {
                val authInfoEncoded = uri.authority
                if (authInfoEncoded.indexOf('@') > 0) {
                    val userInfoParts = uri.userInfo.split(':')
                    val params =  if (userInfoParts.size >= 2) {
                        ALog.w(TAG, "uri $uri format is mistake")
                        Socks5Params(userInfoParts[0], userInfoParts[1])
                    } else {
                        Socks5Params("", "")
                    }

                    params.host = uri.host
                    params.port = uri.port
                    return params
                } else {
                    val newUri = URI("$SCHEME://$authInfoEncoded")
                    val userInfoParts = newUri.userInfo?.split(':')
                    val params = if (null != userInfoParts && userInfoParts?.size?:0 >= 2) {
                        ALog.w(TAG, "uri $uri format is mistake")
                        Socks5Params(userInfoParts[0], userInfoParts[1])
                    } else {
                        Socks5Params("", "")
                    }

                    params.host = newUri.host
                    params.port = newUri.port
                    return params

                }
            } catch (e: Exception) {
                ALog.w(TAG, "uri ${uri}, parse meet exception: ${e.message}")
            }
            return null
        }
        const val SCHEME = "socks5"


    }

    override fun toString(): String {
        val auth = if(authName.isNotEmpty() && pass.isNotEmpty()) {
            "$authName:$pass@"
        } else {
            ""
        }
        return "${SCHEME}://$auth$host:$port"
    }
}