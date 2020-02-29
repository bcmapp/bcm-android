package com.bcm.netswitchy.proxy.support

import com.bcm.messenger.utility.logger.ALog
import com.bcm.netswitchy.HookSystem
import com.bcm.netswitchy.proxy.proxyconfig.ProxyParams
import com.bcm.netswitchy.proxy.proxyconfig.Socks5Params
import io.reactivex.Scheduler

class Socks5Proxy(private val name: String, private val scheduler: Scheduler) : IProxy {
    private var serverPort = 0
    private var listener: IProxyListener? = null
    private var running = false
    private val hookSystem = HookSystem()

    override fun start(params: ProxyParams): Boolean {
        if (params is Socks5Params) {
            stop()
            ALog.i("Socks5Proxy", "running")

            running = true
            hookSystem.proxyHook(params.host, params.port, params.authName, params.pass)

            scheduler.scheduleDirect {
                listener?.onProxyStarted(this)
            }
            return true
        }
        return false
    }

    override fun stop() {
        if (running) {
            ALog.i("Socks5Proxy", "stoped")
            running = false
            hookSystem.proxyUnhook()

            scheduler.scheduleDirect {
                listener?.onProxyStop(this)
            }
        }
    }

    override fun isRunning(): Boolean {
        return running
    }

    override fun setListener(listener: IProxyListener?) {
        this.listener = listener
    }

    override fun serverPort(): Int {
        return serverPort
    }

    override fun name(): String {
        return name
    }
}