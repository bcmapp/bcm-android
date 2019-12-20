package com.bcm.messenger.adhoc.sdk

import com.bcm.messenger.utility.proguard.NotGuard
import com.bcm.messenger.utility.GsonUtils
import com.google.gson.reflect.TypeToken

class AdHocMessagePack {
    companion object {
        const val CHAT = 1
        const val ACK = 2
        const val FILE_CHAT = 3

        fun contentFrom(text: String): Content? {
            try {
                val msgContent = GsonUtils.fromJson<Content>(text, object : TypeToken<Content>() {}.type)
                return when (msgContent.type) {
                    CHAT -> return GsonUtils.fromJson(text, ChatMessage::class.java)
                    ACK -> return GsonUtils.fromJson(text, ACKMessage::class.java)
                    FILE_CHAT -> return GsonUtils.fromJson(text, FileChatMessage::class.java)
                    else -> null
                }
            } catch (ignored: Throwable) {
               return null
            }
        }

    }


    open class Content(var type: Int): NotGuard {
        override fun toString(): String {
            return GsonUtils.toJson(this)
        }
    }

    class ChatMessage(var atList:List<String>? = null,
                      var message:String = ""):Content(CHAT)

    class FileChatMessage(val message:String = "", val size:Long, val digest:String):Content(FILE_CHAT)

    class ACKMessage(val mid:String):Content(ACK)
}