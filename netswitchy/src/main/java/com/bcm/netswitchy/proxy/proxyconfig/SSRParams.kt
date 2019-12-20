package com.bcm.ssrsystem.config

import android.util.Base64
import com.bcm.messenger.utility.logger.ALog
import com.bcm.netswitchy.proxy.proxyconfig.ProxyParams
import com.bcm.netswitchy.proxy.proxyconfig.ProxyParamsParser
import java.lang.StringBuilder
import java.net.URI

data class SSRParams(
        val protocol: String,
        val method: String,
        val obfs: String,
        val password: String,
        var obfsParams: String = "",
        var protocolParams: String = "",
        var group: String = "",
        var remarks: String = ""
) : ProxyParams(Type.SSR, "", 0 ) {

    companion object {
        private const val TAG = "SSRConfig"
        const val SCHEME = "ssr"

        // ssr://base64_urlsafe(server:port:protocol:method:obfs:base64_urlsafe(password)/
        // ?obfsparam=base64_urlsafe(obfs_param)&protoparam=base64_urlsafe(protocol_param)
        // &remarks=base64_urlsafe(remarks)&group=base64_urlsafe(group))
        fun parse(uri: URI): SSRParams? {
            try {
                val newURI = URI(SCHEME + "://" + ProxyParamsParser.base64DecodeToString(uri.authority))
                val userInfoParts = newURI.authority.split(':')
                if (userInfoParts.size < 6) {
                    ALog.w(TAG, "uri ${uri} format is mistake")
                    return null
                }

                val host = userInfoParts[0]
                val port = userInfoParts[1].toInt()
                val protocol = userInfoParts[2]
                val method = userInfoParts[3]
                val obfs = userInfoParts[4]
                val password = ProxyParamsParser.base64DecodeToString(userInfoParts[5])

                val params = SSRParams(protocol, method, obfs, password)
                params.host = host
                params.port = port

                if (!uri.query.isNullOrEmpty()) {
                    val queryParts = uri.query.split('&')
                    for (part in queryParts) {
                        val kvpair = part.split('=')
                        if (kvpair.size < 2 || kvpair[1].isEmpty()) {
                            continue
                        }

                        val decodedParam = ProxyParamsParser.base64DecodeToString(kvpair[1])
                        if (kvpair[0] == "obfsparam") {
                            params.obfsParams = decodedParam
                        } else if (kvpair[0] == "protoparam") {
                            params.protocolParams = decodedParam
                        } else if (kvpair[0] == "group") {
                            params.group = decodedParam
                        } else if (kvpair[0] == "remarks") {
                            params.remarks = decodedParam
                        }
                    }
                }

                return params
            } catch (e: Exception) {
                ALog.w(TAG, "uri ${uri}, parse meet exception: ${e.message}")
            }
            return null
        }
    }

    override fun toString(): String {
        val uriBuilder = StringBuilder("${host}:$port")
        uriBuilder.append(":").append(protocol)
        uriBuilder.append(":").append(method)
        uriBuilder.append(":").append(obfs)
        uriBuilder.append(":").append(base64(password))

        val paramSep = "/?"
        if (protocolParams.isNotEmpty()) {
            uriBuilder.append(paramSep).append("protocol_param=").append(base64(protocolParams))
        }

        if (obfsParams.isNotEmpty()) {
            uriBuilder.append(paramSep).append("obfsparam=").append(base64(obfsParams))
        }

        if (remarks.isNotEmpty()) {
            uriBuilder.append(paramSep).append("remarks=").append(base64(remarks))
        }

        if (group.isNotEmpty()) {
            uriBuilder.append(paramSep).append("group=").append(base64(group))
        }

        val encode = base64(uriBuilder.toString())
        return "${SCHEME}://${encode}"
    }
}