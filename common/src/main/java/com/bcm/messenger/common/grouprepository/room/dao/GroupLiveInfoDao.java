package com.bcm.messenger.common.grouprepository.room.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.bcm.messenger.common.grouprepository.room.entity.GroupLiveInfo;

import java.util.List;


@Dao
public interface GroupLiveInfoDao {


    @Query("SELECT * FROM " + GroupLiveInfo.TABLE_NAME + " WHERE " + "gid" + " = :gid " + " AND " + "liveId" + " = :liveId")
    GroupLiveInfo loadLiveInfoByGidAndLiveId(long gid, long liveId);

    /**
     * 
     *
     * @param gid
     * @return
     */
    @Query("SELECT * FROM " + GroupLiveInfo.TABLE_NAME + " WHERE " + "gid" + " = :gid " + " AND confirmed " + " ORDER BY start_time DESC " + " LIMIT 0 ,1 ")
    GroupLiveInfo loadLatestLiveInfoByGid(long gid);


    @Query("SELECT * FROM " + GroupLiveInfo.TABLE_NAME + " WHERE " + "_id" + " = :indexId ")
    GroupLiveInfo loadLiveInfoByIndexId(long indexId);


    @Insert
    long insert(GroupLiveInfo groupInfo);

    @Insert
    void insertLiveInfoList(List<GroupLiveInfo> infoList);

    @Delete
    void delete(GroupLiveInfo info);


    @Query("DELETE FROM " + GroupLiveInfo.TABLE_NAME + " WHERE " + "gid" + " = :gid ")
    void deleteLiveInfoByGid(long gid);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void update(GroupLiveInfo info);

    @Query("SELECT * FROM " + GroupLiveInfo.TABLE_NAME + " LIMIT 100 OFFSET :page")
    List<GroupLiveInfo> queryByPage(int page);
}
