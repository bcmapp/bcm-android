package com.bcm.netswitchy.proxy.proxyconfig

open class ProxyParams(cType: Type, var host: String, var port: Int, var localPort: Int = 1080, var name: String = "") {
    enum class Type { OBFS4, SS, SSR,SOCKS5 }

    var type: Type = cType

    protected fun base64(encoding: String): String {
        return ProxyParamsParser.base64Encode(encoding)
    }
}