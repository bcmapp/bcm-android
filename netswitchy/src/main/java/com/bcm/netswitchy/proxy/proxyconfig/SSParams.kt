package com.bcm.ssrsystem.config

import android.util.Base64
import com.bcm.messenger.utility.logger.ALog
import com.bcm.netswitchy.proxy.proxyconfig.ProxyParams
import com.bcm.netswitchy.proxy.proxyconfig.ProxyParamsParser
import java.net.URI

data class SSParams(
        val method: String,
        val password: String
) : ProxyParams(Type.SS, "", 0) {

    companion object {
        private const val TAG = "SSConfig"
        const val SCHEME = "ss"

        // ss://base64_urlsafe(method:password)@server:port
        // ss://base64_urlsafe(method:password@server:port)
        fun parse(uri: URI): SSParams? {
            try {
                val authInfoEncoded = uri.authority
                if (authInfoEncoded.indexOf('@') > 0) {
                    val userInfo = ProxyParamsParser.base64DecodeToString(uri.userInfo)
                    val userInfoParts = userInfo.split(':')
                    if (userInfoParts.size < 2) {
                        ALog.w(TAG, "uri ${uri} format is mistake")
                        return null
                    }

                    val params = SSParams(userInfoParts[0], userInfoParts[1])
                    params.host = uri.host
                    params.port = uri.port
                    return params
                } else {
                    val newUri = URI(SCHEME + ProxyParamsParser.base64DecodeToString(authInfoEncoded))
                    val userInfoParts = newUri.userInfo.split(':')
                    if (userInfoParts.size < 2) {
                        ALog.w(TAG, "uri ${uri} format is mistake")
                        return null
                    }
                    val params = SSParams(userInfoParts[0], userInfoParts[1])
                    params.host = newUri.host
                    params.port = newUri.port
                    return params

                }
            } catch (e: Exception) {
                ALog.w(TAG, "uri ${uri}, parse meet exception: ${e.message}")
            }
            return null
        }
    }

    override fun toString(): String {
        val encode = base64("$method:$password")
        return "${SCHEME}://${encode}@$host:$port"
    }
}