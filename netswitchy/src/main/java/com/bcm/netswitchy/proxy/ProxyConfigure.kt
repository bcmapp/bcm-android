package com.bcm.netswitchy.proxy

import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.storage.SPEditor

object ProxyConfigure {
    private const val TAG = "ProxyConfigure"
    private const val PROXY_ENABLE = "proxy_enable"
    private const val PROXY_SELECTED = "proxy_selected"
    private val proxyEditor = SPEditor("server_config")

    var isEnable: Boolean = false
        set(value) {
            field = value
            proxyEditor.set(PROXY_ENABLE, value)
        }

    var current: String = ""
        set(value) {
            field = value
            proxyEditor.set(PROXY_SELECTED, value)
        }

    init {
        isEnable = proxyEditor.get(PROXY_ENABLE, false)
        current = proxyEditor.get(PROXY_SELECTED, "")
    }

    fun isRunning(): Boolean {
        return isEnable && current.isNotEmpty()
    }

    fun checkProxy() {
        ALog.i(TAG, "checkProxy $isEnable")
        if (isEnable) {
            ALog.i(TAG, "checkProxy $current")
            if(current.isNotEmpty()) {
                if(null == ProxyManager.getProxyByName(current)) {
                    current = ""
                }
            }
            ALog.i(TAG, "checkProxy1 $current")

            if (current.isEmpty() && ProxyManager.getProxyList().isNotEmpty()) {
                current = ProxyManager.getProxyList().first().params.name
            }
            ALog.i(TAG, "checkProxy2 $current")
            if (current.isNotEmpty()) {
                if (ProxyManager.getRunningProxyName() != current) {
                    ProxyManager.stopProxy()
                } else if (ProxyManager.isProxyRunning()) {
                    ALog.i(TAG, "checkProxy3 already running")
                    return
                }
                ALog.i(TAG, "checkProxy4 startProxy")
                ProxyManager.startProxy(current)
            }
        } else {
            if (ProxyManager.isProxyRunning()) {
                ProxyManager.stopProxy()
            }
        }
    }
}