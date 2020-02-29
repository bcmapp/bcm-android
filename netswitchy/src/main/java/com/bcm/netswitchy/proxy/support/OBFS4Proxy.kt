package com.bcm.netswitchy.proxy.support

import android.annotation.SuppressLint
import android.os.Build
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.logger.AmeLogConfig
import com.bcm.netswitchy.HookSystem
import com.bcm.netswitchy.proxy.ProxyManager
import com.bcm.netswitchy.proxy.proxyconfig.OBFS4Params
import com.bcm.netswitchy.proxy.proxyconfig.ProxyParams
import com.bcm.netswitchy.utils.Executable
import com.bcm.netswitchy.utils.GenPort
import com.bcm.netswitchy.utils.ProcessLauncher
import io.reactivex.Scheduler
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

class OBFS4Proxy(private val name:String, private val scheduler: Scheduler): IProxy, ProcessLauncher.IProcessLauncherListener {
    companion object {
        private const val TAG = "OBFS4Proxy"
        private const val LOCAL_PORT = 12019
        private const val LOCAL_HOST = "127.0.0.1"
    }

    private var running = false
    private val processList = mutableListOf<ProcessLauncher>()
    private var listener: IProxyListener? = null
    private var serverPort:Int = LOCAL_PORT

    private val hookSystem = HookSystem()

    override fun name(): String {
        return this.name
    }

    override fun setListener(listener: IProxyListener?) {
        this.listener = listener
    }

    override fun serverPort(): Int {
        return this.serverPort
    }

    override fun start(params: ProxyParams): Boolean {
        ALog.i(TAG, "start")

        if (Build.VERSION.SDK_INT >= 29) {
            return false
        }

        if (running) {
            stopProcesses()
            processList.clear()
        }

        running = true
        if (params is OBFS4Params) {
            serverPort = GenPort.getPort(LOCAL_HOST, LOCAL_PORT)
            if (serverPort == 0) {
                ALog.i(TAG, "start port 0")
                return false
            }

            val cmdParams = arrayListOf(
                    "-client",
                    "-state",
                    "${AmeLogConfig.logDir}",
                    "-bind",
                    "$LOCAL_HOST:$serverPort",
                    "-relay",
                    "${params.host}:${params.port}",
                    "-log",
                    "DEBUG",
                    "-cert",
                    params.cert,
                    "-iat-mode",
                    "${params.iatMode}"
            )

            val binPath = Executable.getCmdPath(path = Executable.OBSF4, isNative = true)
            processList.add(ProcessLauncher(params.name, binPath, cmdParams))

            processList.forEach {
                it.setListener(this)
                it.start()
            }

            return true
        }
        ALog.e(TAG, "start failed")
        return false
    }

    override fun onProcessStarted(launcher: ProcessLauncher) {
        ALog.i(TAG, "onProcessStarted")
        val wSelf = WeakReference(this)
        scheduler.scheduleDirect {
            listener?.onProxyStarted(this)
        }

        hookSystem.proxyHook(LOCAL_HOST, serverPort)
        checkBCMConnect(1000) {
            val proxy = wSelf.get()?:return@checkBCMConnect
            ALog.i(TAG, "checkBCMConnect $it")
            if (it) {
                proxy.listener?.onProxySucceed(proxy)
            } else {
                stopProcesses()
                proxy.listener?.onProxyFailed(proxy)
            }
        }
    }

    override fun onProcessStop(launcher: ProcessLauncher) {
        ALog.i(TAG, "onProcessStop")
        stopProcesses()

        scheduler.scheduleDirect {
            running = false
            listener?.onProxyStop(this)
        }
    }

    @SuppressLint("CheckResult")
    private fun checkBCMConnect(delay:Long, result:(succeed:Boolean)->Unit) {
        ALog.i(TAG, "checkBCMConnect delay:$delay")
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
                    result(false)
                }
                .subscribe {
                    result(it)
                }
    }

    override fun stop() {
        ALog.i(TAG, "stop")
        stopProcesses()
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
    }

    override fun isRunning(): Boolean {
        return running
    }
}