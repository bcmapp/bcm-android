package com.bcm.messenger.common

import android.content.Context
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.common.jobs.persistence.EncryptingJobSerializer
import com.bcm.messenger.common.jobs.requirements.MasterSecretRequirementProvider
import com.bcm.messenger.common.jobs.requirements.ServiceRequirementProvider
import org.whispersystems.jobqueue.JobManager
import org.whispersystems.jobqueue.dependencies.DependencyInjector
import org.whispersystems.jobqueue.requirements.NetworkRequirementProvider

/**
 * bcm.social.01 2018/9/26.
 */
object AccountJobManager {
    @SuppressWarnings("all")
    private var jobManager:JobManager? = null
    private var attachUid:String = ""

    fun getManager(context:Context, dependencyInjector: DependencyInjector, uid:String) : JobManager {
        var manager = jobManager
        if (manager != null){
            if (uid == attachUid){
                return manager
            }
        }
        ALog.logForSecret("AccountJobManager", "create manager uid: $uid, attachUid: $attachUid")

        attachUid = uid
        manager = JobManager.newBuilder(context)
                .withName("TextSecureJobs$uid")
                .withDependencyInjector(dependencyInjector)
                .withJobSerializer(EncryptingJobSerializer())
                .withRequirementProviders(MasterSecretRequirementProvider(context),
                        ServiceRequirementProvider(context),
                        NetworkRequirementProvider(context))
                .withConsumerThreads(5)
                .build()

        jobManager = manager

        return manager
    }

    fun clear(){
        jobManager = null
        attachUid = ""
    }

}