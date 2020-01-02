package com.bcm.messenger.common.provider

import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.AccountJobManager
import com.bcm.messenger.common.core.AmeFileUploader
import com.bcm.messenger.common.database.DatabaseFactory
import com.bcm.messenger.common.database.db.UserDatabase
import com.bcm.messenger.common.metrics.ReportUtil
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.accountmodule.*
import com.bcm.messenger.common.server.IServerConnectionDaemon
import com.bcm.messenger.common.server.IServerDataDispatcher
import com.bcm.messenger.common.server.ServerConnectionDaemon
import com.bcm.messenger.common.server.ServerDataDispatcher
import com.bcm.messenger.common.service.RotateSignedPreKeyListener
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import com.sdk.crashreport.ReportUtils
import org.whispersystems.jobqueue.JobManager
import org.whispersystems.jobqueue.dependencies.DependencyInjector

object AmeModuleCenter {
    private val serverConnectionDaemon: ServerConnectionDaemon = ServerConnectionDaemon(AMELogin.majorContext)
    private val serverDataDispatcher: ServerDataDispatcher = ServerDataDispatcher(AMELogin.majorContext)

    init {
        serverConnectionDaemon.setEventListener(serverDataDispatcher)
    }

    fun instance() {
        login().checkLoginAccountState()
        login().initModule()
        adhoc().initModule()
    }

    fun init(accountContext: AccountContext) {
        group(accountContext)?.initModule()
        chat(accountContext)?.initModule()
        contact(accountContext)?.initModule()
        user(accountContext)?.initModule()
        metric(accountContext)?.initModule()
        wallet(accountContext)?.initModule()
    }

    fun unInit(accountContext: AccountContext) {
        group(accountContext)?.uninitModule()
        chat(accountContext)?.uninitModule()
        contact(accountContext)?.uninitModule()
        user(accountContext)?.uninitModule()
        metric(accountContext)?.uninitModule()
        wallet(accountContext)?.uninitModule()
    }

    fun login(): ILoginModule {
        return AmeProvider.get(ARouterConstants.Provider.PROVIDER_LOGIN_BASE)!!
    }

    fun group(accountContext: AccountContext): IGroupModule? {
        return AmeProvider.getAccountModule(ARouterConstants.Provider.PROVIDER_GROUP_BASE, accountContext)
    }

    fun chat(accountContext: AccountContext): IChatModule? {
        return AmeProvider.getAccountModule(ARouterConstants.Provider.PROVIDER_CONVERSATION_BASE, accountContext)
    }

    fun contact(accountContext: AccountContext): IContactModule? {
        return AmeProvider.getAccountModule(ARouterConstants.Provider.PROVIDER_CONTACTS_BASE, accountContext)
    }

    fun user(accountContext: AccountContext): IUserModule? {
        return AmeProvider.getAccountModule(ARouterConstants.Provider.PROVIDER_USER_BASE, accountContext)
    }

    fun wallet(accountContext: AccountContext): IWalletModule? {
        return AmeProvider.getAccountModule(ARouterConstants.Provider.PROVIDER_WALLET_BASE, accountContext)
    }

    fun metric(accountContext: AccountContext): IMetricsModule? {
        return AmeProvider.getAccountModule(ARouterConstants.Provider.REPORT_BASE, accountContext)
    }

    fun wallet(): IWalletModule {
        return AmeProvider.get<IWalletModule>(ARouterConstants.Provider.PROVIDER_WALLET_BASE)!!
    }

    fun adhoc(): IAdHocModule {
        return AmeProvider.get(ARouterConstants.Provider.PROVIDER_AD_HOC)!!
    }

    fun app(): IAmeAppModule {
        return AmeProvider.get(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)!!
    }

    fun serverDispatcher(accountContext: AccountContext): IServerDataDispatcher {
        return serverDataDispatcher
    }

    fun serverDaemon(accountContext: AccountContext): IServerConnectionDaemon {
        return serverConnectionDaemon
    }

    fun accountJobMgr(context: AccountContext): JobManager? {
        return if (AMELogin.isLogin) {
            AccountJobManager.getManager(AppContextHolder.APP_CONTEXT,
                    DependencyInjector { },
                    context.uid)
        } else null
    }

    fun onLoginSucceed(accountContext: AccountContext) {

        accountJobMgr(accountContext)
        ALog.logForSecret("AmeModuleCenter", "updateAccount ${accountContext.uid}")
        AmeFileUploader.initDownloadPath(AppContextHolder.APP_CONTEXT)
        ReportUtils.setGUid(accountContext.uid)

        if (DatabaseFactory.isDatabaseExist(AppContextHolder.APP_CONTEXT) && !TextSecurePreferences.isDatabaseMigrated(AppContextHolder.APP_CONTEXT)) {
            val factory = DatabaseFactory.getInstance(AppContextHolder.APP_CONTEXT)
            factory?.reset(AppContextHolder.APP_CONTEXT, accountContext.uid)
            return
        } else {
            UserDatabase.resetDatabase(accountContext)
        }

        ReportUtil.init()

        this.init(accountContext)

        contact(accountContext)?.doForLogin()

        if (!adhoc().isAdHocMode()) {
            serverDaemon(accountContext).startDaemon()
            serverDaemon(accountContext).startConnection()
        }

        RotateSignedPreKeyListener.schedule(AppContextHolder.APP_CONTEXT)

        System.gc()

    }

    fun onLogOutSucceed(accountContext: AccountContext) {
        serverDaemon(accountContext).stopConnection()
        serverDaemon(accountContext).stopDaemon()

        unInit(accountContext)
    }

}