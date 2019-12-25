package com.bcm.messenger.login.module

import android.content.Intent
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.bcmhttp.configure.lbs.LBSManager
import com.bcm.messenger.common.config.BcmFeatureSupport
import com.bcm.messenger.common.crypto.MasterSecretUtil
import com.bcm.messenger.common.database.DatabaseFactory
import com.bcm.messenger.common.event.ClientAccountDisabledEvent
import com.bcm.messenger.common.event.ServerConnStateChangedEvent
import com.bcm.messenger.common.event.ServiceConnectEvent
import com.bcm.messenger.common.grouprepository.room.database.GroupDatabase
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.*
import com.bcm.messenger.common.provider.accountmodule.IAdHocModule
import com.bcm.messenger.common.provider.accountmodule.IWalletModule
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.common.utils.AmePushProcess
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.login.logic.CreateSignedPreKeyJob
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.foreground.AppForeground
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.network.IConnectionListener
import com.bcm.messenger.utility.network.NetworkUtil
import com.bcm.netswitchy.proxy.IProxyStateChanged
import com.bcm.netswitchy.proxy.ProxyManager
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import io.reactivex.disposables.Disposable
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File

/**
 * bcm.social.01 2018/9/20.
 */
@Route(routePath = ARouterConstants.Provider.PROVIDER_LOGIN_BASE)
class LoginModuleImpl : ILoginModule, AppForeground.IForegroundEvent, IProxyStateChanged, IConnectionListener {

    private val TAG = "LoginProviderImplProxy"

    private var kickEvent: ClientAccountDisabledEvent? = null
    private var serviceConnectedState = ServiceConnectEvent.STATE.UNKNOWN

    private var delayCheckRunProxy: Disposable? = null
    private var lastTryProxyTime = 0L

    private var networkType = NetworkUtil.netType()

    init {
        ALog.i(TAG, "init")
        EventBus.getDefault().register(this)
        AppForeground.listener.addListener(this)
        ProxyManager.setListener(this)
        NetworkUtil.addListener(this)
    }

    override fun initModule() {

    }

    override fun uninitModule() {

    }

    override fun isLogin(): Boolean {
        return AmeLoginLogic.isLogin(AmeLoginLogic.accountHistory.majorAccountUid())
    }

    override fun isAccountLogin(uid: String): Boolean {
        return AmeLoginLogic.isLogin(uid)
    }

    override fun majorUid(): String {
        return AmeLoginLogic.accountHistory.majorAccountUid()
    }

    override fun minorUidList(): List<String> {
        return AmeLoginLogic.accountHistory.minorAccountList()
    }

    override fun genTime(uid: String): Long {
        return AmeLoginLogic.accountHistory.getAccount(uid)?.genKeyTime ?: 0
    }

    override fun registrationId(uid: String): Int {
        return AmeLoginLogic.getCurrentAccount()?.registrationId ?: 0
    }

    override fun setGcmToken(token: String?) {
        val accountData = AmeLoginLogic.getCurrentAccount()
        if (accountData != null) {
            accountData.gcmToken = token
            AmeLoginLogic.saveAccount(accountData)
        }
    }

    override fun gcmTokenLastSetTime(): Long {
        return AmeLoginLogic.getCurrentAccount()?.gcmTokenLastSetTime ?: 0L
    }

    override fun setGcmTokenLastSetTime(time: Long) {
        val accountData = AmeLoginLogic.getCurrentAccount()
        if (accountData != null) {
            accountData.gcmTokenLastSetTime = time
            AmeLoginLogic.saveAccount(accountData)
        }
    }

    override fun isGcmDisabled(): Boolean {
        return AmeLoginLogic.getCurrentAccount()?.gcmDisabled ?: false
    }

    override fun isPushRegistered(): Boolean {
        return AmeLoginLogic.getCurrentAccount()?.pushRegistered ?: false
    }

    override fun signalingKey(uid: String): String? {
        return AmeLoginLogic.getCurrentAccount()?.signalingKey
    }

    override fun isSignedPreKeyRegistered(uid: String): Boolean {
        return AmeLoginLogic.getCurrentAccount()?.signedPreKeyRegistered ?: false
    }

    override fun setSignedPreKeyRegistered(uid: String, registered: Boolean) {
        val accountData = AmeLoginLogic.getCurrentAccount()
        if (accountData != null) {
            accountData.signedPreKeyRegistered = registered
            AmeLoginLogic.saveAccount(accountData)
        }
    }

    override fun getSignedPreKeyFailureCount(uid: String): Int {
        return AmeLoginLogic.getCurrentAccount()?.signedPreKeyFailureCount ?: 0
    }

    override fun setSignedPreKeyFailureCount(uid: String, count: Int) {
        val accountData = AmeLoginLogic.getCurrentAccount()
        if (accountData != null) {
            accountData.signedPreKeyFailureCount = count
            AmeLoginLogic.saveAccount(accountData)
        }
    }

    override fun getSignedPreKeyRotationTime(uid: String): Long {
        return AmeLoginLogic.getCurrentAccount()?.signedPreKeyRotationTime ?: 0L
    }

    override fun setSignedPreKeyRotationTime(uid: String, time: Long) {
        val accountData = AmeLoginLogic.getCurrentAccount()
        if (accountData != null) {
            accountData.signedPreKeyRotationTime = time
            AmeLoginLogic.saveAccount(accountData)
        }
    }

    override fun accountDir(uid: String): String {
        return AmeLoginLogic.accountDir(uid)
    }

    override fun quit(accountContext: AccountContext, clearHistory: Boolean, withLogOut: Boolean) {
        AmeLoginLogic.quit(accountContext, clearHistory, withLogOut)
    }

    override fun clearAll() {
        AmeLoginLogic.quit(true, true)
        try {
            if (DatabaseFactory.isDatabaseExist(AppContextHolder.APP_CONTEXT)) {
                DatabaseFactory.getInstance(AppContextHolder.APP_CONTEXT).deleteAllDatabase()
                GroupDatabase.getInstance().clearAllTables()
            }

            BcmFileUtils.deleteDir(File("${AppContextHolder.APP_CONTEXT.filesDir.parent}"))

            TextSecurePreferences.clear(AppContextHolder.APP_CONTEXT)
            MasterSecretUtil.clearMasterSecretPassphrase(AppContextHolder.APP_CONTEXT)
            val walletProvider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_WALLET_BASE).navigation() as IWalletModule
            walletProvider.destroyWallet()
            AmeModuleCenter.contact().clear()

        } catch (ex: Exception) {
            ALog.e(TAG, "clearAll error", ex)
        }
    }

    override fun authPassword(uid: String): String {
        return AmeLoginLogic.authPassword(uid)
    }

    override fun serviceConnectedState(): ServiceConnectEvent.STATE {
        return serviceConnectedState
    }

    override fun mySupport(): BcmFeatureSupport {
        return AmeLoginLogic.mySupport()
    }

    override fun refreshMySupport2Server() {
        AmeLoginLogic.refreshMySupportFeature()
    }

    override fun checkLoginAccountState() {
        if (AMELogin.isLogin) {
            if (AMELogin.uid != AmeLoginLogic.accountHistory.lastLoginUid()) {
                AmeDispatcher.io.dispatch {
                    AmeLoginLogic.quit(false)

                    AmeDispatcher.mainThread.dispatch {
                        //登出了，跳到注册界面
                        BcmRouter.getInstance().get(ARouterConstants.Activity.USER_REGISTER_PATH)
                                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .navigation()
                    }
                }
                return
            }

            AmePushProcess.reset()
            AmeModuleCenter.onLoginSucceed(AMELogin.uid)
            if (!TextSecurePreferences.isSignedPreKeyRegistered(AppContextHolder.APP_CONTEXT)) {
                AmeModuleCenter.accountJobMgr()?.add(CreateSignedPreKeyJob(AppContextHolder.APP_CONTEXT))
            }

        } else {
            ALog.i(TAG, "checkLoginAccountState not login, pass")
        }
    }

    override fun continueLoginSuccess() {
        AmeLoginLogic.initAfterLoginSuccess()
    }

    override fun refreshPrekeys() {
        AmeLoginLogic.refreshPrekeys()
    }

    override fun rotateSignedPrekey() {
        AmeLoginLogic.rotateSignedPreKey()
    }

    override fun updateAllowReceiveStrangers(allow: Boolean, callback: ((succeed: Boolean) -> Unit)?) {
        AmeLoginLogic.updateAllowReceiveStrangers(allow, callback)
    }

    override fun getAccountContext(uid: String): AccountContext {
        return AccountContext(uid, "", "")
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(e: ClientAccountDisabledEvent) {
        ALog.i(TAG, "onClientAccountDisableEvent: ${e.type}, lastEvent: ${kickEvent?.type}")
        if (AppForeground.foreground()) {
            AmeModuleCenter.user().forceLogout(e)
        } else {
            if (kickEvent?.type ?: 0 < e.type) {
                kickEvent = e
            }
        }
    }

    override fun onForegroundChanged(isForeground: Boolean) {
        ALog.i(TAG, "onForegroundChanged $isForeground")
        val kick = kickEvent
        if (isForeground && kick != null) {
            kickEvent = null
            onEvent(kick)
        } else {
            if (isLogin()) {
                if (isForeground) {
                    AmePushProcess.reset()

                    if (serviceConnectedState != ServiceConnectEvent.STATE.CONNECTED) {
                        AmeModuleCenter.serverDaemon().checkConnection(false)
                    }
                } else {
                    lastTryProxyTime = 0
                }
            }
        }

        if (!isForeground) {
            delayCheckRunProxy?.dispose()
        } else {
            if (serviceConnectedState == ServiceConnectEvent.STATE.DISCONNECTED) {
                tryRunProxy()
            }

            AmeModuleCenter.chat().checkHasRtcCall()
        }
    }

    @Subscribe
    fun onEvent(e: ServerConnStateChangedEvent) {
        val oldState = serviceConnectedState
        serviceConnectedState = ServiceConnectEvent.STATE.UNKNOWN
        if (e.state == ServerConnStateChangedEvent.ON) {
            lastTryProxyTime = 0L
            serviceConnectedState = ServiceConnectEvent.STATE.CONNECTED
            delayCheckRunProxy?.dispose()
        } else if (e.state == ServerConnStateChangedEvent.CONNECTING) {
            serviceConnectedState = ServiceConnectEvent.STATE.CONNECTING
        } else {
            serviceConnectedState = ServiceConnectEvent.STATE.DISCONNECTED
            tryRunProxy()
        }

        if (serviceConnectedState != oldState) {
            ALog.i(TAG, "service connected state changed:$serviceConnectedState")
            AmeDispatcher.mainThread.dispatch {
                EventBus.getDefault().post(ServiceConnectEvent(serviceConnectedState))
            }
        }
    }

    override fun onNetWorkStateChanged() {
        if (networkType != NetworkUtil.netType()) {
            networkType = NetworkUtil.netType()
            if (NetworkUtil.isConnected() && AMELogin.isLogin) {
                //网络切换了，重新
                if (ProxyManager.isProxyRunning()) {
                    ProxyManager.stopProxy()
                    AmeModuleCenter.serverDaemon().checkConnection(false)
                }
            }
        }
    }

    private fun tryRunProxy() {
        val provider = AmeProvider.get<IAdHocModule>(ARouterConstants.Provider.PROVIDER_AD_HOC)
        if (NetworkUtil.isConnected() && provider?.isAdHocMode() != true) {
            if (AppForeground.foreground()) {
                if (lastTryProxyTime > 0) {
                    return
                }

                lastTryProxyTime = System.currentTimeMillis()
                delayCheckRunProxy = AmeDispatcher.io.dispatch({
                    if (AMELogin.isLogin) {
                        when {
                            ProxyManager.isReady() -> ProxyManager.startProxy()
                            else -> ProxyManager.refresh()
                        }
                    } else {
                        ALog.i(TAG, "network is working, ignore start proxy")
                    }
                }, 1000)
            } else {
                ALog.i(TAG, "tryRunProxy, app is in background")
            }
        } else {
            ALog.i(TAG, "tryRunProxy network is shutdown")
        }
    }

    override fun onProxyConnectFinished() {
        val provider = AmeProvider.get<IAdHocModule>(ARouterConstants.Provider.PROVIDER_AD_HOC)
        if (provider?.isAdHocMode() != true) {
            if (!ProxyManager.isProxyRunning()) {
                if (!AMELogin.isLogin) {
                    if (AppForeground.foreground() && !ProxyManager.isTesting()) {
                        AmeAppLifecycle.showProxyGuild {
                            BcmRouter.getInstance().get(ARouterConstants.Activity.PROXY_SETTING)
                                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    .navigation()
                        }
                    }
                }
            } else {
                LBSManager.refresh()
                if (AMELogin.isLogin) {
                    AmeModuleCenter.serverDaemon().checkConnection()
                }
            }
        }
    }
}