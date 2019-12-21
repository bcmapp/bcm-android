package com.bcm.messenger.common.grouprepository.room.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.bcm.messenger.common.grouprepository.room.entity.GroupMember;
import java.util.List;

@Dao
public interface GroupMemberDao {
    /**
     *  gid  uid 
     *
     * @param uid
     * @param gid
     * @return
     */
    @Query("SELECT * FROM " + GroupMember.TABLE_NAME + " WHERE uid = :uid AND gid = :gid")
    GroupMember queryGroupMember(Long gid, String uid);


    /**
     *  gid  uid 
     *
     * @param uidList
     * @param gid
     * @return
     */
    @Query("SELECT * FROM " + GroupMember.TABLE_NAME + " WHERE gid = :gid AND uid IN (:uidList)")
    List<GroupMember> queryGroupMemberList(Long gid, String[] uidList);

    /**
     *  gid  
     *
     * @param gid
     * @param role
     * @return
     */
    @Query("SELECT * FROM " + GroupMember.TABLE_NAME + " WHERE gid = :gid " + " AND role = :role")
    List<GroupMember> loadGroupMembersByGidAndRole(long gid, int role);

    @Query("SELECT * FROM " + GroupMember.TABLE_NAME + " WHERE gid = :gid ORDER BY join_time ASC limit 0, :count")
    List<GroupMember> queryGroupMemberList(long gid, long count);

    @Query("SELECT * FROM " + GroupMember.TABLE_NAME + " LIMIT 500 OFFSET :page")
    List<GroupMember> queryGroupMemberByPage(int page);

    /**
     *  gid 
     *
     * @param gid
     * @return
     */
    @Query("SELECT * FROM " + GroupMember.TABLE_NAME + " WHERE gid = :gid ")
    List<GroupMember> loadGroupMembers(long gid);

    /**
     * 
     *
     * @param user
     */
    @Delete
    void deleteGroupMember(GroupMember user);

    /**
     * 
     *
     * @param users
     */
    @Delete
    void deleteGroupMember(List<GroupMember> users);

    /**
     * 
     */
    @Query("DELETE FROM " + GroupMember.TABLE_NAME + " WHERE gid = :gid ")
    void clear(long gid);

    /**
     * 
     */
    @Query("DELETE FROM " + GroupMember.TABLE_NAME + " WHERE gid = :gid AND uid in (:mlist) ")
    void deleteGroupMember(long gid, List<String> mlist);

    /**
     * 
     *
     * @param user
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertGroupMember(GroupMember user);

    /**
     * 
     *
     * @param users
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertGroupMember(List<GroupMember> users);

    /**
     * 
     *
     * @param user
     */
    @Update(onConflict = OnConflictStrategy.REPLACE)
    void updateGroupMember(GroupMember user);

    /**
     * 
     *
     * @param users
     */
    @Update(onConflict = OnConflictStrategy.REPLACE)
    void updateGroupMember(List<GroupMember> users);
}
