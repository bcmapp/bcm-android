package com.bcm.messenger.common.provider.accountmodule

import android.content.Context
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.provider.bean.ConversationStorage

interface IChatModule : IAmeAccountModule {
    /**
     * check rtc call incoming
     */
    fun checkHasRtcCall()

    /**
     * start web rtc call
     * @param context
     * @param address peer uid
     * @param callType #WebRtcCallService
     */
    fun startRtcCallService(context: Context, address: Address, callType: Int)

    /**
     * run rtc call UI
     */
    fun startRtcCallActivity(context: Context, callType: Int? = null)

    /**
     * delete group message list
     */
    fun deleteMessage(context: Context, isGroup: Boolean, conversationId: Long, messageSet: Set<Any>, callback: ((fail: Set<Any>) -> Unit)? = null)

    /**
     * recall group message
     */
    fun recallMessage(context: Context, isGroup: Boolean, messageRecord: Any, callback: ((success: Boolean) -> Unit)? = null)

    /**
     * forward group message
     */
    fun forwardMessage(context: Context, isGroup: Boolean, conversationId: Long, messageSet: Set<Any>, callback: ((fail: Set<Any>) -> Unit)? = null)

    /**
     * go to conversation media browser activity
     */
    fun toConversationBrowser(address: Address, deleteMode:Boolean)

    /**
     * collection media storage size
     */
    fun queryAllConversationStorageSize(accountContext: AccountContext, callback: ((result: ConversationStorage) -> Unit)?)

    /**
     * finish collection storage state
     */
    fun finishAllConversationStorageQuery()

}