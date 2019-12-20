package com.bcm.messenger.common.grouprepository.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bcm.messenger.common.grouprepository.room.entity.AdHocChannelInfo

@Dao
interface AdHocChannelDao {
    @Query("SELECT * FROM ${AdHocChannelInfo.TABLE_NAME} WHERE cid == :cid")
    fun queryChannel(cid: String):AdHocChannelInfo?

    @Query("SELECT * FROM ${AdHocChannelInfo.TABLE_NAME}")
    fun loadAllChannel():List<AdHocChannelInfo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveChannel(channel:AdHocChannelInfo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertChannels(channels: List<AdHocChannelInfo>)

    @Query("DELETE FROM ${AdHocChannelInfo.TABLE_NAME} where cid = :cid")
    fun deleteChannel(cid: String)
}