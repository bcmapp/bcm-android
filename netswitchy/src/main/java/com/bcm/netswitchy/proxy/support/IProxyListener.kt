package com.bcm.netswitchy.proxy.support

interface IProxyListener {
    fun onProxyStarted(proxy: IProxy)
    fun onProxySucceed(proxy: IProxy)
    fun onProxyFailed(proxy: IProxy)
    fun onProxyStop(proxy: IProxy)
}