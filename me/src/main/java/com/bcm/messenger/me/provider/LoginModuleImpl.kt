package com.bcm.messenger.me.provider

import android.app.Activity
import android.content.Intent
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.bcmhttp.configure.lbs.LBSManager
import com.bcm.messenger.common.config.BcmFeatureSupport
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.crypto.MasterSecretUtil
import com.bcm.messenger.common.database.DatabaseFactory
import com.bcm.messenger.common.event.ClientAccountDisabledEvent
import com.bcm.messenger.common.event.ServerConnStateChangedEvent
import com.bcm.messenger.common.event.ServiceConnectEvent
import com.bcm.messenger.common.grouprepository.room.database.GroupDatabase
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.*
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.common.utils.AmePushProcess
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.login.logic.AmeLoginLogic
import com.bcm.messenger.login.logic.CreateSignedPreKeyJob
import com.bcm.messenger.me.R
import com.bcm.messenger.me.logic.AmeNoteLogic
import com.bcm.messenger.me.logic.AmePinLogic
import com.bcm.messenger.me.ui.keybox.SwitchAccountAdapter
import com.bcm.messenger.me.ui.login.RegistrationActivity
import com.bcm.messenger.me.utils.MeConfirmDialog
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
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * bcm.social.01 2018/9/20.
 */
@Route(routePath = ARouterConstants.Provider.PROVIDER_LOGIN_BASE)
class LoginModuleImpl : ILoginModule, AppForeground.IForegroundEvent, IProxyStateChanged,IConnectionListener {

    private val TAG = "LoginProviderImplProxy"

    private var kickEvent: ClientAccountDisabledEvent? = null
    private var serviceConnectedState = ServiceConnectEvent.STATE.UNKNOWN

    private var expireDispose: Disposable? = null
    private var kickOutDispose: Disposable? = null

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
        AmeNoteLogic.getInstance().refreshCurrentUser()
    }

    override fun uninitModule() {

    }

    override fun isLogin(): Boolean {
        return AmeLoginLogic.isLogin()
    }

    override fun loginUid(): String {
        return AmeLoginLogic.accountHistory.currentLoginUid()
    }

    override fun genTime(): Long {
        return AmeLoginLogic.getCurrentAccount()?.genKeyTime ?: 0
    }

    override fun registrationId(): Int {
        return AmeLoginLogic.getCurrentAccount()?.registrationId ?: 0
    }

    override fun gcmToken(): String? {
        return AmeLoginLogic.getCurrentAccount()?.gcmToken
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

    override fun signalingKey(): String? {
        return AmeLoginLogic.getCurrentAccount()?.signalingKey
    }

    override fun isSignedPreKeyRegistered(): Boolean {
        return AmeLoginLogic.getCurrentAccount()?.signedPreKeyRegistered ?: false
    }

    override fun setSignedPreKeyRegistered(registered: Boolean) {
        val accountData = AmeLoginLogic.getCurrentAccount()
        if (accountData != null) {
            accountData.signedPreKeyRegistered = registered
            AmeLoginLogic.saveAccount(accountData)
        }
    }

    override fun getSignedPreKeyFailureCount(): Int {
        return AmeLoginLogic.getCurrentAccount()?.signedPreKeyFailureCount ?: 0
    }

    override fun setSignedPreKeyFailureCount(count: Int) {
        val accountData = AmeLoginLogic.getCurrentAccount()
        if (accountData != null) {
            accountData.signedPreKeyFailureCount = count
            AmeLoginLogic.saveAccount(accountData)
        }
    }

    override fun getSignedPreKeyRotationTime(): Long {
        return AmeLoginLogic.getCurrentAccount()?.signedPreKeyRotationTime ?: 0L
    }

    override fun setSignedPreKeyRotationTime(time: Long) {
        val accountData = AmeLoginLogic.getCurrentAccount()
        if (accountData != null) {
            accountData.signedPreKeyRotationTime = time
            AmeLoginLogic.saveAccount(accountData)
        }
    }

    override fun token(): String {
        return AmeLoginLogic.token()
    }

    override fun accountDir(uid: String): String {
        return AmeLoginLogic.accountDir(uid)
    }

    override fun quit(clearHistory: Boolean, withLogOut: Boolean) {
        AmeLoginLogic.quit(clearHistory, withLogOut)
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
            val walletProvider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_WALLET_BASE).navigation() as IWalletProvider
            walletProvider.destroyWallet()
            AmeProvider.get<IContactModule>(ARouterConstants.Provider.PROVIDER_CONTACTS_BASE)?.clear()

        }catch (ex: Exception) {
            ALog.e(TAG, "clearAll error", ex)
        }
    }

    override fun authPassword(): String {
        return AmeLoginLogic.authPassword()
    }

    override fun serviceConnectedState(): ServiceConnectEvent.STATE {
        return serviceConnectedState
    }

    override fun isPinEnable(): Boolean {
        return AmePinLogic.hasPin()
    }

    override fun mySupport(): BcmFeatureSupport {
        return AmeLoginLogic.mySupport()
    }

    override fun refreshMySupport2Server() {
        AmeLoginLogic.refreshMySupportFeature()
    }

    override fun checkLoginAccountState() {
        AmePinLogic.initLogic()

        if (AMESelfData.isLogin) {
            if (AMESelfData.uid != AmeLoginLogic.accountHistory.lastLoginUid()) {
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
            AmeModuleCenter.onLoginStateChanged(AMESelfData.uid)
            if(!TextSecurePreferences.isSignedPreKeyRegistered(AppContextHolder.APP_CONTEXT)) {
                AmeModuleCenter.accountJobMgr()?.add(CreateSignedPreKeyJob(AppContextHolder.APP_CONTEXT))
            }
        }
    }

    override fun logoutMenu() {
        val activity = AmeAppLifecycle.current() ?: return
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }

        SwitchAccountAdapter().switchAccount(activity, AMESelfData.uid, Recipient.fromSelf(activity, true))
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

    override fun refreshNotes() {
        AmeNoteLogic.getInstance().refreshCurrentUser()
    }

    override fun updateAllowReceiveStrangers(allow: Boolean, callback: ((succeed: Boolean) -> Unit)?) {
        AmeLoginLogic.updateAllowReceiveStrangers(allow, callback)
    }

    override fun isPinLocked(): Boolean {
        return AmePinLogic.isLocked()
    }

    override fun showPinLock() {
        if (AppForeground.foreground()) {
            AmePinLogic.showPinLock()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(e: ClientAccountDisabledEvent) {
        ALog.i(TAG, "onClientAccountDisableEvent: ${e.type}, lastEvent: ${kickEvent?.type}")
        if (AppForeground.foreground()) {
            handleAccountExceptionLogout(e)
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
        } else {
            serviceConnectedState = ServiceConnectEvent.STATE.DISCONNECTED

            if (e.state == ServerConnStateChangedEvent.OFF) {
                tryRunProxy()
            }
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
            if (NetworkUtil.isConnected() && AMESelfData.isLogin) {
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
                    if (AMESelfData.isLogin) {
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
                if (!AMESelfData.isLogin){
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
                if (AMESelfData.isLogin) {
                    AmeModuleCenter.serverDaemon().checkConnection()
                }
            }
        }
    }

    private fun handleAccountExceptionLogout(event: ClientAccountDisabledEvent) {
        ALog.i(TAG, "handleAccountExceptionLogout ${event.type}")
        val activity = AmeAppLifecycle.current() ?: return
        ALog.i(TAG, "handleAccountExceptionLogout 1 ${event.type}")
        if (AMESelfData.isLogin) {
            ALog.i(TAG, "handleAccountExceptionLogout 2 ${event.type}")
            try {
                when (event.type) {
                    ClientAccountDisabledEvent.TYPE_EXPIRE -> handleTokenExpire(activity)
                    ClientAccountDisabledEvent.TYPE_EXCEPTION_LOGIN -> handleForceLogout(activity, event.data)
                    ClientAccountDisabledEvent.TYPE_ACCOUNT_GONE -> handleAccountGone(activity)
                }
            } catch (ex: Exception) {
                ALog.e(TAG, "handleAccountExceptionLogout error", ex)
            }
        }
    }

    private fun handleForceLogout(activity: Activity, info: String?) {
        expireDispose?.dispose()
        expireDispose = null

        if (!AMESelfData.isLogin) {
            return
        }

        val uid = AMESelfData.uid
        ALog.i(TAG, "handleForceLogout 1")
        if (kickOutDispose == null) {
            ALog.i(TAG, "handleForceLogout 2")
            kickOutDispose = Observable.create<Recipient> {
                ALog.i(TAG, "handleForceLogout 3")
                AmeProvider.get<ILoginModule>(ARouterConstants.Provider.PROVIDER_LOGIN_BASE)?.quit(clearHistory = false, withLogOut = false)
                it.onComplete()
            }.subscribeOn(AmeDispatcher.singleScheduler)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnError {
                        ALog.e(TAG, "handleForceLogout", it)
                        kickOutDispose = null
                    }
                    .doOnComplete { kickOutDispose = null }
                    .subscribe()

            AmePopup.center.dismiss()
            BcmRouter.getInstance().get(ARouterConstants.Activity.ACCOUNT_DESTROY)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putParcelable(ARouterConstants.PARAM.PARAM_ADDRESS, Address.fromSerialized(uid))
                    .putString(ARouterConstants.PARAM.PARAM_CLIENT_INFO, info)
                    .putString(ARouterConstants.PARAM.PARAM_ACCOUNT_ID, uid)
                    .navigation(activity)
        }

    }

    private fun handleTokenExpire(activity: Activity) {
        if (!AMESelfData.isLogin) {
            return
        }

        if (expireDispose == null && kickOutDispose == null) {
            expireDispose = Observable.create<Recipient> {
                ALog.i(TAG, "handleTokenExpire 1")
                if (AMESelfData.isLogin) {
                    AmeProvider.get<ILoginModule>(ARouterConstants.Provider.PROVIDER_LOGIN_BASE)?.quit(clearHistory = false, withLogOut = false)
                } else {
                    throw java.lang.Exception("not login")
                }

                it.onComplete()
            }.delaySubscription(3000, TimeUnit.MILLISECONDS, AmeDispatcher.singleScheduler)
                    .subscribeOn(AmeDispatcher.singleScheduler)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnError {
                        ALog.e(TAG, "handleTokenExpire", it)
                        expireDispose = null
                    }
                    .doOnComplete { expireDispose = null }
                    .subscribe {
                        AmeAppLifecycle.show(activity.getString(R.string.me_logout_with_expire)) {
                            BcmRouter.getInstance().get(ARouterConstants.Activity.USER_REGISTER_PATH)
                                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    .navigation(activity)
                        }
                    }
        }
    }

    private fun handleAccountGone(activity: Activity) {
        val self = try {
            Recipient.fromSelf(activity, true)
        } catch (ex: Exception) {
            null
        } ?: return

        AmeDispatcher.io.dispatch {
            AmeProvider.get<ILoginModule>(ARouterConstants.Provider.PROVIDER_LOGIN_BASE)?.quit(false)
            AmeLoginLogic.accountHistory.deleteAccount(self.address.serialize())
        }

        MeConfirmDialog.showConfirm(activity, activity.getString(R.string.me_destroy_account_confirm_title),
                activity.getString(R.string.me_destroy_account_warning_notice), activity.getString(R.string.me_destroy_account_confirm_button)) {

            AmeModuleCenter.onLoginStateChanged("")

            BcmRouter.getInstance().get(ARouterConstants.Activity.USER_REGISTER_PATH)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .navigation(activity)
            activity.finish()
        }
    }
}