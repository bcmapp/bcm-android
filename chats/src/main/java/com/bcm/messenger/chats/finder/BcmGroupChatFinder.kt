package com.bcm.messenger.chats.finder

import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.finder.BcmFindData
import com.bcm.messenger.common.finder.IBcmFindResult
import com.bcm.messenger.common.finder.BcmFinderType
import com.bcm.messenger.common.finder.IBcmFinder

/**
 * Created by bcm.social.01 on 2019/4/8.
 */
class BcmGroupChatFinder: IBcmFinder {

    override fun type(): BcmFinderType {
        return BcmFinderType.GROUP_CHAT
    }

    override fun find(key: String): IBcmFindResult {
        return GroupChatFindResult()
    }

    override fun cancel() {

    }

    class GroupChatFindResult :IBcmFindResult {
        override fun get(position: Int): BcmFindData<AmeGroupMessage<*>>? {
            throw Exception("not support")
        }

        override fun count(): Int {
            return 0
        }

        override fun topN(n: Int): List<BcmFindData<AmeGroupMessage<*>>> {
            return listOf()
        }

        override fun toList(): List<BcmFindData<AmeGroupMessage<*>>> {
            return listOf()
        }

    }
}