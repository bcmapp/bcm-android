package com.bcm.messenger.chats.group.logic.sync

import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.utility.logger.ALog

class GroupOfflineJoinReqMessageSyncTask(val gid:Long, private var delay:Long = 300, private val needConfirm:Boolean) {
    fun execute(accountContext: AccountContext, result: (succeed: Boolean) -> Unit) {
        val pageSize = 100L

        syncPage(accountContext, "", pageSize) {
            finished, succeed ->
            ALog.i("GroupOfflineJoinReqMessageSyncTask"," execute join req $gid succeed:$succeed finished:$finished")
            result(succeed)
        }
    }

    private fun syncPage(accountContext: AccountContext, start:String, count:Long, result: (finished:Boolean, succeed: Boolean) -> Unit) {
        GroupLogic.get(accountContext).fetchJoinRequestList(gid, delay, start, count) { succeed, list ->
            ALog.i("GroupOfflineJoinReqMessageSyncTask","syncPage join req $gid result:$succeed size:${list.size}")

            if (!accountContext.isLogin) {
                return@fetchJoinRequestList
            }

            if (succeed && !needConfirm) {
                if (succeed && list.isNotEmpty()) {
                    GroupLogic.get(accountContext).autoReviewJoinRequest(gid, list) { ok, error ->
                        ALog.i("GroupOfflineJoinReqMessageSyncTask", "syncPage autoReviewJoinRequest by offline message succeed:$ok, error:$error")
                    }
                }
            }

            if (!succeed) {
                ALog.i("GroupOfflineJoinReqMessageSyncTask"," syncPage join req $gid failed")
                result(false, false)
            } else {
                if (list.size.toLong() != count) {
                    result(true, true)
                } else {
                    syncPage(accountContext, list.last().uid, count, result)
                }
            }
        }
    }
}