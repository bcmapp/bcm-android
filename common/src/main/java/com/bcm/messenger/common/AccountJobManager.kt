package com.bcm.messenger.common

import android.content.Context
import android.os.Looper
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.jobs.persistence.EncryptingJobSerializer
import com.bcm.messenger.common.jobs.requirements.MasterSecretRequirementProvider
import com.bcm.messenger.common.jobs.requirements.ServiceRequirementProvider
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.server.ServerConnectionDaemon
import org.whispersystems.jobqueue.JobManager
import org.whispersystems.jobqueue.dependencies.DependencyInjector
import org.whispersystems.jobqueue.requirements.NetworkRequirementProvider
import java.lang.AssertionError
import java.util.concurrent.ConcurrentHashMap

/**
 * bcm.social.01 2018/9/26.
 */
object AccountJobManager {
    @SuppressWarnings("all")
    private val mgrMap = ConcurrentHashMap<AccountContext, JobManager>()

    fun getManager(context:Context, accountContext:AccountContext, dependencyInjector: DependencyInjector, uid:String) : JobManager? {
        val mgr = mgrMap[accountContext]
        if (null != mgr) {
            return mgr
        }

        synchronized(mgrMap) {
            var mgr1 = mgrMap[accountContext]
            if (mgr1 != null) {
                return mgr1
            }

            ALog.logForSecret("AccountJobManager", "create manager uid: $uid, attachUid: ${accountContext.uid}")

            if (Looper.myLooper() == null) {
                if (BuildConfig.DEBUG) {
                    throw AssertionError("please call in looper thread")
                }
                return null
            }

            mgr1 = JobManager.newBuilder(context)
                    .withName("TextSecureJobs$uid")
                    .withDependencyInjector(dependencyInjector)
                    .withJobSerializer(EncryptingJobSerializer())
                    .withRequirementProviders(MasterSecretRequirementProvider(context),
                            ServiceRequirementProvider(context),
                            NetworkRequirementProvider(context))
                    .withConsumerThreads(5)
                    .build()
            mgrMap[accountContext] = mgr1
            return mgr1
        }
    }

    fun removeManager(accountContext: AccountContext){
        mgrMap.remove(accountContext)
    }
}