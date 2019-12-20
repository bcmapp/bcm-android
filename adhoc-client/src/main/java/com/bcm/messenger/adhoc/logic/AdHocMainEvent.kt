package com.bcm.messenger.adhoc.logic

/**
 * adho event
 * Created by wjh on 2019/7/31
 */
data class AdHocMainEvent(val finishTop: Boolean, val chatEvent: ConversationEvent? = null, val notifyEvent: NotifyPopEvent? = null) {

    /**
     * chat event
     */
    class ConversationEvent(val channel: String)

    /**
     * pop event
     */
    class NotifyPopEvent(val success: Boolean, val message: CharSequence)
}