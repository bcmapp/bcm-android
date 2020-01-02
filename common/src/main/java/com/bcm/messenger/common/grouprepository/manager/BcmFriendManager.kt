package com.bcm.messenger.common.grouprepository.manager

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.database.db.UserDatabase
import com.bcm.messenger.common.grouprepository.room.dao.BcmFriendDao
import com.bcm.messenger.common.grouprepository.room.entity.BcmFriend
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.logger.ALog
import com.google.gson.reflect.TypeToken

/**
 * （）
 * Created by bcm.social.01 on 2019/3/13.
 */
class BcmFriendManager {

    companion object {
        private const val FRIEND_HASH_MAP = "uid_hash_map"
    }

    fun clearHandlingList(accountContext: AccountContext) {
        dao(accountContext).clearHandlingList()
    }

    /**
     * 
     */
    fun getHandingList(accountContext: AccountContext): List<BcmFriend> {
        return dao(accountContext).queryHandingList()
    }

    private fun dao(accountContext: AccountContext): BcmFriendDao {
        return UserDatabase.getDatabase(accountContext).bcmFriendDao()
    }
}