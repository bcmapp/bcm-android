package com.bcm.messenger.login.module

import android.content.Intent
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.bcmhttp.configure.lbs.LBSManager
import com.bcm.messenger.common.config.BcmFeatureSupport
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.ILoginModule
import com.bcm.messenger.common.provider.accountmodule.IAdHocModule
import com.bcm.messenger.common.server.ConnectState
import com.bcm.messenger.common.server.IServerConnectStateListener
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.common.utils.AmePushProcess
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.login.logic.CreateSignedPreKeyJob
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.foreground.AppForeground
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.network.INetworkConnectionListener
import com.bcm.messenger.utility.network.NetworkUtil
import com.bcm.netswitchy.proxy.IProxyStateChanged
import com.bcm.netswitchy.proxy.ProxyManager
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import io.reactivex.disposables.Disposable
import org.greenrobot.eventbus.EventBus

/**
 * bcm.social.01 2018/9/20.
 */
@Route(routePath = ARouterConstants.Provider.PROVIDER_LOGIN_BASE)
class LoginModuleImpl : ILoginModule
        , AppForeground.IForegroundEvent
        , IProxyStateChanged
        , INetworkConnectionListener
        , IServerConnectStateListener {

    private val TAG = "LoginProviderImplProxy"
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
        return AmeLoginLogic.getAccount(uid)?.genKeyTime ?: 0
    }

    override fun registrationId(uid: String): Int {
        return AmeLoginLogic.getAccount(uid)?.registrationId ?: 0
    }

    override fun gcmToken(): String {
        return AmeLoginLogic.getGcmToken()
    }

    override fun setGcmToken(token: String?) {
        AmeLoginLogic.setGcmToken(token ?: "")
    }

    override fun isGcmDisabled(): Boolean {
        return AmeLoginLogic.accountHistory.majorAccountData()?.gcmDisabled ?: false
    }

    override fun signalingKey(uid: String): String? {
        return AmeLoginLogic.getAccount(uid)?.signalingKey
    }

    override fun isSignedPreKeyRegistered(uid: String): Boolean {
        return AmeLoginLogic.getAccount(uid)?.signedPreKeyRegistered ?: false
    }

    override fun setSignedPreKeyRegistered(uid: String, registered: Boolean) {
        val accountData = AmeLoginLogic.getAccount(uid)
        if (accountData != null) {
            accountData.signedPreKeyRegistered = registered
            AmeLoginLogic.saveAccount(accountData)
        }
    }

    override fun getSignedPreKeyFailureCount(uid: String): Int {
        return AmeLoginLogic.getMajorAccount()?.signedPreKeyFailureCount ?: 0
    }

    override fun setSignedPreKeyFailureCount(uid: String, count: Int) {
        val accountData = AmeLoginLogic.getMajorAccount()
        if (accountData != null) {
            accountData.signedPreKeyFailureCount = count
            AmeLoginLogic.saveAccount(accountData)
        }
    }

    override fun getSignedPreKeyRotationTime(uid: String): Long {
        return AmeLoginLogic.getMajorAccount()?.signedPreKeyRotationTime ?: 0L
    }

    override fun setSignedPreKeyRotationTime(uid: String, time: Long) {
        val accountData = AmeLoginLogic.getMajorAccount()
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

    override fun mySupport(): BcmFeatureSupport {
        return AmeLoginLogic.mySupport()
    }

    override fun refreshMySupport2Server(accountContext: AccountContext) {
        AmeLoginLogic.refreshMySupportFeature(accountContext)
    }

    override fun restoreLastLoginState() {
        val loginContextList = AmeLoginLogic.accountHistory.getAllLoginContext()
        if (loginContextList.isNotEmpty()) {
            AmePushProcess.reset()

            for (i in loginContextList) {
                AmeModuleCenter.onLoginSucceed(i)
                if (!TextSecurePreferences.isSignedPreKeyRegistered(i)) {
                    AmeModuleCenter.accountJobMgr(i)?.add(CreateSignedPreKeyJob(AppContextHolder.APP_CONTEXT, i))
                }
            }
        } else {
            ALog.i(TAG, "restoreLastLoginState not login, pass")
        }
    }

    override fun continueLoginSuccess(accountContext: AccountContext) {
        AmeLoginLogic.initAfterLoginSuccess(accountContext)
    }

    override fun refreshPrekeys(accountContext: AccountContext) {
        AmeLoginLogic.refreshPrekeys(accountContext)
    }

    override fun rotateSignedPrekey(accountContext: AccountContext) {
        AmeLoginLogic.rotateSignedPreKey(accountContext)
    }

    override fun updateAllowReceiveStrangers(accountContext: AccountContext, allow: Boolean, callback: ((succeed: Boolean) -> Unit)?) {
        AmeLoginLogic.updateAllowReceiveStrangers(accountContext, allow, callback)
    }

    override fun getAccountContext(uid: String): AccountContext {
        return AmeLoginLogic.getAccountContext(uid)
    }

    override fun refreshOfflineToken() {
        return AmeLoginLogic.refreshOfflineToken()
    }

    override fun getLoginAccountContextList(): List<AccountContext> {
        return AmeLoginLogic.accountHistory.getAllLoginContext()
    }

    override fun onForegroundChanged(isForeground: Boolean) {
        ALog.i(TAG, "onForegroundChanged $isForeground")
        if (isLogin()) {
            if (isForeground) {
                AmePushProcess.reset()

                val loginList = AmeLoginLogic.accountHistory.getAllLoginContext()
                for (accountContext in loginList) {
                    if (AmeModuleCenter.serverDaemon(accountContext).state() != ConnectState.CONNECTED) {
                        AmeModuleCenter.serverDaemon(accountContext).checkConnection(false)
                    }
                }
            } else {
                lastTryProxyTime = 0
            }
        }

        if (!isForeground) {
            delayCheckRunProxy?.dispose()
        } else {
            val loginList = AmeLoginLogic.accountHistory.getAllLoginContext()
            val anyConnecting = loginList.any {
                AmeModuleCenter.serverDaemon(it).state() != ConnectState.DISCONNECTED
            }

            if (!anyConnecting) {
                tryRunProxy()
            }

            for (accountContext in loginList) {
                AmeModuleCenter.chat(accountContext)?.checkHasRtcCall()
            }
        }
    }

    override fun onServerConnectionChanged(accountContext: AccountContext, newState: ConnectState) {
        when(newState) {
            ConnectState.CONNECTED -> {
                lastTryProxyTime = 0L
                delayCheckRunProxy?.dispose()
            }
            ConnectState.DISCONNECTED -> {
                tryRunProxy()
            }
            else -> {}
        }
    }

    override fun onNetWorkStateChanged() {
        ALog.i(TAG, "net work state changed ${NetworkUtil.netType()}")
        if (NetworkUtil.isConnected() && networkType != NetworkUtil.netType()) {
            networkType = NetworkUtil.netType()
            if (NetworkUtil.isConnected() && AMELogin.isLogin) {
                if (ProxyManager.isProxyRunning()) {
                    ALog.i(TAG, "net work state changed and stop proxy")
                    ProxyManager.stopProxy()
                }

                ALog.i(TAG, "net work state changed and check all daemon connection")
                val loginList = AmeLoginLogic.accountHistory.getAllLoginContext()
                loginList.forEach {
                    AmeModuleCenter.serverDaemon(it).checkConnection(false)
                }
            }
        } else if(!NetworkUtil.isConnected()) {
            networkType = NetworkUtil.NetType.NONE
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
                LBSManager.refresh(AMELogin.majorContext)
                val loginList = AmeLoginLogic.accountHistory.getAllLoginContext()
                loginList.forEach {
                    AmeModuleCenter.serverDaemon(it).checkConnection()
                }
            }
        }
    }
}