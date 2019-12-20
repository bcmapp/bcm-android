package com.bcm.netswitchy.proxy.support

import android.annotation.SuppressLint
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.netswitchy.HookSystem
import com.bcm.netswitchy.proxy.ProxyManager
import com.bcm.netswitchy.proxy.proxyconfig.ProxyParams
import com.bcm.netswitchy.utils.*
import com.bcm.ssrsystem.config.SSParams
import com.bcm.ssrsystem.config.SSRParams
import io.reactivex.Scheduler
import java.io.File
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.TimeUnit

class SSRProxy(private val name: String, private val scheduler: Scheduler) : IProxy, ProcessLauncher.IProcessLauncherListener {
    companion object {
        private const val TAG = "SSRProxy"
        private const val REMOTE_DNS = "208.67.222.222:53"
        private const val CHINA_DNS = "114.114.114.114:53,223.5.5.5:53"

        private const val LOCAL_SSR_HOST = "127.0.0.1"
        private const val LOCAL_SSR_PORT = 1080

        private const val DNS_HOST = "208.67.222.222"
        private const val DNS_PORT = "53"

    }

    private val processList = Collections.synchronizedList(mutableListOf<ProcessLauncher>())
    private var running = false
    private var serverPort = LOCAL_SSR_PORT

    private var listener: IProxyListener? = null
    private val hookSystem = HookSystem()

    private val processReadyList = mutableSetOf<String>()

    override fun setListener(listener: IProxyListener?) {
        this.listener = listener
    }

    override fun name(): String {
        return this.name
    }

    override fun serverPort(): Int {
        return this.serverPort
    }

    override fun start(params: ProxyParams): Boolean {
        ALog.i(TAG, "start")
        if (running) {
            stopProcesses()
            processList.clear()
        }

        running = true
        if (params is SSRParams || params is SSParams) {
            params.localPort = GenPort.getSSRPort(LOCAL_SSR_HOST, params.localPort)
            if (params.localPort == 0) {
                ALog.e(TAG, "start port 0")
                return false
            }

            serverPort = params.localPort

            params.host = IPAddressUtil.resolve(params.host)

            updateSocks5ServerParams(params, false)
            updateSSRClientParams(params, false)

            //updateDnsDaemonParams(params, false)
            //updateRedSocksParams(params, false)

            processList.forEach {
                it.setListener(this)
                it.start()
            }
            return true
        }

        ALog.e(TAG, "start failed")
        return false
    }

    override fun stop() {
        ALog.i(TAG, "stop")
        stopProcesses()
        return
    }

    override fun isRunning(): Boolean {
        return running
    }

    private fun stopProcesses() {
        ALog.i(TAG, "stopProcesses")
        if (running) {
            hookSystem.proxyUnhook()
        }
        running = false
        processList.forEach {
            it.setListener(null)
            it.stop()
        }
        processReadyList.clear()
    }

    override fun onProcessStarted(launcher: ProcessLauncher) {
        val wSelf = WeakReference(this)
        scheduler.scheduleDirect {
            processReadyList.add(launcher.name)
            if (processReadyList.size == processList.size) {
                ALog.i(TAG, "onProcessStarted")
                scheduler.scheduleDirect {
                    listener?.onProxyStarted(this)
                }

                hookSystem.proxyHook(LOCAL_SSR_HOST, serverPort)

                checkBCMConnect(1000) {
                    ALog.i(TAG, "checkBCMConnect $it")
                    val proxy = wSelf.get() ?: return@checkBCMConnect
                    if (it) {
                        proxy.listener?.onProxySucceed(proxy)
                    } else {
                        stopProcesses()
                        proxy.listener?.onProxyFailed(proxy)
                    }
                }
            }
        }

    }

    override fun onProcessStop(launcher: ProcessLauncher) {
        ALog.i(TAG, "onProcessStop")
        hookSystem.proxyUnhook()

        if (processList.indexOf(launcher) >= 0) {
            stopProcesses()
        }

        scheduler.scheduleDirect {
            listener?.onProxyStop(this)
        }
    }

    @SuppressLint("CheckResult")
    private fun checkBCMConnect(delay: Long, result: (succeed: Boolean) -> Unit) {
        ALog.i(TAG, "checkBCMConnect")
        val checker = ProxyManager.connectionChecker
        if (null == checker) {
            ALog.w(TAG, "checkBCMConnect failed")
            result(true)
            return
        }

        checker.check(delay, AmeDispatcher.ioScheduler)
                .delaySubscription(delay, TimeUnit.MILLISECONDS, AmeDispatcher.ioScheduler)
                .subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(scheduler)
                .doOnError {
                    ALog.i(TAG, "checkBCMConnect failed")
                    result(false)
                }
                .subscribe {
                    ALog.i(TAG, "checkBCMConnect failed and retry")
                    result(it)
                }
    }

    private fun updateSocks5ServerParams(params: ProxyParams, udpDns: Boolean) {
        val localServerPort = if (udpDns) {
            params.localPort + 53
        } else {
            params.localPort + 63
        }

        val configJson = if (params is SSRParams) {
            ConfigureUtils.buildTunnelConfig(params, localServerPort)
        } else {
            ConfigureUtils.buildSSTunnelConfig(params as SSParams, localServerPort)
        }

        ALog.d(TAG, "startTunnel config: $configJson")
        val tunnelConf = Executable.getLocalTunnelConf()
        File(tunnelConf).apply {
            delete()
            writeText(configJson)
        }

        val dns = "$DNS_HOST:$DNS_PORT"

        val cmdParams = arrayListOf(
                //"-V", "-v",
                "-u", "-t", "60",
                "--host", params.host, "-b", LOCAL_SSR_HOST,
                "-l", localServerPort.toString(),
                "-P", Executable.getCmdDir(), "-c", tunnelConf, "-L", dns
        )

        val binPath = Executable.getCmdPath(path = Executable.SS_LOCAL, isNative = true)
        processList.add(ProcessLauncher(params.name + "_socks5", binPath, cmdParams))
    }

    private fun updateSSRClientParams(params: ProxyParams, udpDns: Boolean) {
        val configJson = if (params is SSRParams) {
            ConfigureUtils.buildSSRConfig(params)
        } else {
            ConfigureUtils.buildSSConfig(params as SSParams)
        }
        ALog.d(TAG, "startSSRDaemon config: $configJson")
        File(Executable.getLocalVpnConf()).apply {
            delete()
            writeText(configJson)
        }

        val cmdParams = arrayListOf(
//                "-V",
                "-v",
                "-x", "-b", LOCAL_SSR_HOST, "-t", "600",
                "--host", params.host, "-P", Executable.getCmdDir(), "-c", Executable.getLocalVpnConf()
        )

        val binPath = Executable.getCmdPath(path = Executable.SS_LOCAL, isNative = true)
        processList.add(ProcessLauncher(params.name + "_client", binPath, cmdParams))
    }

//    private fun updateDnsDaemonParams(params: ProxyParams, udpDns:Boolean) {
//        val pdnAddress = LOCAL_SSR_HOST
//        val dnsGlobalPort = params.localPort + 53
//        val dnsServerPort = params.localPort + 63
//
//        val conf = ConfigureUtils.PDNSD_LOCAL.format(Locale.ENGLISH, "", Executable.getCmdDir(),
//                pdnAddress, dnsGlobalPort, dnsServerPort, "224.0.0.0/3, ::/0")
//
//
//        ALog.d(TAG, "startDnsDaemon config: $conf")
//        File(Executable.getPdnsdConf()).apply {
//            delete()
//            writeText(conf)
//        }
//
//        val cmdParams = arrayListOf("-c", Executable.getPdnsdConf())
//
//        val binPath = Executable.getCmdPath(path = Executable.PDNSD, isNative = true)
//        processList.add(ProcessLauncher(binPath, cmdParams))
//    }

//    private fun updateRedSocksParams(params: ProxyParams, udpDns:Boolean) {
//        val configJson = ConfigureUtils.buildRedsocksConfig(params)
//        ALog.d(TAG, "buildRedSocksConfig config: $configJson")
//        val redsocksConf = Executable.getRedsocksConf()
//        File(redsocksConf).apply {
//            delete()
//            writeText(configJson)
//        }
//
//        val binPath = Executable.getCmdPath(path = Executable.REDSOCKS, isNative = true)
//        val cmd = arrayListOf("-c", redsocksConf)
//        processList.add(ProcessLauncher(binPath, cmd))
//    }
}