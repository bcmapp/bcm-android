package com.bcm.messenger.chats.group.logic.sync

import com.bcm.messenger.chats.group.logic.GroupMessageLogic
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager
import com.bcm.messenger.common.grouprepository.room.entity.GroupMessage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class GroupOfflineDecryptFailCounter(private val accountContext: AccountContext) {
    private data class FailState(val mid:Long, val counter:AtomicInteger = AtomicInteger())
    private val failMap = ConcurrentHashMap<Long, FailState>()

    fun updateFailCount(gid: Long, count:Int) {
        if (failMap[gid] == null) {
            failMap[gid] = FailState(initFailMessage(gid))
        }

        failMap[gid]?.counter?.addAndGet(count)
    }

    fun finishCounter(gid: Long) {
        val state = failMap.remove(gid)
        updateFailMessage(gid, state)
    }

    private fun initFailMessage(gid:Long): Long {
        return GroupMessageLogic.get(accountContext).systemNotice(gid, AmeGroupMessage.SystemContent(AmeGroupMessage.SystemContent.TIP_DECRYPT_FAIL), visible = false)
    }

    private fun updateFailMessage(gid:Long, state: FailState?) {
        if (null != state && state.counter.get() > 0) {
            val failCount = state.counter.get().toString()
            val message = MessageDataManager.queryOneMessage(accountContext, gid, state.mid, false)?:return
            message.is_confirm = GroupMessage.CONFIRM_MESSAGE
            val content = AmeGroupMessage.SystemContent(AmeGroupMessage.SystemContent.TIP_DECRYPT_FAIL, extra = failCount)
            message.text = AmeGroupMessage(AmeGroupMessage.SYSTEM_INFO, content).toString()
            MessageDataManager.insertSendMessage(accountContext, message)
        }
    }
}