package com.bcm.messenger.adhoc.logic

import com.bcm.messenger.adhoc.util.AdHocUtil
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.utility.AppContextHolder

data class AdHocSession(val sessionId:String,
                        val cid:String,
                        val uid:String,
                        var pin:Boolean = false,
                        var mute:Boolean = false,
                        var atMe:Boolean = false,
                        var unreadCount:Int = 0,
                        var timestamp:Long = 0,
                        var lastMessage:String = "",
                        var lastState: Int = STATE_SUCCESS,
                        var draft:String = "") {

    private var mRecipient: Recipient? = null

    companion object {
        const val STATE_SENDING = 2
        const val STATE_SUCCESS = 1
        const val STATE_FAILURE = 0
    }

    fun isChat(): Boolean {
        return uid.isNotEmpty()
    }

    fun isChannel(): Boolean {
        return cid.isNotEmpty()
    }


    fun updateRecipient(recipient: Recipient) {
        if (recipient.address.serialize() == uid) {
            mRecipient = recipient
        }
    }

    fun getChatRecipient(): Recipient? {
        return mRecipient
    }


    fun displayName(): String {
        return if (isChannel()) {
            AdHocChannelLogic.getChannel(cid)?.viewName() ?: cid
        } else {
            if (mRecipient == null) {
                mRecipient = Recipient.from(AppContextHolder.APP_CONTEXT, Address.fromSerialized(uid), true)
            }
            mRecipient?.name ?: uid
        }
    }


    fun isValid(): Boolean {
        return if (isChannel()) {
            AdHocChannelLogic.isOnline()
        } else {
            AdHocChannelLogic.getChannelUser(AdHocUtil.officialSessionId(), uid) != null
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AdHocSession

        if (sessionId != other.sessionId) return false

        return true
    }

    override fun hashCode(): Int {
        return sessionId.hashCode()
    }


}