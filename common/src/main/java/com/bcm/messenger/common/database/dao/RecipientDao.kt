package com.bcm.messenger.common.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.bcm.messenger.common.database.model.IdentityDbModel
import com.bcm.messenger.common.database.model.RecipientDbModel
import com.bcm.messenger.common.database.records.RecipientSettings
import com.bcm.messenger.common.utils.GroupUtil

/**
 * Created by Kin on 2019/9/16
 */
@Dao
interface RecipientDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRecipient(recipient: RecipientDbModel): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRecipients(recipients: List<RecipientDbModel>)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateRecipient(recipient: RecipientDbModel): Int

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateRecipients(recipients: List<RecipientDbModel>): Int

    @Delete
    fun deleteRecipient(recipient: RecipientDbModel)

    @Query("DELETE FROM ${RecipientDbModel.TABLE_NAME} WHERE uid IN (:uids)")
    fun deleteRecipients(uids: Collection<String>)

    @Query("DELETE FROM ${RecipientDbModel.TABLE_NAME}")
    fun deleteAllRecipients()


    @Query("""
        SELECT recipient.uid, block, mute_until, expires_time, local_name, local_avatar, profile_key, profile_name, profile_avatar, profile_sharing_approval, privacy_profile, relationship, support_feature, identities.`key` AS identityKey 
        FROM ${RecipientDbModel.TABLE_NAME} 
        LEFT OUTER JOIN ${IdentityDbModel.TABLE_NAME} ON (${RecipientDbModel.TABLE_NAME}.uid = ${IdentityDbModel.TABLE_NAME}.uid) 
        WHERE recipient.uid = :uid
    """)
    fun queryRecipient(uid: String): RecipientSettings?

    @Query("""
        SELECT recipient.uid, block, mute_until, expires_time, local_name, local_avatar, profile_key, profile_name, profile_avatar, profile_sharing_approval, privacy_profile, relationship, support_feature, identities.`key` AS identityKey 
        FROM ${RecipientDbModel.TABLE_NAME} 
        LEFT OUTER JOIN ${IdentityDbModel.TABLE_NAME} ON (${RecipientDbModel.TABLE_NAME}.uid = ${IdentityDbModel.TABLE_NAME}.uid)
    """)
    fun queryAllRecipients(): List<RecipientSettings>

    @Query("""
        SELECT recipient.uid, block, mute_until, expires_time, local_name, local_avatar, profile_key, profile_name, profile_avatar, profile_sharing_approval, privacy_profile, relationship, support_feature, identities.`key` AS identityKey 
        FROM ${RecipientDbModel.TABLE_NAME} 
        LEFT OUTER JOIN ${IdentityDbModel.TABLE_NAME} ON (${RecipientDbModel.TABLE_NAME}.uid = ${IdentityDbModel.TABLE_NAME}.uid) 
        WHERE recipient.uid != :selfUid AND ${RecipientDbModel.TABLE_NAME}.uid NOT LIKE '${GroupUtil.ENCODED_TT_GROUP_PREFIX}%' AND ${RecipientDbModel.TABLE_NAME}.uid NOT LIKE '${GroupUtil.ENCODED_MMS_GROUP_PREFIX}%' AND ${RecipientDbModel.TABLE_NAME}.uid NOT LIKE '${GroupUtil.ENCODED_SIGNAL_GROUP_PREFIX}%' AND relationship = 1
    """)
    fun queryAllFriends(selfUid: String): List<RecipientSettings>

    @Query("""
        SELECT recipient.uid, block, mute_until, expires_time, local_name, local_avatar, profile_key, profile_name, profile_avatar, profile_sharing_approval, privacy_profile, relationship, support_feature, identities.`key` AS identityKey 
        FROM ${RecipientDbModel.TABLE_NAME} 
        LEFT OUTER JOIN ${IdentityDbModel.TABLE_NAME} ON (${RecipientDbModel.TABLE_NAME}.uid = ${IdentityDbModel.TABLE_NAME}.uid) 
        WHERE recipient.uid != :selfUid AND ${RecipientDbModel.TABLE_NAME}.uid NOT LIKE '${GroupUtil.ENCODED_TT_GROUP_PREFIX}%' AND ${RecipientDbModel.TABLE_NAME}.uid NOT LIKE '${GroupUtil.ENCODED_MMS_GROUP_PREFIX}%' AND ${RecipientDbModel.TABLE_NAME}.uid NOT LIKE '${GroupUtil.ENCODED_SIGNAL_GROUP_PREFIX}%' AND (relationship = 1 OR relationship = 2 OR relationship = 4 OR relationship = 5)
    """)
    fun queryAllFriendsAndFollowers(selfUid: String): List<RecipientSettings>

    @Query("""
        SELECT recipient.uid, block, mute_until, expires_time, local_name, local_avatar, profile_key, profile_name, profile_avatar, profile_sharing_approval, privacy_profile, relationship, support_feature, identities.`key` AS identityKey 
        FROM ${RecipientDbModel.TABLE_NAME} 
        LEFT OUTER JOIN ${IdentityDbModel.TABLE_NAME} ON (${RecipientDbModel.TABLE_NAME}.uid = ${IdentityDbModel.TABLE_NAME}.uid) 
        WHERE recipient.uid != :selfUid AND ${RecipientDbModel.TABLE_NAME}.uid NOT LIKE '${GroupUtil.ENCODED_TT_GROUP_PREFIX}%' AND ${RecipientDbModel.TABLE_NAME}.uid NOT LIKE '${GroupUtil.ENCODED_MMS_GROUP_PREFIX}%' AND ${RecipientDbModel.TABLE_NAME}.uid NOT LIKE '${GroupUtil.ENCODED_SIGNAL_GROUP_PREFIX}%' AND (relationship = 1 OR relationship = 2 OR relationship = 4 OR relationship = 5)
    """)
    fun queryFriendFollowersLiveData(selfUid: String): LiveData<List<RecipientSettings>>

    @Query("""
        SELECT recipient.uid, block, mute_until, expires_time, local_name, local_avatar, profile_key, profile_name, profile_avatar, profile_sharing_approval, privacy_profile, relationship, support_feature, identities.`key` AS identityKey 
        FROM ${RecipientDbModel.TABLE_NAME} 
        LEFT OUTER JOIN ${IdentityDbModel.TABLE_NAME} ON (${RecipientDbModel.TABLE_NAME}.uid = ${IdentityDbModel.TABLE_NAME}.uid) 
        WHERE recipient.uid != :selfUid AND ${RecipientDbModel.TABLE_NAME}.uid NOT LIKE '${GroupUtil.ENCODED_TT_GROUP_PREFIX}%' AND ${RecipientDbModel.TABLE_NAME}.uid NOT LIKE '${GroupUtil.ENCODED_MMS_GROUP_PREFIX}%' AND ${RecipientDbModel.TABLE_NAME}.uid NOT LIKE '${GroupUtil.ENCODED_SIGNAL_GROUP_PREFIX}%' AND block = 1
    """)
    fun queryAllBlockedRecipients(selfUid: String): List<RecipientSettings>

    @Query("""
        SELECT recipient.uid, block, mute_until, expires_time, local_name, local_avatar, profile_key, profile_name, profile_avatar, profile_sharing_approval, privacy_profile, relationship, support_feature, identities.`key` AS identityKey 
        FROM ${RecipientDbModel.TABLE_NAME} 
        LEFT OUTER JOIN ${IdentityDbModel.TABLE_NAME} ON (${RecipientDbModel.TABLE_NAME}.uid = ${IdentityDbModel.TABLE_NAME}.uid) 
        WHERE recipient.uid != :selfUid AND ${RecipientDbModel.TABLE_NAME}.uid NOT LIKE '${GroupUtil.ENCODED_TT_GROUP_PREFIX}%' AND ${RecipientDbModel.TABLE_NAME}.uid NOT LIKE '${GroupUtil.ENCODED_MMS_GROUP_PREFIX}%' AND ${RecipientDbModel.TABLE_NAME}.uid NOT LIKE '${GroupUtil.ENCODED_SIGNAL_GROUP_PREFIX}%' AND relationship != 0
    """)
    fun queryOneSideRecipients(selfUid: String): List<RecipientSettings>

    @Query("""
        SELECT recipient.uid, block, mute_until, expires_time, local_name, local_avatar, profile_key, profile_name, profile_avatar, profile_sharing_approval, privacy_profile, relationship, support_feature, identities.`key` AS identityKey 
        FROM ${RecipientDbModel.TABLE_NAME} 
        LEFT OUTER JOIN ${IdentityDbModel.TABLE_NAME} ON (${RecipientDbModel.TABLE_NAME}.uid = ${IdentityDbModel.TABLE_NAME}.uid) 
        WHERE recipient.uid LIKE '${GroupUtil.ENCODED_TT_GROUP_PREFIX}%'
    """)
    fun queryAllGroupRecipients(): List<RecipientSettings>

    @Query("""
        SELECT recipient.uid, block, mute_until, expires_time, local_name, local_avatar, profile_key, profile_name, profile_avatar, profile_sharing_approval, privacy_profile, relationship, support_feature, identities.`key` AS identityKey 
        FROM ${RecipientDbModel.TABLE_NAME} 
        LEFT OUTER JOIN ${IdentityDbModel.TABLE_NAME} ON (${RecipientDbModel.TABLE_NAME}.uid = ${IdentityDbModel.TABLE_NAME}.uid) 
        WHERE recipient.uid IN (:uids)
    """)
    fun queryRecipients(uids: Collection<String>): List<RecipientSettings>

    @RawQuery
    fun queryRecipients(query: SupportSQLiteQuery): List<RecipientSettings>

    // Update SQLs
    @Query("UPDATE ${RecipientDbModel.TABLE_NAME} SET support_feature = :feature WHERE uid = :uid")
    fun updateSupportFeature(uid: String, feature: String): Int

    @Query("UPDATE ${RecipientDbModel.TABLE_NAME} SET block = :block WHERE uid = :uid")
    fun updateBlock(uid: String, block: Int): Int

    @Query("UPDATE ${RecipientDbModel.TABLE_NAME} SET mute_until = :mute WHERE uid = :uid")
    fun updateMute(uid: String, mute: Long): Int

    @Query("UPDATE ${RecipientDbModel.TABLE_NAME} SET expires_time = :expireTime WHERE uid = :uid")
    fun updateExpireTime(uid: String, expireTime: Long): Int

    @Query("UPDATE ${RecipientDbModel.TABLE_NAME} SET profile_key = :profileKey WHERE uid = :uid")
    fun updateProfileKey(uid: String, profileKey: String?): Int

    @Query("UPDATE ${RecipientDbModel.TABLE_NAME} SET profile_name = :profileName WHERE uid = :uid")
    fun updateProfileName(uid: String, profileName: String?): Int

    @Query("UPDATE ${RecipientDbModel.TABLE_NAME} SET profile_avatar = :profileAvatar WHERE uid = :uid")
    fun updateProfileAvatar(uid: String, profileAvatar: String?): Int

    @Query("UPDATE ${RecipientDbModel.TABLE_NAME} SET profile_sharing_approval = :sharing WHERE uid = :uid")
    fun updateProfileSharing(uid: String, sharing: Int): Int

    @Query("UPDATE ${RecipientDbModel.TABLE_NAME} SET profile_name = :profileName, profile_key = :profileKey, profile_avatar = :profileAvatar WHERE uid = :uid")
    fun updateProfile(uid: String, profileName: String?, profileKey: String?, profileAvatar: String?): Int

    @Query("UPDATE ${RecipientDbModel.TABLE_NAME} SET local_name = :localName, local_avatar = :localAvatar WHERE uid = :uid")
    fun updateLocalProfile(uid: String, localName: String?, localAvatar: String?): Int

    @Query("UPDATE ${RecipientDbModel.TABLE_NAME} SET privacy_profile = :profile WHERE uid = :uid")
    fun updatePrivacyProfile(uid: String, profile: String): Int
}