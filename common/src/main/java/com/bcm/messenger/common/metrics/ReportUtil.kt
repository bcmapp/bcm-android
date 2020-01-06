package com.bcm.messenger.common.metrics

import android.os.HandlerThread
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.bcmhttp.configure.lbs.LBSFetcher
import com.bcm.messenger.common.crypto.IdentityKeyUtil
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.utils.isUsingNetwork
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.Base64
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.whispersystems.libsignal.ecc.DjbECPublicKey
import java.util.concurrent.TimeUnit

internal object ReportUtil : LBSFetcher.ILBSFetchResult {
    private const val TAG = "ReportUtil"

    private lateinit var uploader: MetricsUploader

    private val reportThread = HandlerThread("bcm-report")
    private val reportExecutor: Scheduler

    private var initConfig = false
    private var isLogin = false
    private var isLbsReady = false
    private var isAdhocRunning = false
    private var retry = false

    private var lastReportTime = 0L
    private var timeDisposable: Disposable? = null

    var loginStartTime = 0L
    var appLaunchTime = 0L
    var appLaunchEndTime = 0L

    private var protoVersion = 1

    private var slicesMap = mutableMapOf<String, TimeSlice>()
    private val histogramConfigMap = mutableMapOf<String, String>()
    private val accountMap = mutableMapOf<String, ReportLogic>()

    init {
        reportThread.start()
        reportExecutor = AndroidSchedulers.from(reportThread.looper)

        Observable.create<Unit> {
            ALog.i(TAG, "ReportUtil init start")
            val preference = SuperPreferences.getSuperPreferences(AppContextHolder.APP_CONTEXT, SuperPreferences.METRICS)
            val configJson = preference.getString("config", "")
            val sliceJson = preference.getString("slice", "")

            if (!sliceJson.isNullOrBlank()) {
                ALog.i(TAG, "Found cache slices")
                val sliceStorage = GsonUtils.fromJson(sliceJson, SliceStorage::class.java)
                sliceStorage.sliceList.forEach { slice ->
                    slicesMap[slice.name] = slice
                }
            } else {
                ALog.i(TAG, "Cache slices not found, add default config")

                slicesMap["Default1"] = TimeSlice("Default1").apply {
                    cfgVersion = 1
                    timeSlice = listOf(100, 200, 500, 1000, 3000)
                }
                slicesMap["Default2"] = TimeSlice("Default2").apply {
                    cfgVersion = 1
                    timeSlice = listOf(200, 400, 600, 1000, 1500, 3000)
                }
                slicesMap["Default3"] = TimeSlice("Default3").apply {
                    cfgVersion = 1
                    timeSlice = listOf(1000, 2000, 3000, 5000)
                }
            }
            if (!configJson.isNullOrBlank()) {
                ALog.i(TAG, "Found cache configs")
                val configStorage = GsonUtils.fromJson(configJson, ConfigStorage::class.java)
                configStorage.configList.forEach { pair ->
                    histogramConfigMap[pair.first] = pair.second
                }
            }

            it.onComplete()
        }.subscribeOn(reportExecutor)
                .observeOn(reportExecutor)
                .subscribe()
    }


    fun init(accountContext: AccountContext) {
        Observable.create<Unit> {
            ALog.i(TAG, "Login init")
            checkAccountMap(accountContext)
            if (accountMap.size == 1) {
                startTimer()
                if (!initConfig && isLbsReady) {
                    initConfig()
                }
                if (appLaunchEndTime != 0L) {
                    addCustomNetworkReportData(accountContext, APP_TOPIC_BCM, null, -1, null, APP_LAUNCH, METRIC_SUCCESS, appLaunchEndTime - appLaunchTime)
                }
            }

            it.onComplete()
        }.subscribeOn(reportExecutor)
                .subscribe({}, {
                    if (!retry) {
                        retry = true
                        reportExecutor.scheduleDirect({
                            init(accountContext)
                        }, 10L, TimeUnit.SECONDS)
                    }
                })
    }


    fun unInit(accountContext: AccountContext) {
        Observable.create<Unit> {
            ALog.i(TAG, "Logout unInit")
            isLogin = false
            accountMap.remove(accountContext.uid)
            if (accountMap.isEmpty()) {
                timeDisposable?.dispose()
            }
            it.onComplete()
        }.subscribeOn(reportExecutor)
                .subscribe()
    }

    private fun loadConfigs() {
        Observable.create<Unit> {
            ALog.i(TAG, "Load config")
            isLbsReady = true
            if (!initConfig && accountMap.isNotEmpty()) {
                initConfig()
            }
            it.onComplete()
        }.subscribeOn(reportExecutor)
                .subscribe()
    }

    fun setAdhocRunning(isRunning: Boolean) {
        Observable.create<Unit> {
            isAdhocRunning = isRunning
            if (isAdhocRunning) {
                timeDisposable?.dispose()
            } else if (timeDisposable?.isDisposed == true) {
                startTimer()
            }
            it.onComplete()
        }.subscribeOn(reportExecutor)
                .subscribe()
    }

    fun loginEnded(accountContext: AccountContext, isSuccess: Boolean) {
        checkAccountMap(accountContext)
        accountMap[accountContext.uid]!!.loginEnded(isSuccess)
    }

    fun launchEnded() {
        appLaunchTime = System.currentTimeMillis()
        if (accountMap.isNotEmpty()) {
            accountMap.values.first().addCustomNetworkReportData(APP_TOPIC_BCM, null, -1, null, APP_LAUNCH, METRIC_SUCCESS, appLaunchEndTime - appLaunchTime)
        }
    }

    fun addNetworkReportData(accountContext: AccountContext, serverIp: String?, port: Int, reqMethod: String?, path: String?, returnCode: String, time: Long) {
        addCustomNetworkReportData(accountContext, API_TOPIC_BCM_SERVER, serverIp, port, reqMethod, path, returnCode, time)
    }

    fun addLbsNetworkReportData(accountContext: AccountContext, serverIp: String?, port: Int, reqMethod: String?, path: String?, returnCode: String, time: Long) {
        addCustomNetworkReportData(accountContext, API_TOPIC_LBS_SERVER, serverIp, port, reqMethod, path, returnCode, time)
    }

    fun addCallNetworkReportData(accountContext: AccountContext, serverIp: String?, port: Int, reqMethod: String?, path: String?, returnCode: String, time: Long) {
        addCustomNetworkReportData(accountContext, NET_TOPIC_MEDIA_SERVER, serverIp, port, reqMethod, path, returnCode, time)
    }

    fun addCustomNetworkReportData(accountContext: AccountContext, topic: String, serverIp: String?, port: Int, reqMethod: String?, path: String?, returnCode: String, time: Long) {
        Observable.create<Unit> {
            if (isAdhocRunning) {
                ALog.w(TAG, "AdHoc is running, stop report")
                return@create
            }

            accountMap[accountContext.uid]?.addCustomNetworkReportData(topic, serverIp, port, reqMethod, path, returnCode, time)

            it.onComplete()
        }.subscribeOn(reportExecutor)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe()
    }

    fun addDataErrorReportData(accountContext: AccountContext, exceptionName: String) {
        addCustomCounterReportData(accountContext, "bcm_data", exceptionName)
    }

    fun addSystemErrorReportData(accountContext: AccountContext, exceptionName: String) {
        addCustomCounterReportData(accountContext, "bcm_sys", exceptionName)
    }

    fun addCustomCounterReportData(accountContext: AccountContext, topic: String, counterName: String, increment: Boolean = true) {
        Observable.create<Unit> {
            if (isAdhocRunning) {
                ALog.w(TAG, "AdHoc is running, stop report")
                return@create
            }

            accountMap[accountContext.uid]?.addCustomCounterReportData(topic, counterName, increment)

            it.onComplete()
        }.subscribeOn(reportExecutor)
                .subscribe()
    }

    private fun initConfig() {
        ALog.i(TAG, "Init configs")
        uploader = MetricsUploader()

        val accountContext = accountMap[accountMap.keys.first()]!!.accountContext
        val publicKey = IdentityKeyUtil.getIdentityKey(accountContext)
        val pubKey = Base64.encodeBytes((publicKey.publicKey as DjbECPublicKey).serialize())
        val signature = Base64.encodeBytes(BCMEncryptUtils.signWithMe(accountContext, accountContext.uid.toByteArray()))

        val configReq = HistogramConfigReq(accountContext.uid, pubKey, signature)
        val configs = uploader.getTimeSplicesConfig(accountContext, configReq)

        if (null != configs) {
            saveConfig(configs)
            val sliceStorage = SliceStorage()
            sliceStorage.sliceList.addAll(slicesMap.values)

            val configStorage = ConfigStorage()
            configStorage.configList.addAll(histogramConfigMap.toList())

            val preference = SuperPreferences.getSuperPreferences(AppContextHolder.APP_CONTEXT, SuperPreferences.METRICS)
            preference.edit().putString("config", GsonUtils.toJson(configStorage)).apply()
            preference.edit().putString("slice", GsonUtils.toJson(sliceStorage)).apply()
        }

        initConfig = true
    }

    private fun startTimer() {
        timeDisposable?.dispose()
        timeDisposable = Observable.interval(60, TimeUnit.SECONDS, AmeDispatcher.ioScheduler)
                .observeOn(reportExecutor)
                .subscribe {
                    reportData()
                }
    }

    private fun saveConfig(newConfig: MetricsConfigs) {
        if (newConfig.protoVersion > 0) {
            protoVersion = newConfig.protoVersion
        }
        newConfig.histogramConfig.keys.forEach {
            val slice = newConfig.histogramConfig[it]
            if (slice != null) {
                slice.name = it
                slicesMap[it] = slice

                if (it.startsWith("get_") || it.startsWith("put_")
                        || it.startsWith("post_") || it.startsWith("delete_")
                        || it.startsWith("patch_")) {
                    histogramConfigMap[it] = it
                }
            }
        }

        accountMap.values.forEach {
            it.slicesMap = slicesMap
            it.protoVersion = protoVersion
        }
    }

    private fun checkAccountMap(accountContext: AccountContext) {
        var logic = accountMap[accountContext.uid]
        if (logic == null) {
            logic = ReportLogic(accountContext, uploader, slicesMap, protoVersion)
            accountMap[accountContext.uid] = logic
        }
    }

    private fun reportData() {
        ALog.i(TAG, "Report data start")
        if (!initConfig) {
            ALog.i(TAG, "No login account")
            return
        }
        if (!isUsingNetwork()) {
            ALog.w(TAG, "Network is disconnected, all network metrics will be deleted")
            accountMap.values.forEach {
                it.clearNetworkHistogram()
            }
            return
        }
        if (isAdhocRunning) {
            ALog.w(TAG, "AdHoc is running, stop report")
            return
        }

        accountMap.values.forEach {
            it.reportData()
        }

        lastReportTime = System.currentTimeMillis()
    }

    override fun onLBSFetchResult(succeed: Boolean, fetchIndex: Int) {
        if (succeed) {
            loadConfigs()
        }
    }
}