package com.bcm.netswitchy.proxy.proxyconfig

import android.util.Base64
import com.bcm.messenger.utility.logger.ALog
import java.net.URI

data class OBFS4Params(
        val cert: String,
        val iatMode: Int
): ProxyParams(Type.OBFS4, "", 0) {

    companion object {
        private const val TAG = "OBFS4Config"
        const val SCHEME = "obfs4"

        // obfs4://base64_urlsafe(cert:iat-mode)@server:port
        fun parse(uri: URI): OBFS4Params? {
            try {
                val userInfo = ProxyParamsParser.base64DecodeToString(uri.userInfo.trim('\n').trim('='))
                val userInfoParts = userInfo.split(':')
                if (userInfoParts.isEmpty()) {
                    ALog.w(TAG, "uri ${uri} format is mistake")
                    return null
                }
                val cert = userInfoParts[0]
                var iatMode = 0
                if (userInfoParts.size >= 2) {
                    iatMode = userInfoParts[1].toInt()
                }

                val params = OBFS4Params(cert, iatMode)
                params.host = uri.host
                params.port = uri.port
                return params
            } catch (e: Exception) {
                ALog.w(TAG, "uri ${uri}, parse meet exception: ${e.message}")
            }
            return null
        }
    }

    override fun toString(): String {
        val encode = base64("${cert}:${iatMode}")
        return "${SCHEME}://${encode}@$host:$port"
    }

}