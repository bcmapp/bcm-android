package com.bcm.messenger.chats.finder

import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.finder.BcmFindData
import com.bcm.messenger.common.finder.IBcmFindResult
import com.bcm.messenger.common.finder.BcmFinderType
import com.bcm.messenger.common.finder.IBcmFinder
import com.bcm.messenger.common.recipients.Recipient

/**
 * Created by bcm.social.01 on 2019/4/8.
 */
class BcmGroupMemberFinder : IBcmFinder {

    override fun type(): BcmFinderType {
        return BcmFinderType.GROUP_MEMBER
    }

    override fun find(key: String): IBcmFindResult {
        return GroupMemberFindResult()
    }

    override fun findWithTarget(key: String, targetAddress: Address): IBcmFindResult {
        return GroupMemberFindResult()
    }

    override fun cancel() {

    }


    class GroupMemberFindResult :IBcmFindResult {
        override fun get(position: Int): BcmFindData<AmeGroupMemberInfo>? {
            return BcmFindData(AmeGroupMemberInfo())
        }

        override fun count(): Int {
            return 0
        }

        override fun topN(n: Int): List<BcmFindData<AmeGroupMemberInfo>> {
            return listOf()
        }

        override fun toList(): List<BcmFindData<Recipient>> {
            return listOf()
        }

    }
}