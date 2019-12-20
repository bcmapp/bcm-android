package com.bcm.netswitchy.proxy

interface IProxyStateChanged {
    fun onProxyListChanged() {}

    fun onProxyConnectStarted() {}
    fun onProxyConnecting(proxyName: String, isOfficial: Boolean) {}
    fun onProxyConnectFinished() {}
}