package com.bcm.messenger.common.event

import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.utility.GsonUtils

/**
 * 
 * Created by wjh on 2019/1/17
 */
data class HomeTopEvent(val finishTop: Boolean = true, val chatEvent: ConversationEvent? = null, val callEvent: RtcCallEvent? = null,
                        val notifyEvent: NotifyPopEvent? = null) {

    /**
     * 
     */
    class ConversationEvent(val path: String, val threadId: Long, val address: Address?, val gid: Long?, val createIfNotExist: Boolean) {

        companion object {
            fun fromGroupConversation(address: Address?, gid: Long): ConversationEvent {
                return ConversationEvent(ARouterConstants.Activity.CHAT_GROUP_CONVERSATION, 0L, address, gid, true)
            }

            fun fromPrivateConversation(address: Address, createIfNotExist: Boolean): ConversationEvent {
                return ConversationEvent(ARouterConstants.Activity.CHAT_CONVERSATION_PATH, 0L, address, null, createIfNotExist)
            }
        }
    }

    /**
     * 
     */
    class RtcCallEvent(val address: Address)

    /**
     * 
     */
    class NotifyPopEvent(val success: Boolean, val message: String)

    override fun toString(): String {
        return GsonUtils.toJson(this)
    }
}