package com.bcm.netswitchy.utils

import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.logger.ALog
import java.io.IOException

class ProcessLauncher(val name:String, private val binPath: String, private val params: List<String>) {
    companion object {
        private const val TAG = "ProcessLauncher"
    }

    private var trackThread: Thread? = null
    private var runningProcess: Process? = null
    private var listener: IProcessLauncherListener? = null
    private var isRunning = false

    fun setListener(listener: IProcessLauncherListener?) {
        this.listener = listener
    }

    fun start() {
        val workingThread = this.trackThread
        this.trackThread = null
        val trackThread = Thread {
            runTrack()
        }
        this.trackThread = trackThread

        try {
            workingThread?.interrupt()
        } catch (e: Throwable) {
            ALog.e(TAG, "", e)
        }

        trackThread.start()
    }

    fun stop() {
        ALog.i(TAG, "stop $binPath")
        try {
            isRunning = false
            runningProcess = null
            trackThread?.interrupt()
            trackThread = null
        } catch (e: Throwable) {
            ALog.e(TAG, "invoke stop", e)
        }
    }

    fun running() : Boolean {
        return isRunning
    }

    private fun runTrack() {
        val myThreadId = Thread.currentThread().id

        var process: Process? = null
        try {
            ALog.i(TAG, "run process: $binPath")
            ALog.d(TAG, "run params ${GsonUtils.toJson(params)}")

            val runningProcess = this.runningProcess
            runningProcess?.waitFor()
            this.runningProcess = null

            val exeParams = mutableListOf(binPath)
            exeParams.addAll(params)

            val startTime = System.currentTimeMillis()
            process = ProcessBuilder(exeParams)
                    .redirectErrorStream(true)
                    .start()

            this.runningProcess = process

            isRunning = true
            listener?.onProcessStarted(this)

            process.waitFor()

            ALog.e(TAG, "process exit error:${process.inputStream.bufferedReader().readLine()}")

            if (System.currentTimeMillis() - startTime < 1000) {
                ALog.i(TAG, "$binPath exit too fast")
            }
        } catch (e: InterruptedException) {
            destroyProcess(process)
            ALog.e(TAG, "process stop", e)
        } catch (e: Throwable) {
            destroyProcess(process)
            ALog.e(TAG, "process stop", e)
        }

        if (myThreadId == trackThread?.id) {
            isRunning = false
            listener?.onProcessStop(this)
        }
    }

    private fun destroyProcess(process: Process?) {
        try {
            process?.destroy()
        } catch (e: Throwable) {
            ALog.e(TAG, "destroyProcess exception", e)
        }
    }

    interface IProcessLauncherListener {
        fun onProcessStarted(launcher: ProcessLauncher)
        fun onProcessStop(launcher: ProcessLauncher)
    }
}