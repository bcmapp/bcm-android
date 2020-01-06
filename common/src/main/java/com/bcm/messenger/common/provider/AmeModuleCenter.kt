package com.bcm.messenger.common.provider

import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.AccountJobManager
import com.bcm.messenger.common.core.AmeFileUploader
import com.bcm.messenger.common.database.DatabaseFactory
import com.bcm.messenger.common.database.db.UserDatabase
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.accountmodule.*
import com.bcm.messenger.common.server.IServerConnectionDaemon
import com.bcm.messenger.common.server.IServerDataDispatcher
import com.bcm.messenger.common.server.ServerConnectionDaemon
import com.bcm.messenger.common.server.ServerDataDispatcher
import com.bcm.messenger.common.service.RotateSignedPreKeyListener
import com.bcm.messenger.common.utils.AccountContextMap
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import com.sdk.crashreport.ReportUtils
import org.whispersystems.jobqueue.JobManager

object AmeModuleCenter {
    private val serverDataDispatcher: ServerDataDispatcher = ServerDataDispatcher()
    private val serverConnDaemons = AccountContextMap {
        ServerConnectionDaemon(it, serverDataDispatcher)
    }

    fun instance() {
        login().restoreLastLoginState()
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

        AmeProvider.removeModule(accountContext)
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

    fun adhoc(): IAdHocModule {
        return AmeProvider.get(ARouterConstants.Provider.PROVIDER_AD_HOC)!!
    }

    fun app(): IAmeAppModule {
        return AmeProvider.get(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)!!
    }

    fun serverDispatcher(): IServerDataDispatcher {
        return serverDataDispatcher
    }

    fun serverDaemon(accountContext: AccountContext): IServerConnectionDaemon {
        return serverConnDaemons.get(accountContext)
    }

    fun accountJobMgr(context: AccountContext): JobManager? {
        return AccountJobManager.get(context)
    }

    fun onLoginSucceed(accountContext: AccountContext) {

        accountJobMgr(accountContext)
        ALog.logForSecret("AmeModuleCenter", "updateAccount ${accountContext.uid}")
        AmeFileUploader.initDownloadPath(AppContextHolder.APP_CONTEXT)
        ReportUtils.setGUid(accountContext.uid)

        if (DatabaseFactory.isDatabaseExist(AppContextHolder.APP_CONTEXT) && !TextSecurePreferences.isDatabaseMigrated(accountContext)) {
            val factory = DatabaseFactory.getInstance(AppContextHolder.APP_CONTEXT)
            factory?.reset(AppContextHolder.APP_CONTEXT, accountContext.uid)
            return
        } else {
            Repository.getInstance(accountContext)
        }

        this.init(accountContext)

        contact(accountContext)?.doForLogin()

        if (!adhoc().isAdHocMode()) {
            serverDaemon(accountContext).startDaemon()
            serverDaemon(accountContext).startConnection()
        }

        RotateSignedPreKeyListener.schedule(AppContextHolder.APP_CONTEXT, accountContext)

        System.gc()

    }

    fun onLogOutSucceed(accountContext: AccountContext) {
        if (serverConnDaemons.containsKey(accountContext)) {
            val serverdaemon = serverConnDaemons.get(accountContext)
            serverdaemon.stopConnection()
            serverdaemon.stopDaemon()
        }

        unInit(accountContext)
        Repository.accountLogOut(accountContext)
        serverConnDaemons.remove(accountContext)
        AccountJobManager.remove(accountContext)
    }

}