package com.bcm.messenger.common.grouprepository.room.dao

import androidx.room.*
import com.bcm.messenger.common.grouprepository.room.entity.AdHocMessageDBEntity

@Dao
interface AdHocMessageDao {

    @Query("SELECT * FROM ${AdHocMessageDBEntity.TABLE_NAME} WHERE session_id == :session AND _id < :index ORDER BY _id DESC LIMIT :count")
    fun loadMessageBefore(session: String, index: Long, count: Int): List<AdHocMessageDBEntity>

    @Query("SELECT * FROM ${AdHocMessageDBEntity.TABLE_NAME} WHERE session_id == :session AND _id > :index ORDER BY _id DESC LIMIT :count")
    fun loadMessageAfter(session: String, index: Long, count: Int): List<AdHocMessageDBEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMessage(messageDBEntity: AdHocMessageDBEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMessages(messageDBEntityList: List<AdHocMessageDBEntity>)

    @Delete
    fun deleteMessage(messageDBEntityList: List<AdHocMessageDBEntity>)

    @Query("SELECT MAX(_id) FROM ${AdHocMessageDBEntity.TABLE_NAME} WHERE session_id == :session")
    fun queryMaxIndexId(session: String): Long

    @Query("SELECT * FROM ${AdHocMessageDBEntity.TABLE_NAME} WHERE session_id == :session ORDER BY _id DESC LIMIT 1")
    fun findLastMessage(session: String): AdHocMessageDBEntity?

    @Query("DELETE FROM ${AdHocMessageDBEntity.TABLE_NAME} WHERE session_id == :session")
    fun deleteAllMessage(session: String)

    @Query("UPDATE ${AdHocMessageDBEntity.TABLE_NAME} SET is_read = 1 WHERE session_id == :session")
    fun readAllMessage(session: String)

    @Update(onConflict = OnConflictStrategy.IGNORE)
    fun updateMessage(messageDBEntityList: List<AdHocMessageDBEntity>)

    @Query("UPDATE ${AdHocMessageDBEntity.TABLE_NAME} SET state = ${AdHocMessageDBEntity.STATE_FAILURE} WHERE session_id == :session AND state == ${AdHocMessageDBEntity.STATE_SENDING}")
    fun setSendingMessageFail(session: String)

    @Query("SELECT * FROM ${AdHocMessageDBEntity.TABLE_NAME} WHERE is_read == 1 AND session_id == :session ORDER BY _id DESC LIMIT 1")
    fun findLastSeen(session: String): AdHocMessageDBEntity?

    @Query("SELECT COUNT(*) FROM ${AdHocMessageDBEntity.TABLE_NAME} WHERE is_read != 1 AND session_id == :session ORDER BY _id DESC ")
    fun queryUnreadCount(session: String): Int

    @Query("SELECT * FROM ${AdHocMessageDBEntity.TABLE_NAME} WHERE message_id == :mid AND session_id == :session")
    fun findMessage(session: String, mid: String): AdHocMessageDBEntity?

    @Query("SELECT * FROM ${AdHocMessageDBEntity.TABLE_NAME} WHERE _id == :index AND session_id == :session")
    fun findMessage(session: String, index: Long): AdHocMessageDBEntity?

    @Query("SELECT * FROM ${AdHocMessageDBEntity.TABLE_NAME} LIMIT 100 OFFSET :page")
    fun loadByPage(page: Int): List<AdHocMessageDBEntity>
}