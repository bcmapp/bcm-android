package com.bcm.messenger.common.grouprepository.manager

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

    fun clearHandlingList() {
        dao().clearHandlingList()
    }
    /**
     * 
     */
    fun saveHandlingList(list: List<BcmFriend>) {
        val dao = dao()
        dao.clearHandlingList()
        dao.saveFriends(list)
    }

    fun deleteHandlingList(list: List<BcmFriend>) {
        dao().deleteFriends(list)
    }

    /**
     * 
     */
    fun getHandingList(): List<BcmFriend> {
        return dao().queryHandingList()
    }

    /**
     * hash
     */
    fun saveSyncHashMap(map: Map<String, Long>) {
        val bcm = BcmFriend()
        bcm.uid = FRIEND_HASH_MAP
        bcm.state = BcmFriend.DATA
        bcm.tag = GsonUtils.toJson(map)
        dao().saveFriends(listOf(bcm))
    }

    /**
     * hash
     */
    fun loadLastSyncHashMap(): Map<String, Long>? {
        val tmp = dao().queryFriend(FRIEND_HASH_MAP)
        if (null != tmp) {
            try {
                return GsonUtils.fromJson<Map<String, Long>>(tmp.tag, object : TypeToken<Map<String, Long>>() {}.type)

            } catch (e: Exception) {
                ALog.e("BcmFriendManager", e)
            }
        }
        return null
    }

    private fun dao(): BcmFriendDao {
        return UserDatabase.getDatabase().bcmFriendDao()
    }
}