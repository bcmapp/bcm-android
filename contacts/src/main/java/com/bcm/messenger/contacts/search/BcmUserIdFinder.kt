package com.bcm.messenger.contacts.search

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.finder.BcmFindData
import com.bcm.messenger.common.finder.BcmFinderType
import com.bcm.messenger.common.finder.IBcmFindResult
import com.bcm.messenger.common.finder.IBcmFinder
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.recipients.Recipient
import kotlin.math.min

class BcmUserIdFinder(private val accountContext: AccountContext): IBcmFinder {
    private val EMPTY = BcmUserIdFindResult(listOf())
    override fun type(): BcmFinderType {
        return BcmFinderType.USER_ID
    }

    override fun find(key: String): IBcmFindResult {
        return if (Address.isUid(key)) {
            val recipient = AmeModuleCenter.contact(accountContext)?.fetchProfile(key)
            return if(null != recipient) {
                BcmUserIdFindResult(listOf(BcmFindData(recipient)))
            } else {
                EMPTY
            }
        } else {
            EMPTY
        }
    }

    override fun cancel() {

    }

    private class BcmUserIdFindResult(private val list:List<BcmFindData<Recipient>>):IBcmFindResult {
        override fun get(position: Int): BcmFindData<Recipient>? {
            return list[position]
        }

        override fun count(): Int {
            return list.size
        }

        override fun topN(n: Int): List<BcmFindData<Recipient>> {
            return list.subList(0, min(n, list.size))
        }

        override fun toList(): List<BcmFindData<Recipient>> {
            return list
        }

    }
}