package com.bcm.messenger.adhoc.sdk

class AdHocChatMessage(val messageId: String,
                       val fromId: String,
                       val nickname: String,
                       val sessionName: String,
                       val text: String,
                       val timestamp: Long,
                       val isChannel: Boolean,
                       val fileDigest: String?) {
}