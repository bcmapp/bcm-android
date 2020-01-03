package com.bcm.netswitchy.proxy

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Base64
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.listener.SafeWeakListeners
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.network.INetworkConnectionListener
import com.bcm.messenger.utility.network.NetworkUtil
import com.bcm.messenger.utility.storage.SPEditor
import com.bcm.netswitchy.configure.AmeConfigure
import com.bcm.netswitchy.proxy.proxyconfig.ProxyParams
import com.bcm.netswitchy.proxy.proxyconfig.ProxyParamsParser
import com.bcm.netswitchy.proxy.support.IConnectionChecker
import com.bcm.netswitchy.proxy.support.IProxy
import com.bcm.ssrsystem.config.SSParams
import com.bcm.ssrsystem.config.SSRParams
import io.reactivex.android.schedulers.AndroidSchedulers
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.concurrent.ConcurrentHashMap

object ProxyManager : INetworkConnectionListener, ProxyRunner.IProxyRunResult, ProxyTester.IProxyTesterResult {
    private const val TAG = "ProxyManager"
    private const val PROXY_OFFICIAL = "official_proxy_list"
    private const val PROXY_CUSTOM = "custom_proxy_list"

    private val proxyEditor = SPEditor("bcm_proxy_config")
    private val officialList = ConcurrentHashMap<String, ProxyItem>()
    private val customList = ConcurrentHashMap<String, ProxyItem>()

    internal var connectionChecker:IConnectionChecker? = null

    private var proxyRunner: ProxyRunner? = null
    private var proxyTester: ProxyTester? = null

    private var lastNetWork = ""
    private var isReady = false

    private var weakNotify = SafeWeakListeners<IProxyStateChanged>()

    init {
        AmeDispatcher.io.dispatch {
            loadProxy()
        }
        NetworkUtil.addListener(this)
    }

    fun refresh() {
        ALog.i(TAG, "refresh")
        updateOfficialProxyConfig()

        loadProxy()
    }

    fun setListener(proxyStateChanged: IProxyStateChanged) {
        this.weakNotify.addListener(proxyStateChanged)
    }

    fun setConnectionChecker(checker: IConnectionChecker) {
        connectionChecker = checker
    }

    fun getProxyList(): List<ProxyItem> {
        return customList.values.toList()
    }

    fun addProxy(proxy: ProxyItem, isOfficial: Boolean = false) {
        ALog.i(TAG, "addProxy")
        val list = if (isOfficial) {
            officialList
        } else {
            customList
        }

        if (!isOfficial) {
            val found = getProxyItemByContent(proxy.content)
            if (null != found) {
                if (proxy.params.name.isNotEmpty() && found.params.name != proxy.params.name) {
                    removeProxy(found.params.name)
                    found.params.name = proxy.params.name
                    customList[proxy.params.name] = found

                    notifyListChanged()
                    saveCustomProxy()
                }
                ALog.i(TAG, "addProxy update name")
                return
            }
        }

        if (proxy.params.name.isEmpty()) {
            proxy.params.name = when (proxy.params) {
                is SSRParams -> "SSR"
                is SSParams -> "SS"
                else -> {
                    "OBFS4"
                }
            }
        }

        val storeName = if (!list.containsKey(proxy.params.name) || isOfficial) {
            proxy.params.name
        } else {
            var n = proxy.params.name
            for (i in 1..1000) {
                if (!customList.containsKey("${proxy.params.name}_$i")) {
                    n = "${proxy.params.name}_$i"
                    break
                }
            }

            if (n == proxy.params.name) {
                return
            }
            n
        }

        proxy.status = list[proxy.params.name]?.status ?: ProxyItem.Status.UNKNOWN
        proxy.params.name = storeName
        list[storeName] = proxy

        if (!isOfficial) {
            ALog.i(TAG, "addProxy new proxy added")

            stopTester()

            notifyListChanged()
            saveCustomProxy()
        } else {
            saveOfficialProxy()
        }
    }

    fun getProxyByName(name: String): ProxyItem? {
        return customList[name]
    }

    private fun getProxyItemByContent(content: String): ProxyItem? {
        return customList.values
                .filter { it.content == content }
                .takeIf {
                    it.isNotEmpty()
                }?.first()
    }

    fun removeProxy(name: String) {
        if (null != customList.remove(name)) {
            ALog.i(TAG, "removeProxy removed")
            stopTester()
            notifyListChanged()
            saveCustomProxy()
        }
    }

    fun isReady(): Boolean {

        ALog.i(TAG, "isReady $isReady")

        return this.isReady
    }

    fun hasCustomProxy(): Boolean {
        return customList.isNotEmpty()
    }

    @SuppressLint("CheckResult")
    fun checkConnectionState(result: (connected: Boolean) -> Unit) {
        val checker = connectionChecker.takeIf {
            if (it == null) {
                result(true)
            }
            true
        }?:return

        checker.check(0, AmeDispatcher.ioScheduler)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError {
                    ALog.e(TAG, "checkConnectionState", it)
                    result(false)
                }
                .subscribe {
                    ALog.i(TAG, "checkConnectionState $it")
                    result(it)
                }
    }

    fun startProxy() {
        ALog.i(TAG, "startProxy ready:${isReady()}")
        if (!isReady()) {
            weakNotify.forEach {
                it.onProxyConnectFinished()
            }
            return
        }

        if (officialList.isEmpty() && customList.isEmpty()) {
            weakNotify.forEach {
                it.onProxyConnectFinished()
            }
            return
        }

        weakNotify.forEach {
            it.onProxyConnectStarted()
        }

        var runner = proxyRunner
        if (null == runner) {
            val list = mutableListOf<ProxyParams>()
            list.addAll(customList.map { it.value.params })
            list.addAll(officialList.map { it.value.params })
            runner = ProxyRunner(list)
            runner.setListener(this)
            proxyRunner = runner
        }

        runner.start()
    }

    fun stopProxy() {
        ALog.i(TAG, "stopProxy")
        proxyRunner?.setListener(null)
        proxyRunner?.stop()
        proxyRunner = null
    }

    private fun restoreProxy() {
        ALog.i(TAG, "restoreProxy")
        proxyRunner?.stop()
    }

    private fun resumeProxy() {
        ALog.i(TAG, "resumeProxy")
        proxyRunner?.start()
    }

    fun startTester() {
        ALog.i(TAG, "startTester ${customList.size}")
        if (customList.isNotEmpty()) {
            restoreProxy()

            var tester = this.proxyTester
            if (null == tester) {
                tester = ProxyTester(customList.values.map { it.params })
                tester.setListener(this)
                this.proxyTester = tester
            }

            tester.startTest()
        }
    }

    fun stopTester() {
        ALog.i(TAG, "stopTester")
        val testing = proxyTester?.isTesting() == true
        if (proxyTester != null) {
            proxyTester?.setListener(null)
            proxyTester?.stopTest()
            proxyTester = null

            if (testing) {
                resumeProxy()
            }
        }
    }

    fun isTesting(): Boolean {
        return proxyTester?.isTesting() ?: false
    }

    fun isProxyRunning(): Boolean {
        return proxyRunner?.runningProxy()?.isRunning() == true
    }

    fun getRunningProxyTitle(): String {
        val proxy = proxyRunner?.runningProxy()?:return ""
        val paramItem = officialList[proxy.name()]?: customList[proxy.name()]
        if (null != paramItem) {
            val uri = Uri.parse(paramItem.content)
            return "${uri.scheme}_${paramItem.params.name}_${paramItem.params.host}:${paramItem.params.port}"
        }
        return ""
    }

    fun getRunningProxyName(): String {
        val proxy = proxyRunner?.runningProxy()?:return ""
        val paramItem = customList[proxy.name()]?:officialList[proxy.name()]
        return paramItem?.params?.name?:""
    }

    fun getTestingProxyName(): String {
        return proxyTester?.getTestingProxy()?.name()?:""
    }

    override fun onProxyTestFinished(succeedList: List<String>, failedList: List<String>) {
        ALog.i(TAG, "onProxyTestFinished succeed:${succeedList.size}, failed:${failedList.size}")
        customList.forEach {
            if (it.value.status == ProxyItem.Status.TESTING) {
                it.value.status = ProxyItem.Status.UNKNOWN
            }
        }
        stopTester()
        notifyListChanged()
    }

    override fun onProxyTestResultChanged(succeedList: List<String>, failedList: List<String>) {
        ALog.i(TAG, "onProxyTestResultChanged succeed:${succeedList.size}, failed:${failedList.size}")
        for (s in succeedList) {
            customList[s]?.status = ProxyItem.Status.USABLE
        }

        for (f in failedList) {
            customList[f]?.status = ProxyItem.Status.UNUSABLE
        }

        notifyListChanged()
    }

    override fun onProxyTestStart(proxyName: String) {
        customList[proxyName]?.status = ProxyItem.Status.TESTING
        notifyListChanged()
    }

    private fun notifyListChanged() {
        AmeDispatcher.mainThread.dispatch {
            weakNotify.forEach {
                it.onProxyListChanged()
            }
        }
    }

    override fun onProxyRunSucceed(proxy: IProxy) {
        ALog.i(TAG, "onProxyRunSucceed ${proxy.name()}")
        AmeDispatcher.mainThread.dispatch {
            weakNotify.forEach {
                it.onProxyConnectFinished()
            }
        }
    }

    override fun onProxyRunTrying(proxy: IProxy) {
        ALog.i(TAG, "onProxyRunTrying")
        AmeDispatcher.mainThread.dispatch {
            weakNotify.forEach {
                it.onProxyConnecting(proxy.name(), customList.containsKey(proxy.name()))
            }
        }
    }

    override fun onProxyRunStop(proxy: IProxy) {
        ALog.i(TAG, "onProxyRunStop")
        proxyRunner = null
    }

    override fun onProxyRunFailed() {
        ALog.i(TAG, "onProxyRunFailed")
        proxyRunner = null
        AmeDispatcher.mainThread.dispatch {
            weakNotify.forEach {
                it.onProxyConnectFinished()
            }
        }
    }

    @SuppressLint("CheckResult")
    override fun onNetWorkStateChanged() {
        ALog.i(TAG, "onNetWorkStateChanged")
        if (lastNetWork != NetworkUtil.netType().typeName) {
            lastNetWork = NetworkUtil.netType().typeName
            if (!NetworkUtil.isConnected()) {
                return
            }

            updateOfficialProxyConfig()
        }
    }

    @SuppressLint("CheckResult")
    private fun updateOfficialProxyConfig() {
        ALog.i(TAG, "updateOfficialProxyConfig")
        AmeConfigure.queryProxy()
                .subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AmeDispatcher.ioScheduler)
                .doOnError {
                    isReady = true
                    ALog.e(TAG, "queryProxy failed", it)
                }
                .subscribe {
                    isReady = true
                    val configs = it.split(',')
                    val items = configs.mapNotNull { c ->
                        val param = ProxyParamsParser.parse(c)
                        if (null != param) {
                            ProxyItem(param, c)
                        } else {
                            null
                        }
                    }
                    if (items.isNotEmpty()) {
                        replaceOfficialProxy(items)
                    } else {
                        ALog.w(TAG, "queryProxy no proxy found")
                    }
                }
    }


    private fun replaceOfficialProxy(params: List<ProxyItem>) {
        ALog.i(TAG, "replaceOfficialProxy proxy size:${params.size}")

        if (params.isEmpty()) {
            return
        }

        officialList.clear()
        for (i in params) {
            if (i.params.name.isEmpty()) {
                i.params.name = md5(i.content)
            }

            ALog.d(TAG, "proxy:${i.content}")
            i.status = officialList[i.params.name]?.status ?: ProxyItem.Status.UNKNOWN
            officialList[i.params.name] = i
        }
        saveOfficialProxy()
    }

    private fun saveOfficialProxy() {
        if (officialList.isNotEmpty()) {
            proxyEditor.set(PROXY_OFFICIAL, officialList.map { it.value.content }.toMutableSet())
        } else {
            proxyEditor.remove(PROXY_OFFICIAL)
        }
    }

    private fun saveCustomProxy() {
        if (customList.isNotEmpty()) {
            proxyEditor.set(PROXY_CUSTOM, customList.map { "${it.value.params.name}\r${it.value.content}" }.toMutableSet())
        } else {
            proxyEditor.remove(PROXY_CUSTOM)
        }
    }

    private fun loadProxy() {
        val officialList = proxyEditor.get(PROXY_OFFICIAL, mutableSetOf())
        val paramsList = officialList.mapNotNull {
            val param = ProxyParamsParser.parse(it)
            if (null != param) {
                ProxyItem(param, it)
            } else {
                null
            }
        }

        if (paramsList.isNotEmpty()) {
            replaceOfficialProxy(paramsList)
        }

        val customStringList = proxyEditor.get(PROXY_CUSTOM, mutableSetOf())
        customStringList.forEach {
            val split = it.split('\r')
            if (split.size == 2) {
                val param = ProxyParamsParser.parse(split[1])
                param?.name = split[0]

                if (null != param) {
                    customList[split[0]] = ProxyItem(param, split[1], ProxyItem.Status.UNKNOWN)
                }
            }
        }

        if (!isReady) {
            isReady = officialList.isNotEmpty() || customStringList.isNotEmpty()
        }
    }

    private fun md5(data: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            md.update(data.toByteArray())
            String(Base64.encode(md.digest(), Base64.NO_PADDING))
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            ""
        }
    }
}