package com.bcm.messenger.chats.finder

import com.bcm.messenger.common.finder.BcmFindData
import com.bcm.messenger.common.finder.IBcmFindResult
import com.bcm.messenger.common.finder.BcmFinderType
import com.bcm.messenger.common.finder.IBcmFinder
import com.bcm.messenger.common.database.model.MessageRecord

/**
 * Created by bcm.social.01 on 2019/4/8.
 */
class BcmChatFinder : IBcmFinder {
    override fun type(): BcmFinderType {
        return BcmFinderType.CHAT
    }

    override fun find(key: String): IBcmFindResult {
        return ChatFindResult()
    }

    override fun cancel() {

    }

    class ChatFindResult :IBcmFindResult {
        override fun get(position: Int): BcmFindData<MessageRecord>? {
            throw Exception("not support")
        }

        override fun count(): Int {
            return 0
        }

        override fun topN(n: Int): List<BcmFindData<MessageRecord>> {
            throw Exception("not support")
        }

        override fun toList(): List<BcmFindData<MessageRecord>> {
            return listOf()
        }

    }
}