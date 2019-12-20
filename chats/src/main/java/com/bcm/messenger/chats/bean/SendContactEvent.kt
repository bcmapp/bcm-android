package com.bcm.messenger.chats.bean

import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.utility.GsonUtils

/**
 * Event for send contact card
 *
 * Created by wjh on 2019-10-11
 */
class SendContactEvent(val groupId: Long, val dataList: List<AmeGroupMessage.ContactContent>, val comment: String?) {

    override fun toString(): String {
        return GsonUtils.toJson(this)
    }
}