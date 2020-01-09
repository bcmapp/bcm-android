package com.bcm.messenger.common.metrics

import android.os.HandlerThread
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.bcmhttp.configure.lbs.LBSFetcher
import com.bcm.messenger.common.crypto.IdentityKeyUtil
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.event.AccountLoginStateChangedEvent
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.Base64
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.listener.IWeakListeners
import com.bcm.messenger.utility.listener.SafeWeakListeners
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.whispersystems.libsignal.ecc.DjbECPublicKey
import java.util.concurrent.TimeUnit

object ReportConfigure : LBSFetcher.ILBSFetchResult {
    private const val TAG = "ReportConfigure"
    private val METRICS_URL = "https://39.108.124.60:6666"
    private val CONFIGURE_URL = "${METRICS_URL}/v1/metrics/config"
    val REPORT_URL = "${METRICS_URL}/v1/metrics/reports"

    private var initConfig = false
    private val histogramConfigMap = mutableMapOf<String, String>()
    private val slicesMap = mutableMapOf<String, TimeSlice>()
    private var protoVersion: Int = 1

    private var timeDisposable: Disposable? = null
    val tickListeners:IWeakListeners<IReportTickerListener> = SafeWeakListeners()

    private val DEFAULT_SLICE = TimeSlice("Default1").apply {
        cfgVersion = 1
        timeSlice = listOf(100, 200, 500, 1000, 3000)
    }

    private val reportThread = HandlerThread("bcm-report")
    private val reportExecutor: Scheduler

    init {
        EventBus.getDefault().register(this)
        reportThread.start()
        reportExecutor = AndroidSchedulers.from(reportThread.looper)

        val disposable = Observable.create<Unit> {
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

            syncConfig()
            it.onComplete()
        }.subscribeOn(reportExecutor)
                .observeOn(reportExecutor)
                .subscribe({}, {})

        tryRunTicker()
    }


    fun getConfig(protoKey: String): TimeSlice {
        return slicesMap[histogramConfigMap[protoKey]]?: slicesMap["Default1"] ?: DEFAULT_SLICE
    }

    fun updateConfig(newConfig: MetricsConfigs) {
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
    }

    fun getProtoVersion(): Int {
        return protoVersion
    }

    fun isReady(): Boolean {
        return initConfig
    }

    fun getScheduler(): Scheduler {
        return reportExecutor
    }

    @Subscribe
    fun onEvent(loginStateChangedEvent: AccountLoginStateChangedEvent) {
        tryRunTicker()
    }

    private fun tryRunTicker() {
        if (AMELogin.isLogin) {
            if (timeDisposable?.isDisposed != false) {
                startTicker()
                reportExecutor.scheduleDirect {
                    syncConfig()
                }
            }
        } else {
            stopTicker()
        }
    }

    private fun startTicker() {
        timeDisposable?.dispose()
        timeDisposable = Observable.interval(60, TimeUnit.SECONDS, reportExecutor)
                .observeOn(reportExecutor)
                .subscribe {
                    (tickListeners as SafeWeakListeners).forEach {
                        it.onMetricsReportTick()
                    }
                }
    }

    private fun syncConfig() {
        if (AMELogin.isLogin) {
            AmeDispatcher.io.dispatch ({
                val publicKey = IdentityKeyUtil.getIdentityKey(AMELogin.majorContext)
                val pubKey = Base64.encodeBytes((publicKey.publicKey as DjbECPublicKey).serialize())
                val signature = Base64.encodeBytes(BCMEncryptUtils.signWithMe(AMELogin.majorContext, AMELogin.majorContext.uid.toByteArray()))

                val configReq = HistogramConfigReq(AMELogin.majorContext.uid, pubKey, signature)
                val configs = getTimeSplicesConfig(AMELogin.majorContext, configReq)

                if (null != configs) {
                    updateConfig(configs)
                    val sliceStorage = SliceStorage()
                    sliceStorage.sliceList.addAll(slicesMap.values)

                    val configStorage = ConfigStorage()
                    configStorage.configList.addAll(histogramConfigMap.toList())

                    val preference = SuperPreferences.getSuperPreferences(AppContextHolder.APP_CONTEXT, SuperPreferences.METRICS)
                    preference.edit().putString("config", GsonUtils.toJson(configStorage)).apply()
                    preference.edit().putString("slice", GsonUtils.toJson(sliceStorage)).apply()
                }
            }, 2000)
        }
    }

    private fun getTimeSplicesConfig(accountContext: AccountContext, configReq: HistogramConfigReq): MetricsConfigs? {
        var configs: MetricsConfigs? = null

        try {
            val metrics = AmeModuleCenter.metric(accountContext)?:return null
            configs = ReportHttp(metrics).post<MetricsConfigs>(CONFIGURE_URL, configReq.toJson(), MetricsConfigs::class.java)
        } catch (e: Throwable) {
            ALog.e(TAG, e)
        }

        return configs
    }

    private fun stopTicker() {
        timeDisposable?.dispose()
    }

    override fun onLBSFetchResult(succeed: Boolean, fetchIndex: Int) {
        if (succeed) {
            reportExecutor.scheduleDirect {
                syncConfig()
            }
        }
    }

    interface IReportTickerListener {
        fun onMetricsReportTick()
    }
}