package com.bcm.netswitchy.proxy

import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.netswitchy.proxy.proxyconfig.OBFS4Params
import com.bcm.netswitchy.proxy.proxyconfig.ProxyParams
import com.bcm.netswitchy.proxy.support.*
import com.bcm.ssrsystem.config.SSParams
import java.lang.ref.WeakReference
import java.util.*

class ProxyTester(private val testList: List<ProxyParams>) : IProxyListener {
    private var weakNotify = WeakReference<IProxyTesterResult>(null)
    private var testingProxy:IProxy? = null

    private var succeedList = Collections.synchronizedList(mutableListOf<String>())
    private var failedList = Collections.synchronizedList(mutableListOf<String>())

    private var testingIndex = -1

    fun setListener(proxyResult: IProxyTesterResult?) {
        weakNotify = WeakReference<IProxyTesterResult>(proxyResult)
    }

    fun startTest() {
        AmeDispatcher.singleScheduler.scheduleDirect {
            stopProxy()
            testingIndex = -1

            failedList.clear()
            succeedList.clear()
            tryNextTest()
        }
    }

    private fun tryNextTest() {
        if (testingIndex < 0) {
            testingIndex = 0
        } else {
            testingIndex += 1
        }

        if (testingIndex < testList.size) {
            val params = testList[testingIndex]
            val proxy: IProxy = if (params is OBFS4Params) {
                OBFS4Proxy(params.name, AmeDispatcher.singleScheduler)
            } else if(params is SSParams){
                SSRProxy(params.name, AmeDispatcher.singleScheduler)
            } else {
                Socks5Proxy(params.name, AmeDispatcher.singleScheduler)
            }
            this.testingProxy = proxy

            weakNotify.get()?.onProxyTestStart(proxy.name())

            proxy.setListener(this)
            proxy.start(params)
        } else {
            weakNotify.get()?.onProxyTestFinished(succeedList, failedList)
        }
    }

    fun stopTest() {
        AmeDispatcher.singleScheduler.scheduleDirect {
            testingIndex = -1
            stopProxy()
        }
    }

    fun getTestingProxy(): IProxy? {
        return testingProxy
    }

    fun isTesting(): Boolean {
        return testingIndex >= 0
    }

    private fun stopProxy() {
        testingProxy?.setListener(null)
        testingProxy?.stop()
        testingProxy = null
    }

    override fun onProxyStarted(proxy: IProxy) {
        //nothing to do
    }

    override fun onProxySucceed(proxy: IProxy) {
        stopProxy()
        succeedList.add(proxy.name())

        weakNotify.get()?.onProxyTestResultChanged(succeedList.toList(), failedList.toList())

        tryNextTest()
    }

    override fun onProxyFailed(proxy: IProxy) {
        stopProxy()
        failedList.add(proxy.name())
        weakNotify.get()?.onProxyTestResultChanged(succeedList.toList(), failedList.toList())
        tryNextTest()
    }

    override fun onProxyStop(proxy: IProxy) {
        stopProxy()

        this.testingProxy = null
        if (succeedList.contains(proxy.name()) || failedList.contains(proxy.name())) {
            return
        }
        failedList.add(proxy.name())
        weakNotify.get()?.onProxyTestResultChanged(succeedList, failedList)

        tryNextTest()
    }

    interface IProxyTesterResult {
        fun onProxyTestStart(proxyName:String)
        fun onProxyTestResultChanged(succeedList: List<String>, failedList: List<String>)
        fun onProxyTestFinished(succeedList: List<String>, failedList: List<String>)
    }
}