package com.bcm.netswitchy.proxy.proxyconfig

import android.util.Base64
import com.bcm.messenger.utility.logger.ALog
import com.bcm.ssrsystem.config.SSParams
import com.bcm.ssrsystem.config.SSRParams
import java.net.URI

object ProxyParamsParser {
    const val TAG = "ProxyConfigParser"

    fun parse(uri: String): ProxyParams? {
        try {
            val urix = URI(uri.trim('\n').trim('='))
            if (urix.scheme == OBFS4Params.SCHEME) {
                return OBFS4Params.parse(urix)
            } else if (urix.scheme == SSParams.SCHEME) {
                return SSParams.parse(urix)
            } else if (urix.scheme == SSRParams.SCHEME) {
                return SSRParams.parse(urix)
            } else if(urix.scheme == Socks5Params.SCHEME){
                return Socks5Params.parse(urix)
            } else {
                ALog.w(TAG, "unsupport uri: $uri")
            }
        } catch (e: Exception) {
            ALog.w(TAG, "parse meet exception: ${e.message}")
        }
        return null
    }

    internal fun base64DecodeToString(decoding: String): String {
        var newDecoding = decoding.replace('-', '+')
                .replace('_', '/')
                .replace("\n", "")
                .replace("=", "")
                .replace("\r", "")

        val padding = if (newDecoding.length % 4 > 0) {
            4 - newDecoding.length % 4
        } else {
            0
        }
        newDecoding += ("====".substring(0, padding))
        val decoded = Base64.decode(newDecoding, 0)
        return decoded.toString(Charsets.UTF_8).trim('\n')
    }

    internal fun base64Encode(encoding: String): String {
        return String(Base64.encode(encoding.toByteArray(), Base64.URL_SAFE))
                .replace("\n", "")
                .replace("=", "")
                .replace("\r", "")
    }
}