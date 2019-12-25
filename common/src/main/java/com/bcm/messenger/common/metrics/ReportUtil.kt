package com.bcm.messenger.common.metrics

import android.net.Uri
import android.os.HandlerThread
import com.bcm.messenger.common.bcmhttp.interceptor.RedirectInterceptorHelper
import com.bcm.messenger.common.bcmhttp.configure.lbs.LBSFetcher
import com.bcm.messenger.common.core.BcmHttpApiHelper
import com.bcm.messenger.common.crypto.IdentityKeyUtil
import com.bcm.messenger.common.preferences.SuperPreferences
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.utils.isUsingNetwork
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.Base64
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.bcmhttp.exception.NoContentException
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.network.NetworkUtil
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.whispersystems.libsignal.ecc.DjbECPublicKey
import java.util.*
import java.util.concurrent.TimeUnit


object ReportUtil : LBSFetcher.ILBSFetchResult {
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

    private var currentTime = getCurrentTimeMinute()
    private var protoVersion = 1
    private var histogramCount = 0

    private var slicesMap = mutableMapOf<String, TimeSlice>()
    private val histogramConfigMap = mutableMapOf<String, String>()
    private val networkReportMap = mutableMapOf<String, MutableMap<String, MutableMap<String, MutableMap<String, MutableMap<Long, Histogram>>>>>() // Map<IP, Map<Topic, Map<Path, Map<ReturnCode, Map<Time, LocalHistogram>>>>>
    private val counterReportMap = mutableMapOf<String, MutableMap<String, MutableMap<Long, Counter>>>() // Map<Topic, Map<CounterName, Map<Time, Counter>>>

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

    
    fun init() {
        Observable.create<Unit> {
            ALog.i(TAG, "Login init")
            isLogin = true
            if (!initConfig && isLbsReady) {
                initConfig()
            }
            startTimer()
            it.onComplete()
        }.subscribeOn(reportExecutor)
                .subscribe({}, {
                    if (!retry) {
                        retry = true
                        reportExecutor.scheduleDirect({
                            init()
                        }, 10L, TimeUnit.SECONDS)
                    }
                })
    }

    
    fun unInit() {
        Observable.create<Unit> {
            ALog.i(TAG, "Logout unInit")
            isLogin = false
            lastReportTime = 0L
            timeDisposable?.dispose()
            it.onComplete()
        }.subscribeOn(reportExecutor)
                .subscribe()

    }

    fun loadConfigs() {
        Observable.create<Unit> {
            ALog.i(TAG, "Load config")
            isLbsReady = true
            if (!initConfig && isLogin) {
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

    fun loginEnded(isSuccess: Boolean) {
        val endTime = System.currentTimeMillis()

        val uri = Uri.parse(BcmHttpApiHelper.getApi(""))
        addCustomNetworkReportData(NET_TOPIC_BCM_SERVER, uri.host, uri.port, null, "login", if (isSuccess) METRIC_SUCCESS else METRIC_FAILED, endTime - loginStartTime)
    }

    fun launchEnded() {
        val endTime = System.currentTimeMillis()

        addCustomNetworkReportData(APP_TOPIC_BCM, null, -1, null, APP_LAUNCH, METRIC_SUCCESS, endTime - appLaunchTime)
    }

    fun addNetworkReportData(serverIp: String?, port: Int, reqMethod: String?, path: String?, returnCode: String, time: Long) {
        addCustomNetworkReportData(API_TOPIC_BCM_SERVER, serverIp, port, reqMethod, path, returnCode, time)
    }

    fun addLbsNetworkReportData(serverIp: String?, port: Int, reqMethod: String?, path: String?, returnCode: String, time: Long) {
        addCustomNetworkReportData(API_TOPIC_LBS_SERVER, serverIp, port, reqMethod, path, returnCode, time)
    }

    fun addCallNetworkReportData(serverIp: String?, port: Int, reqMethod: String?, path: String?, returnCode: String, time: Long) {
        addCustomNetworkReportData(NET_TOPIC_MEDIA_SERVER, serverIp, port, reqMethod, path, returnCode, time)
    }

    fun addCustomNetworkReportData(topic: String, serverIp: String?, port: Int, reqMethod: String?, path: String?, returnCode: String, time: Long) {
        Observable.create<Unit> {
            if (isAdhocRunning) {
                ALog.w(TAG, "AdHoc is running, stop report")
                return@create
            }

//            val innerTopic = topic
            val innerTopic = when {
//                // aws and cloudfront belongs to aws topic
//                serverIp?.contains("amazonaws.com") == true || serverIp?.contains("cloudfront.net") == true -> API_TOPIC_AWS_S3
//                // bs2dl belongs to bs2 topic
//                serverIp?.contains("ameim.bs2dl.yy.com") == true -> API_TOPIC_BS2
//                serverIp?.contains("aliyuncs.com") == true -> API_ALIYUN
                path?.contains("/v1/lbs") == true -> API_TOPIC_LBS_SERVER
                else -> topic
            }

            val urlWithPort = if (!serverIp.isNullOrEmpty()) {
                if (port > 0) "$serverIp:$port"
                else serverIp
            } else ""

            val protoKey = when {
                // cdn contains uid, may leaks privacy
                // Amazon AWS and AWS CDN use RequestMethod_Host as protoKey
//                urlWithPort.contains("cloudfront") -> "${reqMethod?.toLowerCase() ?: "get"}_$serverIp"
//                urlWithPort.contains("amazonaws.com") || urlWithPort.contains("aliyuncs.com") -> "${reqMethod?.toLowerCase() ?: "post"}_$serverIp"
                !reqMethod.isNullOrEmpty() -> {
                    when {
                        // Path filters
                        path == null -> "/"
                        path.startsWith("/v1/accounts") -> {
                            when {
                                path.startsWith("/v1/accounts/features") -> "${reqMethod.toLowerCase()}_/v1/accounts/features"
                                path.startsWith("/v1/accounts/challenge") -> "${reqMethod.toLowerCase()}_/v1/accounts/challenge"
                                path.startsWith("/v1/accounts/gcm") -> "${reqMethod.toLowerCase()}_/v1/accounts/gcm"
                                path.startsWith("/v1/accounts/turn") -> "${reqMethod.toLowerCase()}_/v1/accounts/turn"
                                path.startsWith("/v1/accounts/attributes") -> "${reqMethod.toLowerCase()}_/v1/accounts/attributes"
                                path.startsWith("/v1/accounts/signin") -> "${reqMethod.toLowerCase()}_/v1/accounts/signin"
                                path.startsWith("/v1/accounts/signup") -> "${reqMethod.toLowerCase()}_/v1/accounts/signup"
                                path.startsWith("/v1/accounts/tokens") -> "${reqMethod.toLowerCase()}_/v1/accounts/tokens"
                                else -> "${reqMethod.toLowerCase()}_/v1/accounts"
                            }
                        }
                        path.startsWith("/v2/keys") -> {
                            when {
                                path.startsWith("/v2/keys/signed") -> "${reqMethod.toLowerCase()}_/v2/keys/signed"
                                // /v2/keys API with params
                                path.startsWith("/v2/keys/") && path.length > 9 -> "${reqMethod.toLowerCase()}_/v2/keys/:uid"
                                // /v2/keys API with no param
                                else -> "${reqMethod.toLowerCase()}_/v2/keys"
                            }
                        }
                        path.startsWith("/v1/profile") -> {
                            // /v1/profile APIs
                            when {
                                path.startsWith("/v1/profile/namePlaintext") -> "${reqMethod.toLowerCase()}_/v1/profile/namePlaintext"
                                path.startsWith("/v1/profile/nickname") -> "${reqMethod.toLowerCase()}_/v1/profile/nickname"
                                path.startsWith("/v1/profile/avatar") -> "${reqMethod.toLowerCase()}_/v1/profile/avatar"
                                path.startsWith("/v1/profile/uploadAvatarPlaintext") -> "${reqMethod.toLowerCase()}_/v1/profile/uploadAvatarPlaintext"
                                path.startsWith("/v1/profile/keys") -> "${reqMethod.toLowerCase()}_/v1/profile/keys"
                                else -> "${reqMethod.toLowerCase()}_/v1/profile"
                            }
                        }
                        path.startsWith("/v1/attachments") -> {
                            when {
                                path.startsWith("/v1/attachments/s3/upload_certification") -> "${reqMethod.toLowerCase()}_/v1/attachments/s3/upload_certification"
                                path.startsWith("/v1/attachments/upload") -> "${reqMethod.toLowerCase()}_/v1/attachments/upload"
                                path.startsWith("/v1/attachments/download") -> "${reqMethod.toLowerCase()}_/v1/attachments/download"
                                else -> "${reqMethod.toLowerCase()}_/v1/attachments"
                            }
                        }
                        path.startsWith("/v1/system/msgs") -> "${reqMethod.toLowerCase()}_/v1/system/msgs"
                        path.startsWith("/v1/messages") -> "${reqMethod.toLowerCase()}_/v1/messages"
                        path.startsWith("/attachments") -> "${reqMethod.toLowerCase()}_/attachments"
                        path.startsWith("/v1/opaque_data") -> "${reqMethod.toLowerCase()}_/v1/opaque_data"
                        else -> "${reqMethod.toLowerCase()}_$path"
                    }
                }
                path != null -> path
                else -> "/"
            }

            val minuteTime = getCurrentTimeMinute()
            if (minuteTime != currentTime) {
                currentTime = minuteTime
            }

            ALog.logForSecret(TAG, "Add network report data, topic = $innerTopic, serverIp = $urlWithPort, protoKey = $protoKey, timestamp = $minuteTime, returnCode = $returnCode, time = $time")

            checkAndCreateHistogramMap(urlWithPort, innerTopic, protoKey, returnCode)

            val timeSlice = slicesMap[histogramConfigMap[protoKey]] ?: slicesMap["Default1"]!!
            var histogram = networkReportMap[urlWithPort]!![innerTopic]!![protoKey]!![returnCode]!![minuteTime]
            if (histogram == null) {
                val map = hashMapOf<String, Int>().apply {
                    timeSlice.getZones().forEach { zone ->
                        put(zone, 0)
                    }
                }
                histogram = Histogram(innerTopic, urlWithPort, protoKey, returnCode, minuteTime,
                        timeSlice.name, timeSlice.cfgVersion, TimeData(0, 0, map))
                histogramCount++
            }
            histogram.timeData.totalTime += time
            histogram.timeData.totalCount += 1

            val zone = timeSlice.getZone(time)
            var timeCount = histogram.timeData.slices[zone] ?: 0
            timeCount++
            histogram.timeData.slices[zone] = timeCount

            networkReportMap[urlWithPort]!![innerTopic]!![protoKey]!![returnCode]!![minuteTime] = histogram

            it.onComplete()
        }.subscribeOn(reportExecutor)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe()
    }

    fun addDataErrorReportData(exceptionName: String) {
        addCustomCounterReportData("bcm_data", exceptionName)
    }

    fun addSystemErrorReportData(exceptionName: String) {
        addCustomCounterReportData("bcm_sys", exceptionName)
    }

    fun addCustomCounterReportData(topic: String, counterName: String, increment: Boolean = true) {
        Observable.create<Unit> {
            if (isAdhocRunning) {
                ALog.w(TAG, "AdHoc is running, stop report")
                return@create
            }

            val minuteTime = getCurrentTimeMinute()
            if (minuteTime != currentTime) {
                currentTime = minuteTime
            }

            ALog.d(TAG, "Add counter report data, topic = $topic, name = $counterName, time = $minuteTime")

            checkAndCreateCounterMap(topic, counterName)
            var counter = counterReportMap[topic]!![counterName]!![minuteTime]
            if (counter == null) {
                counter = Counter(topic, counterName, minuteTime, 0)
            }
            if (increment) {
                counter.increment += 1
            }

            counterReportMap[topic]!![counterName]!![minuteTime] = counter

            it.onComplete()
        }.subscribeOn(reportExecutor)
                .subscribe()
    }

    private fun initConfig() {
        ALog.i(TAG, "Init configs")
        uploader = MetricsUploader()

        
        val uid = AMELogin.uid
        val publicKey = IdentityKeyUtil.getIdentityKey(AppContextHolder.APP_CONTEXT)
        val pubKey = Base64.encodeBytes((publicKey.publicKey as DjbECPublicKey).serialize())
        val signature = Base64.encodeBytes(BCMEncryptUtils.signWithMe(AppContextHolder.APP_CONTEXT, uid.toByteArray()))

        val configReq = HistogramConfigReq(uid, pubKey, signature)
        val configs = uploader.getTimeSplicesConfig(configReq)

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
    }

    private fun checkAndCreateHistogramMap(serverIp: String, topic: String, protoKey: String, returnCode: String) {
        var topicMap = networkReportMap[serverIp]
        if (topicMap == null) {
            topicMap = mutableMapOf()
            networkReportMap[serverIp] = topicMap
        }
        var protoKeyMap = topicMap[topic]
        if (protoKeyMap == null) {
            protoKeyMap = mutableMapOf()
            topicMap[topic] = protoKeyMap
        }
        var codeMap = protoKeyMap[protoKey]
        if (codeMap == null) {
            codeMap = mutableMapOf()
            protoKeyMap[protoKey] = codeMap
        }
        var minuteMap = codeMap[returnCode]
        if (minuteMap == null) {
            minuteMap = mutableMapOf()
            codeMap[returnCode] = minuteMap
        }
    }

    private fun checkAndCreateCounterMap(topic: String, counterName: String) {
        var topicMap = counterReportMap[topic]
        if (topicMap == null) {
            topicMap = mutableMapOf()
            counterReportMap[topic] = topicMap
        }
        var nameMap = topicMap[counterName]
        if (nameMap == null) {
            nameMap = mutableMapOf()
            topicMap[counterName] = nameMap
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
            networkReportMap.clear()
            return
        }
        if (isAdhocRunning) {
            ALog.w(TAG, "AdHoc is running, stop report")
            return
        }

        val uid: String
        val pubKey: String
        try {
            uid = AMELogin.uid
            val publicKey = IdentityKeyUtil.getIdentityKey(AppContextHolder.APP_CONTEXT)
            pubKey = Base64.encodeBytes((publicKey.publicKey as DjbECPublicKey).serialize())

            if (uid.isBlank() || pubKey.isBlank()) {
                ALog.e(TAG, "UID or PublicKey is empty!! Cannot report to server!!")
                unInit()
                return
            }
        } catch (e: Throwable) {
            ALog.e(TAG, "Error occurred when getting UID or PublicKey.", e)
            unInit()
            return
        }

        val netType = NetworkUtil.netType()

        val histogramList = mutableListOf<Histogram>()
        val counterList = mutableListOf<Counter>()

        
        networkReportMap.values.forEach { topicMap ->
            topicMap.values.forEach { protoKeyMap ->
                protoKeyMap.values.forEach { codeMap ->
                    codeMap.values.forEach { timeMap ->
                        timeMap.values.forEach { histogram ->
                            histogramList.add(histogram)
                        }
                    }
                }
            }
        }
        counterReportMap.values.forEach { topicMap ->
            topicMap.values.forEach { nameMap ->
                nameMap.values.forEach { counter ->
                    counterList.add(counter)
                }
            }
        }

        try {
            val area = RedirectInterceptorHelper.imServerInterceptor.getCurrentServer().area.toString()
            if (histogramList.size + counterList.size <= 100) {
                
                val signature = Base64.encodeBytes(BCMEncryptUtils.signWithMe(AppContextHolder.APP_CONTEXT, uid.toByteArray()))
                val reportData = ReportData(1, uid, pubKey, signature,
                        ClientInfo(area, netType.typeName),
                        histogramList, counterList)
                if (!realReport(reportData)) {
                    ALog.i(TAG, "Report data failed")
                    return
                }
            } else {
                val reportDataList = mutableListOf<ReportData>()
                while (histogramList.size > 0 || counterList.size > 0) {
                    
                    val signature = Base64.encodeBytes(BCMEncryptUtils.signWithMe(AppContextHolder.APP_CONTEXT, uid.toByteArray()))
                    if (histogramList.size > 100) {
                        
                        val subList = histogramList.subList(0, 100)
                        val reportData = ReportData(protoVersion, uid, pubKey, signature,
                                ClientInfo(area, netType.typeName),
                                subList, emptyList())
                        reportDataList.add(reportData)
                        
                        histogramList.removeAll(subList)
                    } else {
                        
                        val endSize = if (100 - histogramList.size > counterList.size) counterList.size else 100 - histogramList.size
                        val subList = counterList.subList(0, endSize)
                        val reportData = ReportData(protoVersion, uid, pubKey, signature,
                                ClientInfo(area, netType.typeName),
                                histogramList, subList)
                        reportDataList.add(reportData)
                        
                        histogramList.clear()
                        
                        counterList.removeAll(subList)
                    }
                }
                reportDataList.forEach {
                    
                    if (!realReport(it)) {
                        ALog.i(TAG, "Report data failed")
                        return
                    }
                }
            }

            ALog.i(TAG, "Report data success, clear temp data")
            
            networkReportMap.clear()
            counterReportMap.clear()

            lastReportTime = System.currentTimeMillis()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun realReport(reportData: ReportData): Boolean {
        try {
            val configs = uploader.reportToServer(reportData) ?: return false
            saveConfig(configs)
            networkReportMap.clear()
        } catch (e: NoContentException) {
        }
        return true
    }

    private fun getCurrentTimeMinute(): Long {
        val calendar = Calendar.getInstance()
        val minuteCalendar = Calendar.getInstance()

        calendar.timeZone = TimeZone.getTimeZone("UTC")
        minuteCalendar.timeZone = TimeZone.getTimeZone("UTC")

        minuteCalendar.clear()
        minuteCalendar.set(calendar[Calendar.YEAR], calendar[Calendar.MONTH], calendar[Calendar.DATE], calendar[Calendar.HOUR], calendar[Calendar.MINUTE])
        return minuteCalendar.time.time
    }

    override fun onLBSFetchResult(succeed: Boolean, fetchIndex: Int) {
        if (succeed) {
            loadConfigs()
        }
    }
}