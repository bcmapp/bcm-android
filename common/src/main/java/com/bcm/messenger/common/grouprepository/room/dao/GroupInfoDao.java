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
     * 拿到群信息列表数据
     *
     * @return
     */
    @Query("SELECT * FROM " + GroupInfo.TABLE_NAME)
    List<GroupInfo> loadAll();

    /**
     * 根据 gid 查询到群信息
     *
     * @param gid
     * @return
     */
    @Query("SELECT * FROM " + GroupInfo.TABLE_NAME + " WHERE " + "gid" + " = :gid ")
    GroupInfo loadGroupInfoByGid(long gid);

    @Query("SELECT * FROM " + GroupInfo.TABLE_NAME + " WHERE gid IN (:gidList) ")
    List<GroupInfo> loadGroupInfoListByGid(long[] gidList);

    /**
     * 根据 shareUrl 查询到群信息
     *
     * @param shareUrl
     * @return
     */
    @Query("SELECT * FROM " + GroupInfo.TABLE_NAME + " WHERE " + "share_url" + " = :shareUrl ")
    GroupInfo loadGroupInfoByShareUrl(String shareUrl);


    /**
     * 更新群消息的密钥
     *
     * @return
     */
    @Query("UPDATE " + GroupInfo.TABLE_NAME + " SET `key` = :key, key_version =:keyVersion WHERE gid = :gid")
    void updateGroupKey(long gid, long keyVersion, String key);

    /**
     * 更新群Info的密钥
     *
     * @return
     */
    @Query("UPDATE " + GroupInfo.TABLE_NAME + " SET group_info_secret = :infoSecret WHERE gid = :gid")
    void updateGroupInfoKey(long gid, String infoSecret);

    @Query("UPDATE " + GroupInfo.TABLE_NAME + " SET profile_encrypted = :isEncrypted WHERE gid = :gid")
    void setProfileEncrypted(long gid, boolean isEncrypted);

    /**
     * 查询群数量
     *
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

    @Query("UPDATE " + GroupInfo.TABLE_NAME + " SET name = :name, iconUrl = :icon WHERE gid = :gid ")
    void updateNameAndAvatar(long gid, String name, String icon);

    @Query("UPDATE " + GroupInfo.TABLE_NAME + " SET member_sync_state = :syncSate WHERE gid = :gid ")
    void updateMemberSyncState(long gid, @NotNull String syncSate);

    @Query("UPDATE " + GroupInfo.TABLE_NAME + " SET role = :role WHERE gid = :gid ")
    void updateRole(long gid, @NotNull long role);

    @Query("UPDATE " + GroupInfo.TABLE_NAME + " SET share_code = \"\", share_qr_code_setting = \"\", share_sig=\"\", share_epoch = 0, share_enabled = 0, share_link = \"\", share_and_owner_confirm_sig = \"\" WHERE gid = :gid ")
    void clearShareSetting(long gid);
}
