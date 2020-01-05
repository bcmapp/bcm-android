package com.bcm.messenger.adhoc.ui

import com.bcm.messenger.adhoc.R
import com.bcm.messenger.adhoc.logic.AdHocChannelLogic
import com.bcm.messenger.adhoc.sdk.AdHocSDK
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

class AdHocConnectingStep {
    private val TAG = "AdHocConnectingStep"
    private var currentStep = STEP.INIT
    private var scanningStep: Disposable?= null
    private var founedStep: Disposable?= null
    private var connectingFailed: Disposable?= null
    private var connected: Disposable?= null

    private var stepChanged: () -> Unit = {}
    private var connectingDevice:String = ""
    private var deviceCount:Int = 0

    fun init(stepChanged:()->Unit) {
        this.stepChanged = stepChanged

        stepScanning()
    }

    fun unInit() {
        currentStep = STEP.INIT
        this.stepChanged = {}
        scanningStep?.dispose()
        scanningStep = null
        founedStep?.dispose()
        founedStep = null
        connectingFailed?.dispose()
        connectingFailed = null
        connected?.dispose()
        connected = null
    }

    private fun updateStep(step: STEP, deviceName: String) {
        ALog.i(TAG, "updateStep $step")
        val count = AdHocSDK.getSearchResult()
        if (currentStep != step
                || this.connectingDevice != deviceName
                || count != deviceCount) {
            deviceCount = count
            connectingDevice = deviceName
            currentStep = step
            stepChanged()
        }

    }

    fun connected() {
        ALog.i(TAG, "stepFinished")
        connectingDevice = ""
        stepConnected()
    }

    fun disconnected() {
        stepScanning()
    }

    fun connecting() {
        ALog.i(TAG, "connecting")
        val deviceName = AdHocSDK.getNetSnapshot().serverName
        if (connectingDevice.isEmpty() || deviceName == connectingDevice) {
            stepConnecting(deviceName)
        } else {
            stepConnectingFailed()
        }
    }

    fun getStepDescribe(): String {
        val clientConnected = AdHocSDK.getNetSnapshot().clientSet.isNotEmpty()
        val serverConnected = AdHocSDK.getNetSnapshot().myIp.isNotEmpty()
        if (clientConnected || serverConnected) {
            return Recipient.major().name
        }

        return when(currentStep) {
            STEP.INIT, STEP.SCANNING -> {
                AppUtil.getString(R.string.adhoc_device_scanning)
            }
            STEP.FOUNDED -> {
                val count = AdHocSDK.getSearchResult()
                if (count == 0) {
                    AppUtil.getString(R.string.adhoc_main_title_device_found_0)
                } else {
                    AppContextHolder.APP_CONTEXT.resources.getQuantityString(R.plurals.adhoc_main_title_device_found, count, count)
                }
            }
            STEP.CONNECTING -> AppContextHolder.APP_CONTEXT.resources.getString(R.string.adhoc_main_title_connecting_to, connectingDevice)
            STEP.CONNECTED -> AppUtil.getString(R.string.adhoc_main_title_connected)
            STEP.CONNECTING_FAILED -> AppUtil.getString(R.string.adhoc_main_title_connect_failed)
            else -> {
                Recipient.major().name
            }
        }
    }

    private fun stepScanning() {
        ALog.i(TAG, "stepScanning")

        updateStep(STEP.SCANNING, "")

        val wSelf = WeakReference(this)

        scanningStep = Observable.timer(1000, TimeUnit.MILLISECONDS)
                .repeat(8)
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete {
                    wSelf.get()?. scanningStep = null
                    wSelf.get()?.stepFounded()
                }
                .subscribe {
                    val count = AdHocSDK.getSearchResult()
                    if (count > 0) {
                        scanningStep?.dispose()
                        scanningStep = null
                        wSelf.get()?. scanningStep = null
                        wSelf.get()?.stepFounded()
                    }
                }
    }

    private fun stepFounded() {
        ALog.i(TAG, "stepFounded")
        updateStep(STEP.FOUNDED, "")

        val wSelf = WeakReference(this)
        founedStep = AmeDispatcher.mainThread.dispatch({
            wSelf.get()?.founedStep = null
            val connectingDevice = AdHocSDK.getNetSnapshot().serverName
            wSelf.get()?.stepConnecting(connectingDevice)
        },1000)
    }

    private fun stepConnecting(connectingDevice: String) {
        ALog.i(TAG, "stepConnecting")
        if (connectingDevice.isEmpty()) {
            stepFounded()
            return
        }

        updateStep(STEP.CONNECTING, connectingDevice)
    }

    private fun stepConnectingFailed() {
        ALog.i(TAG, "stepConnectingFailed")
        updateStep(STEP.CONNECTING_FAILED, connectingDevice)

        val wSelf = WeakReference(this)
        connectingFailed = AmeDispatcher.mainThread.dispatch({
            wSelf.get()?.connectingFailed = null
            val connectingDevice = AdHocSDK.getNetSnapshot().serverName
            wSelf.get()?.stepConnecting(connectingDevice)
        },1000)
    }

    private fun stepConnected() {
        ALog.i(TAG, "stepConnected")
        updateStep(STEP.CONNECTED,"")

        val wSelf = WeakReference(this)
        connected = AmeDispatcher.mainThread.dispatch({
            wSelf.get()?.connected = null
            wSelf.get()?.stepFinished()
        },1000)
    }

    private fun stepFinished() {
        ALog.i(TAG, "stepFinished")
        if (AdHocChannelLogic.isOnline()) {
            updateStep(STEP.FINISHED,connectingDevice)
        } else {
            stepScanning()
        }
    }

    enum class STEP(val step:Int) {
        INIT(0),
        SCANNING(1),
        FOUNDED(2),
        CONNECTING(3),
        CONNECTING_FAILED(4),
        CONNECTED(5),
        FINISHED(6)
    }
}