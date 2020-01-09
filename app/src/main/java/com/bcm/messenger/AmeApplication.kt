package com.bcm.messenger

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.webkit.WebView
import androidx.multidex.MultiDexApplication
import com.bcm.messenger.common.*
import com.bcm.messenger.common.bcmhttp.configure.lbs.LBSManager
import com.bcm.messenger.common.bcmhttp.conncheck.IMServerConnectionChecker
import com.bcm.messenger.common.bcmhttp.interceptor.BcmHeaderInterceptor
import com.bcm.messenger.common.bcmhttp.interceptor.metrics.NormalMetricsInterceptor
import com.bcm.messenger.common.core.SystemUtils
import com.bcm.messenger.common.core.onConfigurationChanged
import com.bcm.messenger.common.core.setApplicationLanguage
import com.bcm.messenger.common.core.setLocale
import com.bcm.messenger.common.crypto.PRNGFixes
import com.bcm.messenger.common.jobs.GcmRefresh
import com.bcm.messenger.common.metrics.ReportConfigure
import com.bcm.messenger.common.provider.*
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.crash.initCrash
import com.bcm.messenger.crash.setCrashReportLogs
import com.bcm.messenger.logic.EnvSettingLogic
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.bcmhttp.facade.BaseHttp
import com.bcm.messenger.utility.ble.BleUtil
import com.bcm.messenger.utility.foreground.AppForeground
import com.bcm.messenger.utility.gps.GPSUtil
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.logger.AmeExceptionLogger
import com.bcm.messenger.utility.logger.AmeLogConfig
import com.bcm.messenger.utility.network.NetworkUtil
import com.bcm.messenger.utility.permission.PermissionUtil
import com.bcm.messenger.utility.wifi.WiFiUtil
import com.bcm.netswitchy.configure.AmeConfigure
import com.bcm.netswitchy.proxy.ProxyManager
import com.bcm.route.api.BcmRouter
import com.orhanobut.logger.Logger
import com.squareup.leakcanary.LeakCanary
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins
import okhttp3.OkHttpClient
import org.whispersystems.libsignal.logging.SignalProtocolLoggerProvider
import java.io.File
import java.io.IOException
import java.net.SocketException


/**
 * Created by ling on 2018/2/27.
 */
class AmeApplication : MultiDexApplication() {

    companion object {
        private const val TAG = "AmeApplication"
    }

    private val exceptionLogger = AmeExceptionLogger()

    override fun onCreate() {
        super.onCreate()
        ALog.i(TAG, "application create")
        //do not use AmeApplicationProvider, because of not inited, maybe null
        val isDevBuild = BuildConfig.DEBUG
        val isReleaseBuild = BuildConfig.FLAVOR.startsWith(ARouterConstants.CONSTANT_RELEASE) && !isDevBuild

        AppContextHolder.init(this)
        AppForeground.init(this)

        NetworkUtil.init(this)

        AmeAppLifecycle.init(this)
        initBcmRouter()
        AmeConfigure.init(!isReleaseBuild)

        initLog()
        initCrash()
        initLanguage()

        AmeProvider.get<IAFModule>(ARouterConstants.Provider.PROVIDER_AFLIB)?.onAppInit(this)
        AmeProvider.get<IUmengModule>(ARouterConstants.Provider.PROVIDER_UMENG)?.onAppInit(this)

        if (!AppUtil.isMainProcess()) {
            bindService()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isWebProcess()) {
                WebView.setDataDirectorySuffix("Web")
            }

            return
        }

        initNetWork(isReleaseBuild)

        AmeModuleCenter.login()

        check()

        WebRtcSetup.setup()
        GcmRefresh.refresh()
        PRNGFixes.apply()

        GPSUtil.init(this)
        WiFiUtil.init(this)
        BleUtil.init(this)

        AmePushProcess.clearNotificationCenter()

        if (AMELogin.isLogin) {
            AmeModuleCenter.metric(AMELogin.majorContext)?.launchStart()
        }

        if (LeakCanary.isInAnalyzerProcess(this)) {
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not reset your app in this process.
            return
        }

        LeakCanary.install(this)

        AmeConfigure.checkContactTransformEnable()

        try {
            ALog.i(TAG, "version:${getPackageInfo().versionName} build:${getPackageInfo().versionCode} Flavor: ${BuildConfig.FLAVOR}")
            ALog.i(TAG, "env:${EnvSettingLogic.getEnvSetting(isReleaseBuild).server} lbs:${EnvSettingLogic.getEnvSetting(isReleaseBuild).lbsEnable}")
            ALog.i(TAG, "model:${SystemUtils.getSimpleSystemInfo()} version:${Build.VERSION.SDK_INT} release:${Build.VERSION.RELEASE}")
        } catch (e: Exception) {
            ALog.e(TAG, e)
        }

        AmeModuleCenter.instance()
    }

    override fun onTerminate() {
        super.onTerminate()
        NetworkUtil.unInit(this)
    }

    private fun initBcmRouter() {
        if (BuildConfig.DEBUG) {
            BcmRouter.openDebug()
        }
        BcmRouter.init(this, BuildConfig.FLAVOR)
    }

    private fun initLog() {
        val diskPath = if (!AppUtil.isReleaseBuild()) {
            Environment.getExternalStorageDirectory().absolutePath + File.separatorChar + ARouterConstants.SDCARD_ROOT_FOLDER
        } else {
            AppContextHolder.APP_CONTEXT.filesDir.absolutePath
        }

        var level = Logger.INFO
        if (!BuildConfig.FLAVOR.startsWith(ARouterConstants.CONSTANT_RELEASE) || BuildConfig.DEBUG) {
            level = Logger.VERBOSE
        }

        AmeLogConfig.setLog(this, level, "$diskPath${File.separatorChar}logger${File.separatorChar}", 12) {
            setCrashReportLogs()
        }

        setRxJavaErrorHandler()

        exceptionLogger.initLogger()

        SignalProtocolLoggerProvider.setProvider { priority, tag, message ->
            Logger.log(priority, tag, message, null)
        }
    }

    private fun initNetWork(isReleaseBuild: Boolean) {
        SystemUtils.initAPPInfo(getPackageInfo().versionName, getPackageInfo().versionCode)

        val httpsEnable = EnvSettingLogic.getEnvSetting(isReleaseBuild).httpsEnable
        BaseHttp.setDevMode(!httpsEnable)

        ALog.i(TAG, "initNetWork $httpsEnable")

        ProxyManager.setConnectionChecker(IMServerConnectionChecker())

        val client = OkHttpClient.Builder()
                .addInterceptor(NormalMetricsInterceptor())
                .addInterceptor(BcmHeaderInterceptor())
                .build()
        AmeConfigure.setClient(client)

        ProxyManager.refresh()

        LBSManager.addMetricsListener(ReportConfigure)
        LBSManager.refresh(null)
    }

    private fun setRxJavaErrorHandler() {
        if (RxJavaPlugins.getErrorHandler() != null || RxJavaPlugins.isLockdown()) return

        RxJavaPlugins.setErrorHandler { ex ->
            var e: Throwable? = null
            if (ex is UndeliverableException) {
                e = ex.cause
            }
            if (e is IOException || e is SocketException) {
                // fine, irrelevant network problem or API that throws on cancellation
                return@setErrorHandler
            }
            if (e is InterruptedException) {
                // fine, some blocking code was interrupted by a dispose call
                return@setErrorHandler
            }
            if (e is NullPointerException || e is IllegalArgumentException) {
                // that's likely a bug in the application
                return@setErrorHandler
            }
            if (e is IllegalStateException) {
                // that's a bug in RxJava or in a custom operator
                return@setErrorHandler
            }
            ALog.e("AmeApplication", "Undeliverable exception received, not sure what to do", e)
        }
    }

    private fun initLanguage() {
        setApplicationLanguage(this)
        PermissionUtil.languageSetting = object : PermissionUtil.ILanguageSetting {
            override fun permissionTitle(): String {
                return getString(R.string.common_permission_rationale_notice)
            }

            override fun permissionGoSetting(): String {
                return getString(R.string.common_open)
            }

            override fun permissionCancel(): String {
                return getString(R.string.common_cancel)
            }

        }
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(setLocale(base))
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        onConfigurationChanged(this)
    }

    private fun bindService() {
        ALog.i(TAG, "bind other process service")
        val intent = Intent(this, ApplicationService::class.java)
        bindService(intent, object : ServiceConnection {

            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                ApplicationService.impl = IApplicationlImpl.Stub.asInterface(service)
                //如果服务已经连接上，要检测一下防窥屏设置（因为服务有延后，所以可能当时的activity还没来得及设置）
                val curActivity = AmeAppLifecycle.current()
                if (curActivity is SwipeBaseActivity) {
                    curActivity.updateScreenshotSecurity()
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {

            }

        }, Context.BIND_AUTO_CREATE)
    }

    /**
     * check
     */
    private fun check() {
        val o = p()
        if (Build.VERSION.SDK_INT >= 28) {
            o.b(AppContextHolder.APP_CONTEXT)
        } else {
            o.a(AppContextHolder.APP_CONTEXT)
        }
    }
}