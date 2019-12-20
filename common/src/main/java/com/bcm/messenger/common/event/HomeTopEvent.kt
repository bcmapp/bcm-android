package com.bcm.messenger.common.event

import com.bcm.messenger.common.core.Address
import com.bcm.messenger.utility.GsonUtils

/**
 * 首页事件
 * Created by wjh on 2019/1/17
 */
data class HomeTopEvent(val finishTop: Boolean = true, val chatEvent: ConversationEvent? = null, val callEvent: RtcCallEvent? = null,
                        val notifyEvent: NotifyPopEvent? = null) {

    /**
     * 聊天事件
     */
    class ConversationEvent(val path: String, val threadId: Long, val address: Address?, val gid: Long? = null)

    /**
     * 视频通话事件
     */
    class RtcCallEvent(val address: Address)

    /**
     * 弹窗通知事件
     */
    class NotifyPopEvent(val success: Boolean, val message: String)

    override fun toString(): String {
        return GsonUtils.toJson(this)
    }
}