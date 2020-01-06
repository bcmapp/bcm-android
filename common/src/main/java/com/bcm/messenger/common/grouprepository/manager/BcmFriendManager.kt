package com.bcm.messenger.common.grouprepository.manager

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.room.dao.BcmFriendDao
import com.bcm.messenger.common.grouprepository.room.entity.BcmFriend

/**
 * Created by bcm.social.01 on 2019/3/13.
 */
class BcmFriendManager {

    companion object {
        private const val FRIEND_HASH_MAP = "uid_hash_map"
    }

    fun clearHandlingList(accountContext: AccountContext) {
        dao(accountContext)?.clearHandlingList()
    }

    /**
     * 
     */
    fun getHandingList(accountContext: AccountContext): List<BcmFriend> {
        return dao(accountContext)?.queryHandingList() ?: listOf()
    }

    private fun dao(accountContext: AccountContext): BcmFriendDao? {
        return Repository.getBcmFriendRepo(accountContext)
    }
}