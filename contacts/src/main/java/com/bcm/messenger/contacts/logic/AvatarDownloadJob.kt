package com.bcm.messenger.contacts.logic

import android.content.Context
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeFileUploader
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.utility.Base64
import com.bcm.messenger.utility.bcmhttp.callback.FileDownCallback
import com.bcm.messenger.utility.logger.ALog
import okhttp3.Call
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 *
 * Created by wjh on 2019-12-31
 */
class AvatarDownloadJob(private val context: Context, private val accountContext: AccountContext, private val logic: BcmProfileLogic) : Runnable {

    private val TAG = "AvatarDownloadJob"
    private var mCurrentList: List<BcmProfileLogic.TaskData>? = null
    private var mToFinish: Boolean = false
    private var mCompleteCount = AtomicInteger(0)
    private var mFinishCount = 0

    private fun handleTaskData(dataList: List<BcmProfileLogic.TaskData>) {
        if (dataList.isEmpty()) return

        var count = 0
        dataList.forEach {
            if (it.type == BcmProfileLogic.TYPE_AVATAR_BOTH) {
                count += 2
            }else {
                count += 1
            }
        }
        mFinishCount += count
        val completeCount = AtomicInteger(0)
        val successList = mutableListOf<BcmProfileLogic.TaskData>()
        val failList = mutableListOf<BcmProfileLogic.TaskData>()

        fun checkDownloadFinish(complete: Int) {

            ALog.i(TAG, "onRun AvatarDownloadJob end, completeCount: ${completeCount.get()}")
            if (complete >= count) {
                ALog.d(TAG, "downloadAvatar onRun finish, success: ${successList.size}, fail: ${failList.size}")
                if (successList.isNotEmpty()) {
                    logic.doneHandling(successList, true)
                }
                if (failList.isNotEmpty()) {
                    logic.doneHandling(failList, false)
                }
            }

            ALog.i(TAG, "checkDownloadFinish: $mToFinish, completeCount: ${mCompleteCount.get()}, finishCount: $mFinishCount")
            if (mCompleteCount.addAndGet(1) >= mFinishCount && mToFinish) {
                logic.finishJob(this)
            }
        }

        ALog.d(TAG, "onRun AvatarDownloadJob begin recipient: $count")
        for (data in dataList) {
            val q = if (data.type == BcmProfileLogic.TYPE_AVATAR_BOTH) {
                arrayOf(true, false)
            }else if (data.type == BcmProfileLogic.TYPE_AVATAR_HD) {
                arrayOf(true)
            }else {
                arrayOf(false)
            }
            q.forEach {isHd ->
                downloadAvatar(isHd, data.recipient) {
                    ALog.i(TAG, "downloadAvatar callback finish")
                    if (it) {
                        successList.add(data)
                    } else {
                        failList.add(data)
                    }
                    checkDownloadFinish(completeCount.addAndGet(1))
                }
            }

        }
    }

    @Throws(Exception::class)
    override fun run() {
        var hasWait = false
        do {
            if (mCurrentList != null) {
                ALog.i(TAG, "continue handle last list")
                handleTaskData(mCurrentList ?: listOf())
                mCurrentList = null
            } else {
                val list = logic.getAvailableTaskDataListOfAvatar()
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

        mToFinish = true

        ALog.i(TAG, "onRun finish, completeCount: ${mCompleteCount.get()}, finishCount: $mFinishCount")
        if (mCompleteCount.get() >= mFinishCount) {
            logic.finishJob(this)
        }
    }

    private fun downloadAvatar(isHd: Boolean, recipient: Recipient, callback: (success: Boolean) -> Unit) {

        try {
            val privacyProfile = recipient.privacyProfile
            val avatarId = if (isHd) privacyProfile.avatarHD else privacyProfile.avatarLD
            if (avatarId.isNullOrEmpty()) {
                callback(false)
                return
            }
            val avatarUrl = if (avatarId.startsWith("http", true)) {
                avatarId
            } else {
                AmeFileUploader.get(accountContext).ATTACHMENT_URL + avatarId
            }
            ALog.d(TAG, "begin download avatar uid: ${recipient.address}, isHd: $isHd, url: $avatarUrl")

            val encryptFileName = BcmProfileLogic.getAvatarFileName(recipient, isHd, false)
            AmeFileUploader.get(accountContext).downloadFile(context, avatarUrl, object : FileDownCallback(AmeFileUploader.get(accountContext).ENCRYPT_DIRECTORY, encryptFileName) {

                override fun onError(call: Call?, e: java.lang.Exception?, id: Long) {
                    callback(false)
                }

                override fun onResponse(response: File?, id: Long) {
                    if (response == null) {
                        callback(false)
                    } else {
                        val targetFullFile = File(AmeFileUploader.get(accountContext).DECRYPT_DIRECTORY, BcmProfileLogic.getAvatarFileName(recipient, isHd, true))
                        try {
                            BCMEncryptUtils.decryptFileByAES256(response.absolutePath, targetFullFile.absolutePath, Base64.decode(privacyProfile.avatarKey))
                            val finalAvatarPath = File(AmeFileUploader.get(accountContext).DECRYPT_DIRECTORY, BcmProfileLogic.getAvatarFileName(recipient, isHd, false))
                            targetFullFile.renameTo(finalAvatarPath)
                            val finalUri = BcmFileUtils.getFileUri(finalAvatarPath.absolutePath)
                            if (isHd) {
                                BcmProfileLogic.clearAvatarResource(privacyProfile.avatarHDUri)
                                privacyProfile.avatarHDUri = finalUri.toString()
                                privacyProfile.isAvatarHdOld = false
                            } else {
                                BcmProfileLogic.clearAvatarResource(recipient.privacyProfile.avatarLDUri)
                                privacyProfile.avatarLDUri = finalUri.toString()
                                privacyProfile.isAvatarLdOld = false
                            }
                            ALog.d(TAG, "downloadAvatar done avatarHd: ${privacyProfile.avatarHDUri}, avatarLd: ${privacyProfile.avatarLDUri}")
                            Repository.getRecipientRepo(accountContext)?.setPrivacyProfile(recipient, privacyProfile)

                            if (recipient.isLogin) {
                                AmeModuleCenter.user(accountContext)?.saveAccount(recipient, null, recipient.privacyAvatar)
                            }
                            callback(true)

                        } catch (ex: Exception) {
                            ALog.e(TAG, "downloadAvatar isHd: $isHd error", ex)
                            targetFullFile.delete()
                            callback(false)
                        }
                    }
                }

            })

        } catch (ex: Exception) {
            ALog.e(TAG, "downloadAvatar isHd: $isHd error", ex)
            callback(false)
        }

    }

}