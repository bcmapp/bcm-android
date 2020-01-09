package com.bcm.messenger.common.database.repositories

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.database.records.MessageRecord

/**
 * Created by Kin on 2019/9/20
 */
class PrivateChatEvent(
        val accountContext: AccountContext,
        val threadId: Long,
        val type: EventType,
        val records: List<MessageRecord>,
        val ids: List<Long> = listOf()
) {
    enum class EventType {
        INSERT,
        UPDATE,
        DELETE,
        DELETE_ALL,
        DELETE_EXCEPT
    }
}