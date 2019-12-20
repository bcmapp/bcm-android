package com.bcm.netswitchy.proxy.support

import com.bcm.netswitchy.proxy.proxyconfig.ProxyParams

interface IProxy {
    fun start(params: ProxyParams): Boolean
    fun stop()
    fun isRunning(): Boolean
    fun setListener(listener: IProxyListener?)
    fun serverPort(): Int
    fun name(): String
}