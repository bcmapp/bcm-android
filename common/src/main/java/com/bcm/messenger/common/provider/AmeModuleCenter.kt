package com.bcm.messenger.common.provider

import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.AccountJobManager
import com.bcm.messenger.common.core.AmeFileUploader
import com.bcm.messenger.common.database.DatabaseFactory
import com.bcm.messenger.common.database.db.UserDatabase
import com.bcm.messenger.common.metrics.ReportUtil
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.server.IServerConnectionDaemon
import com.bcm.messenger.common.server.IServerDataDispatcher
import com.bcm.messenger.common.server.ServerConnectionDaemon
import com.bcm.messenger.common.server.ServerDataDispatcher
import com.bcm.messenger.common.service.RotateSignedPreKeyListener
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.sdk.crashreport.ReportUtils
import org.whispersystems.jobqueue.JobManager
import org.whispersystems.jobqueue.dependencies.DependencyInjector

object AmeModuleCenter {
    private val serverConnectionDaemon: ServerConnectionDaemon = ServerConnectionDaemon()
    private val serverDataDispatcher: ServerDataDispatcher = ServerDataDispatcher()

    init {
        serverConnectionDaemon.setEventListener(serverDataDispatcher)
    }

    fun instance() {
        login().checkLoginAccountState()
    }

    fun init() {
        login().initModule()
        group().initModule()
        chat().initModule()
        contact().initModule()
        adhoc().initModule()
        user().initModule()
        metric().initModule()
    }

    fun unInit() {
        login().uninitModule()
        group().uninitModule()
        chat().uninitModule()
        contact().uninitModule()
        adhoc().uninitModule()
        user().uninitModule()
        metric().uninitModule()
    }

    fun login(): ILoginModule {
        return AmeProvider.get<ILoginModule>(ARouterConstants.Provider.PROVIDER_LOGIN_BASE)!!
    }

    fun group(): IGroupModule {
        return AmeProvider.get<IGroupModule>(ARouterConstants.Provider.PROVIDER_GROUP_BASE)!!
    }

    fun chat(): IChatModule {
        return AmeProvider.get<IChatModule>(ARouterConstants.Provider.PROVIDER_CONVERSATION_BASE)!!
    }

    fun contact(): IContactModule {
        return AmeProvider.get<IContactModule>(ARouterConstants.Provider.PROVIDER_CONTACTS_BASE)!!
    }

    fun adhoc(): IAdHocModule {
        return AmeProvider.get<IAdHocModule>(ARouterConstants.Provider.PROVIDER_AD_HOC)!!
    }

    fun user(): IUserModule {
        return AmeProvider.get<IUserModule>(ARouterConstants.Provider.PROVIDER_USER_BASE)!!
    }

    fun metric(): IMetricsModule {
        return AmeProvider.get<IMetricsModule>(ARouterConstants.Provider.REPORT_BASE)!!
    }

    fun app(): IAmeAppModule {
        return AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)!!
    }

    fun serverDispatcher(): IServerDataDispatcher {
        return serverDataDispatcher
    }

    fun serverDaemon(): IServerConnectionDaemon {
        return serverConnectionDaemon
    }

    fun accountJobMgr(): JobManager? {
        return if (AMESelfData.isLogin) {
            AccountJobManager.getManager(AppContextHolder.APP_CONTEXT,
                    DependencyInjector { },
                    AMESelfData.uid)
        } else null
    }

    fun onLoginStateChanged(newUid: String) {
        if (newUid.isNotEmpty()) {
            accountJobMgr()
            ALog.logForSecret("AmeModuleCenter", "updateAccount $newUid")
            AmeFileUploader.initDownloadPath(AppContextHolder.APP_CONTEXT)
            ReportUtils.setGUid(newUid)

            if (DatabaseFactory.isDatabaseExist(AppContextHolder.APP_CONTEXT)
                    && (!TextSecurePreferences.isDatabaseMigrated(AppContextHolder.APP_CONTEXT) || TextSecurePreferences.getMigrateFailedCount(AppContextHolder.APP_CONTEXT) == 3)) {
                val factory = DatabaseFactory.getInstance(AppContextHolder.APP_CONTEXT)
                factory?.reset(AppContextHolder.APP_CONTEXT, newUid)
                return
            } else {
                UserDatabase.resetDatabase()
            }

            ReportUtil.init()

            this.init()

            AmeProvider.get<IContactModule>(ARouterConstants.Provider.PROVIDER_CONTACTS_BASE)?.doForLogin()

            if (!adhoc().isAdHocMode()) {
                AmeDispatcher.mainThread.dispatch {
                    serverDaemon().startDaemon()
                    serverDaemon().startConnection()
                }

            }

            RotateSignedPreKeyListener.schedule(AppContextHolder.APP_CONTEXT)

            System.gc()

        } else {
            serverDaemon().stopConnection()
            serverDaemon().stopDaemon()

            unInit()
        }
    }

}