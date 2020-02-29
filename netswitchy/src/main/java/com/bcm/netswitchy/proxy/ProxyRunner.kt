package com.bcm.netswitchy.proxy

import android.annotation.SuppressLint
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.netswitchy.proxy.proxyconfig.OBFS4Params
import com.bcm.netswitchy.proxy.proxyconfig.ProxyParams
import com.bcm.netswitchy.proxy.support.*
import com.bcm.ssrsystem.config.SSParams
import java.lang.ref.WeakReference

class ProxyRunner(private val maybeList: List<ProxyParams>) : IProxyListener {
    companion object {
        private const val TAG = "ProxyRunner"
    }
    private var weakNotify = WeakReference<IProxyRunResult>(null)

    private var lastSucceedProxy: String = ""
    private var tryingProxy: IProxy? = null
    private var workingProxy: IProxy? = null
    private val tryedList = mutableListOf<String>()
    private var runningIndex = -1

    fun setListener(proxyResult: IProxyRunResult?) {
        weakNotify = WeakReference<IProxyRunResult>(proxyResult)
    }

    @SuppressLint("CheckResult")
    fun start() {
        ALog.i(TAG, "start")
        AmeDispatcher.singleScheduler.scheduleDirect {
            stopProxy()
            runningIndex = -1
            if (lastSucceedProxy.isNotEmpty()) {
                val params = maybeList.filter { it.name == lastSucceedProxy }
                        .takeIf { it.isNotEmpty() }?.first()
                if (null != params) {
                    connectProxy(params)
                    return@scheduleDirect
                }
            }
            tryNext()
        }
    }

    private fun tryNext() {
        ALog.i(TAG, "tryNext")
        if (runningIndex < 0) {
            runningIndex = 0
        } else {
            runningIndex += 1
        }

        if (runningIndex < maybeList.size) {
            val params = maybeList[runningIndex]
            if (params.name == lastSucceedProxy) {
                tryNext()
            } else {
                connectProxy(params)
            }
        } else {
            weakNotify.get()?.onProxyRunFailed()
        }
    }

    private fun connectProxy(params:ProxyParams) {
        ALog.i(TAG, "connectProxy")
        val proxy: IProxy = if (params is OBFS4Params) {
            OBFS4Proxy(params.name, AmeDispatcher.singleScheduler)
        } else if(params is SSParams){
            SSRProxy(params.name, AmeDispatcher.singleScheduler)
        } else {
            Socks5Proxy(params.name, AmeDispatcher.singleScheduler)
        }
        this.tryingProxy = proxy

        weakNotify.get()?.onProxyRunTrying(proxy)

        proxy.setListener(this)
        if(!proxy.start(params)) {
            tryNext()
        }
    }

    fun stop() {
        ALog.i(TAG, "stop")
        AmeDispatcher.singleScheduler.scheduleDirect {
            runningIndex = -1
            stopProxy()
        }
    }

    fun runningProxy(): IProxy? {
        ALog.i(TAG, "runningProxy")
        return workingProxy?:tryingProxy
    }

    private fun stopProxy() {
        tryingProxy?.setListener(null)
        tryingProxy?.stop()
        tryingProxy = null

        workingProxy?.setListener(null)
        workingProxy?.stop()
        workingProxy = null
    }

    override fun onProxyStarted(proxy: IProxy) {
        ALog.i(TAG, "onProxyStarted")
        //noting to do
    }

    override fun onProxySucceed(proxy: IProxy) {
        ALog.i(TAG, "onProxySucceed")
        lastSucceedProxy = proxy.name()
        workingProxy = proxy

        weakNotify.get()?.onProxyRunSucceed(proxy)
    }

    override fun onProxyFailed(proxy: IProxy) {
        ALog.i(TAG, "onProxyFailed")
        tryedList.add(proxy.name())
        stopProxy()
        tryNext()
    }

    override fun onProxyStop(proxy: IProxy) {
        ALog.i(TAG, "onProxyStop")
        if (workingProxy == proxy) {
            weakNotify.get()?.onProxyRunStop(proxy)
        }

        if (!tryedList.contains(proxy.name())) {
            stopProxy()
            tryNext()
        }
    }

    interface IProxyRunResult {
        fun onProxyRunSucceed(proxy: IProxy)
        fun onProxyRunTrying(proxy: IProxy)
        fun onProxyRunStop(proxy: IProxy)
        fun onProxyRunFailed()
    }
}