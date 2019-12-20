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
     * 根据 gid 和 uid 查询一个群成员
     *
     * @param uid
     * @param gid
     * @return
     */
    @Query("SELECT * FROM " + GroupMember.TABLE_NAME + " WHERE uid = :uid AND gid = :gid")
    GroupMember queryGroupMember(Long gid, String uid);


    /**
     * 根据 gid 和 uid 查询一个群成员
     *
     * @param uidList
     * @param gid
     * @return
     */
    @Query("SELECT * FROM " + GroupMember.TABLE_NAME + " WHERE gid = :gid AND uid IN (:uidList)")
    List<GroupMember> queryGroupMemberList(Long gid, String[] uidList);

    /**
     * 根据 gid 和成员类型 查找群内的成员
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
     * 根据 gid 查找群内的成员
     *
     * @param gid
     * @return
     */
    @Query("SELECT * FROM " + GroupMember.TABLE_NAME + " WHERE gid = :gid ")
    List<GroupMember> loadGroupMembers(long gid);

    /**
     * 更新一个群成员信息
     *
     * @param user
     */
    @Delete
    void deleteGroupMember(GroupMember user);

    /**
     * 更新一个群成员信息
     *
     * @param users
     */
    @Delete
    void deleteGroupMember(List<GroupMember> users);

    /**
     * 清理所群的成员
     */
    @Query("DELETE FROM " + GroupMember.TABLE_NAME + " WHERE gid = :gid ")
    void clear(long gid);

    /**
     * 删除指定的成员
     */
    @Query("DELETE FROM " + GroupMember.TABLE_NAME + " WHERE gid = :gid AND uid in (:mlist) ")
    void deleteGroupMember(long gid, List<String> mlist);

    /**
     * 更新一个群成员信息
     *
     * @param user
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertGroupMember(GroupMember user);

    /**
     * 更新一个群成员信息
     *
     * @param users
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertGroupMember(List<GroupMember> users);

    /**
     * 更新一个群成员信息
     *
     * @param user
     */
    @Update(onConflict = OnConflictStrategy.REPLACE)
    void updateGroupMember(GroupMember user);

    /**
     * 更新一批群成员信息
     *
     * @param users
     */
    @Update(onConflict = OnConflictStrategy.REPLACE)
    void updateGroupMember(List<GroupMember> users);
}
