package com.bcm.messenger.common.grouprepository.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bcm.messenger.common.core.corebean.BcmGroupJoinStatus
import com.bcm.messenger.common.grouprepository.room.entity.GroupJoinRequestInfo

@Dao
interface GroupJoinInfoDao {
    @Query("SELECT * FROM ${GroupJoinRequestInfo.TABLE_NAME} WHERE gid == :gid ORDER BY timestamp DESC")
    fun queryJoinListByGid(gid:Long):List<GroupJoinRequestInfo>

    @Query("SELECT * FROM ${GroupJoinRequestInfo.TABLE_NAME} WHERE req_id IN(:reqIdList)")
    fun queryJoinInfoByReqId(reqIdList:List<Long>): List<GroupJoinRequestInfo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveJoinInfos(list:List<GroupJoinRequestInfo>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun updateJoinInfos(list:List<GroupJoinRequestInfo>)

    @Query("SELECT * FROM ${GroupJoinRequestInfo.TABLE_NAME} WHERE gid = :gid AND uid = :uid AND status = :status AND inviter = :inviter")
    fun queryByDetail(gid:Long, uid:String, status:Int, inviter:String): GroupJoinRequestInfo?

    @Query("SELECT * FROM ${GroupJoinRequestInfo.TABLE_NAME} WHERE gid = :gid AND timestamp = :timestamp AND inviter = :inviter AND uid = :uid")
    fun queryByInviteData(gid: Long, inviter: String, uid:String, timestamp: Long): GroupJoinRequestInfo?

    @Query("SELECT * FROM ${GroupJoinRequestInfo.TABLE_NAME} WHERE gid = :gid AND uid IN(:uidList) AND status == 1")
    fun queryByUid(gid: Long, uidList:List<String>): List<GroupJoinRequestInfo>

    @Query("SELECT * FROM ${GroupJoinRequestInfo.TABLE_NAME} LIMIT 100 OFFSET :page")
    fun queryByPage(page: Int): List<GroupJoinRequestInfo>
}