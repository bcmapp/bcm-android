package com.bcm.messenger.common.grouprepository.room.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.bcm.messenger.common.grouprepository.room.entity.GroupInfo;

import org.jetbrains.annotations.NotNull;

import java.util.List;

@Dao
public interface GroupInfoDao {


    /**
     * @return
     */
    @Query("SELECT * FROM " + GroupInfo.TABLE_NAME)
    List<GroupInfo> loadAll();

    /**
     * gid
     *
     * @param gid
     * @return
     */
    @Query("SELECT * FROM " + GroupInfo.TABLE_NAME + " WHERE " + "gid" + " = :gid ")
    GroupInfo loadGroupInfoByGid(long gid);

    @Query("SELECT * FROM " + GroupInfo.TABLE_NAME + " WHERE gid IN (:gidList) ")
    List<GroupInfo> loadGroupInfoListByGid(long[] gidList);

    /**
     * @return
     */
    @Query("UPDATE " + GroupInfo.TABLE_NAME + " SET `key` = :key, key_version =:keyVersion WHERE gid = :gid")
    void updateGroupKey(long gid, long keyVersion, String key);

    /**
     * Info
     *
     * @return
     */
    @Query("UPDATE " + GroupInfo.TABLE_NAME + " SET group_info_secret = :infoSecret WHERE gid = :gid")
    void updateGroupInfoKey(long gid, String infoSecret);

    @Query("UPDATE " + GroupInfo.TABLE_NAME + " SET profile_encrypted = :isEncrypted WHERE gid = :gid")
    void setProfileEncrypted(long gid, boolean isEncrypted);

    /**
     * @return The number of group
     */
    @Query("SELECT COUNT(*) FROM " + GroupInfo.TABLE_NAME)
    int count();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdateAll(List<GroupInfo> list);

    @Insert
    void insert(GroupInfo groupInfo);

    @Delete
    void delete(GroupInfo info);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void update(GroupInfo info);

    @Query("UPDATE " + GroupInfo.TABLE_NAME + " SET share_link = :shareLink WHERE gid = :gid ")
    void updateShareShortLink(long gid, String shareLink);

    @Query("UPDATE " + GroupInfo.TABLE_NAME + " SET name = :name WHERE gid = :gid ")
    void updateName(long gid, String name);

    @Query("UPDATE " + GroupInfo.TABLE_NAME + " SET iconUrl = :icon WHERE gid = :gid ")
    void updateAvatar(long gid, String icon);

    @Query("UPDATE " + GroupInfo.TABLE_NAME + " SET member_sync_state = :syncSate WHERE gid = :gid ")
    void updateMemberSyncState(long gid, @NotNull String syncSate);

    @Query("UPDATE " + GroupInfo.TABLE_NAME + " SET role = :role WHERE gid = :gid ")
    void updateRole(long gid, @NotNull long role);

    @Query("UPDATE " + GroupInfo.TABLE_NAME + " SET share_code = \"\", share_qr_code_setting = \"\", share_sig=\"\", share_epoch = 0, share_enabled = 0, share_link = \"\", share_and_owner_confirm_sig = \"\" WHERE gid = :gid ")
    void clearShareSetting(long gid);

    @Query("SELECT member_count FROM " + GroupInfo.TABLE_NAME + " WHERE gid = :gid ")
    long queryMemberCount(long gid);

    @Query("UPDATE " + GroupInfo.TABLE_NAME + " SET member_count = :count WHERE gid = :gid ")
    void updateMemberCount(long gid, long count);
}
