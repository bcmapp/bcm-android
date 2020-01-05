package com.bcm.messenger.common

import android.os.Looper
import com.bcm.messenger.common.jobs.persistence.EncryptingJobSerializer
import com.bcm.messenger.common.jobs.requirements.MasterSecretRequirementProvider
import com.bcm.messenger.common.jobs.requirements.ServiceRequirementProvider
import com.bcm.messenger.common.utils.AccountContextMap
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import org.whispersystems.jobqueue.JobManager
import org.whispersystems.jobqueue.requirements.NetworkRequirementProvider

/**
 * bcm.social.01 2018/9/26.
 */
object AccountJobManager:AccountContextMap<JobManager?>({
    ALog.logForSecret("AccountJobManager", "create manager uid: ${it.uid}")

    if (Looper.myLooper() == null) {
        if (BuildConfig.DEBUG) {
            throw AssertionError("please call in looper thread")
        }
        null
    } else {
        val context = AppContextHolder.APP_CONTEXT
        JobManager.newBuilder(context)
                .withName("TextSecureJobs${it.uid}")
                .withDependencyInjector { }
                .withJobSerializer(EncryptingJobSerializer())
                .withRequirementProviders(MasterSecretRequirementProvider(context),
                        ServiceRequirementProvider(context),
                        NetworkRequirementProvider(context))
                .withConsumerThreads(5)
                .build()
    }
})