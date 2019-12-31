package com.bcm.messenger.contacts.logic

import android.annotation.SuppressLint
import android.content.Context
import com.bcm.messenger.common.jobs.ContextJob
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import org.whispersystems.jobqueue.JobParameters
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException

/**
 *
 * Created by wjh on 2019-12-31
 */
class ProfileFetchJob(context: Context, private val logic: BcmProfileLogic) : ContextJob(context,
        JobParameters.newBuilder().withGroupId("RecipientProfileLogic_profileFetchJob")
                .withRetryCount(3).create()) {

    private val TAG = "ProfileFetchJob"
    private var mCurrentList: List<BcmProfileLogic.TaskData>? = null

    override fun onAdded() {}

    @SuppressLint("CheckResult")
    private fun handleTaskData(dataList: List<BcmProfileLogic.TaskData>) {
        ALog.i(TAG, "onRun ProfileFetchJob dataList: ${dataList.size}")
        if (dataList.isEmpty()) return

        val addressList = dataList.filter { !it.recipient.isGroupRecipient }
        logic.getProfiles(addressList)
                .observeOn(AmeDispatcher.ioScheduler)
                .doOnError {
                    ALog.e(TAG, "handleTaskData", it)
                    logic.doneHandling(dataList, false)
                }
                .subscribe {
                    logic.doneHandling(dataList, true)
                }
    }

    @Throws(Exception::class)
    override fun onRun() {
        var hasWait = false
        do {
            if (mCurrentList != null) {
                ALog.i(TAG, "continue handle last list")
                handleTaskData(mCurrentList ?: listOf())
                mCurrentList = null
            } else {
                val list = logic.getAvailableTaskDataListOfProfile()
                mCurrentList = list
                if (list.isEmpty()) {
                    mCurrentList = null
                    ALog.i(TAG, "no more list to handle, hasWait: $hasWait")
                    if (hasWait) {
                        break
                    } else {
                        hasWait = true
                        Thread.sleep(BcmProfileLogic.TASK_HANDLE_DELAY)
                    }
                } else {
                    handleTaskData(list)
                    mCurrentList = null
                }
            }
        } while (true)

        logic.finishJob(this)
    }

    override fun onShouldRetry(e: Exception): Boolean {
        ALog.e(TAG, "retrieve profiles error", e)
        return e is PushNetworkException
    }

    override fun onCanceled() {}

}