package com.bcm.messenger.common.grouprepository.room.dao

import androidx.room.*
import com.bcm.messenger.common.grouprepository.room.entity.BcmFriend
import com.bcm.messenger.common.grouprepository.room.entity.GroupKey
import com.bcm.messenger.common.grouprepository.room.entity.GroupMessage

/**
 * bcm.social.01 2019/3/13.
 */
@Dao
interface GroupKeyDao {
    @Query("SELECT * FROM ${GroupKey.TABLE_NAME} WHERE (version IN (:versionList) AND gid = :gid)")
    fun queryKeys(gid:Long, versionList:List<Long>): List<GroupKey>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveKeys(listKeys:List<GroupKey>)

    @Query("SELECT * FROM ${GroupKey.TABLE_NAME} WHERE gid = :gid  ORDER BY version DESC  LIMIT 0 ,1 ")
    fun queryLastVersionKey(gid:Long): GroupKey?

    @Query("SELECT * FROM ${GroupKey.TABLE_NAME}")
    fun queryKeys(): List<GroupKey>
}