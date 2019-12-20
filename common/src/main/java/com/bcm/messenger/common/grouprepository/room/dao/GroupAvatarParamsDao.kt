package com.bcm.messenger.common.grouprepository.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bcm.messenger.common.grouprepository.room.entity.GroupAvatarParams

@Dao
interface GroupAvatarParamsDao {
    @Query("SELECT * FROM ${GroupAvatarParams.TABLE_NAME} WHERE gid = :gid")
    fun queryAvatarParams(gid: Long): GroupAvatarParams?

    @Query("SELECT * FROM ${GroupAvatarParams.TABLE_NAME} WHERE gid IN(:gidList)")
    fun queryAvatarParamsList(gidList: List<Long>): List<GroupAvatarParams>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveAvatarParams(paramsList: List<GroupAvatarParams>)

    @Query("SELECT * FROM ${GroupAvatarParams.TABLE_NAME} LIMIT 100 OFFSET :page")
    fun queryByPage(page: Int): List<GroupAvatarParams>
}