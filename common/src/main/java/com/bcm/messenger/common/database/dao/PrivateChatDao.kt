package com.bcm.messenger.common.database.dao

import androidx.room.*
import com.bcm.messenger.common.database.model.AttachmentDbModel
import com.bcm.messenger.common.database.model.PrivateChatDbModel
import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.database.repositories.BASE_FAIL_CANNOT_DECRYPT_TYPE
import com.bcm.messenger.common.database.repositories.BASE_TYPE_MASK

/**
 * Created by Kin on 2019/9/16
 */
@Dao
interface PrivateChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertChatMessage(message: PrivateChatDbModel): Long

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateChatMessage(message: PrivateChatDbModel)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateChatMessages(messages: List<PrivateChatDbModel>)

    @Delete
    fun deleteChatMessage(message: PrivateChatDbModel)

    @Query("DELETE FROM ${PrivateChatDbModel.TABLE_NAME} WHERE id = :id")
    fun deleteChatMessage(id: Long)

    @Query("DELETE FROM ${PrivateChatDbModel.TABLE_NAME} WHERE thread_id = :threadId")
    fun deleteChatMessages(threadId: Long)

    @Query("DELETE FROM ${PrivateChatDbModel.TABLE_NAME} WHERE id IN (:ids)")
    fun deleteChatMessages(ids: List<Long>)

    @Query("DELETE FROM ${PrivateChatDbModel.TABLE_NAME} WHERE thread_id = :threadId AND id NOT IN (:ids)")
    fun deleteChatMessages(threadId: Long, ids: List<Long>)

    @Query("DELETE FROM ${PrivateChatDbModel.TABLE_NAME}")
    fun deleteAllChatMessages()

    @Query("SELECT * FROM ${PrivateChatDbModel.TABLE_NAME} WHERE id = :id")
    fun queryChatMessage(id: Long): MessageRecord?

    @Query("SELECT * FROM ${PrivateChatDbModel.TABLE_NAME} WHERE id in (:ids)")
    fun queryChatMessages(ids: List<Long>): List<MessageRecord>

    @Query("SELECT * FROM ${PrivateChatDbModel.TABLE_NAME} WHERE date_sent = :sendTime")
    fun queryChatMessageBySendTime(sendTime: Long): MessageRecord?

    @Query("SELECT * FROM ${PrivateChatDbModel.TABLE_NAME} WHERE date_sent = :sendTime")
    fun queryChatMessagesBySendTime(sendTime: Long): List<MessageRecord>

    @Query("SELECT * FROM ${PrivateChatDbModel.TABLE_NAME} WHERE date_sent = :sendTime AND thread_id = :threadId")
    fun queryChatMessagesBySendTime(threadId: Long, sendTime: Long): List<MessageRecord>

    @Query("SELECT * FROM ${PrivateChatDbModel.TABLE_NAME} WHERE thread_id = :threadId AND id < :lastId ORDER BY date_sent DESC LIMIT :pageSize")
    fun queryChatMessagesByPage(threadId: Long, lastId: Long, pageSize: Int): List<MessageRecord>

    @Query("SELECT * FROM ${PrivateChatDbModel.TABLE_NAME} WHERE thread_id = :threadId ORDER BY date_sent DESC")
    fun queryLatestMessage(threadId: Long): MessageRecord?

    @Query("SELECT COUNT(*) FROM ${PrivateChatDbModel.TABLE_NAME} WHERE thread_id = :threadId")
    fun queryMessageCount(threadId: Long): Long

    @Query("SELECT * FROM ${PrivateChatDbModel.TABLE_NAME} WHERE thread_id = :threadId AND read = 0")
    fun queryUnreadMessages(threadId: Long): List<MessageRecord>

    @Query("SELECT * FROM ${PrivateChatDbModel.TABLE_NAME} WHERE read = 0")
    fun queryUnreadMessages(): List<MessageRecord>

    @Query("SELECT COUNT(*) FROM ${PrivateChatDbModel.TABLE_NAME} WHERE thread_id = :threadId AND read = 0")
    fun queryUnreadMessageCount(threadId: Long): Long

    @Query("SELECT * FROM ${PrivateChatDbModel.TABLE_NAME} WHERE thread_id = :threadId AND (type & $BASE_TYPE_MASK) = $BASE_FAIL_CANNOT_DECRYPT_TYPE AND date_sent > :lastShowTime")
    fun queryDecryptFailedMessages(threadId: Long, lastShowTime: Long): List<MessageRecord>

    @Query("SELECT * FROM ${PrivateChatDbModel.TABLE_NAME} WHERE expires_start > 0")
    fun queryExpirationStartedMessages(): List<MessageRecord>

    @Query("SELECT * FROM ${PrivateChatDbModel.TABLE_NAME} WHERE thread_id = :threadId AND message_type = 2")
    fun queryMediaMessages(threadId: Long): MutableList<MessageRecord>

    @Query("SELECT * FROM ${PrivateChatDbModel.TABLE_NAME} WHERE thread_id = :threadId AND message_type = 3")
    fun queryFileMessages(threadId: Long): List<MessageRecord>

    @Query("SELECT * FROM ${PrivateChatDbModel.TABLE_NAME} WHERE thread_id = :threadId AND payload_type = 7")
    fun queryLinkMessages(threadId: Long): List<MessageRecord>

    @Query("SELECT date_sent FROM ${PrivateChatDbModel.TABLE_NAME} WHERE thread_id = :threadId AND (type & $BASE_TYPE_MASK) = $BASE_FAIL_CANNOT_DECRYPT_TYPE ORDER BY id DESC LIMIT 1")
    fun queryLastDecryptFail(threadId: Long): Long

    @Query("SELECT * FROM ${PrivateChatDbModel.TABLE_NAME} WHERE thread_id = :threadId AND message_type = 1 AND id > :lastId LIMIT :size")
    fun queryBackwardTextMessages(threadId: Long, lastId: Long, size: Int): List<MessageRecord>

    @Query("SELECT * FROM ${PrivateChatDbModel.TABLE_NAME} WHERE thread_id = :threadId AND message_type = 1 AND id < :lastId LIMIT :size")
    fun queryForwardTextMessages(threadId: Long, lastId: Long, size: Int): List<MessageRecord>

    // Update SQLs
    @Query("UPDATE ${PrivateChatDbModel.TABLE_NAME} SET date_sent = :dataSent WHERE id = :id")
    fun updateDataSent(id: Long, dataSent: Long)

    @Query("UPDATE ${PrivateChatDbModel.TABLE_NAME} SET type = :type WHERE id = :id")
    fun updateType(id: Long, type: Long)

    @Query("UPDATE ${PrivateChatDbModel.TABLE_NAME} SET read_recipient_count = read_recipient_count + 1 WHERE id = :id")
    fun updateReadRecipientCount(id: Long)

    @Query("UPDATE ${PrivateChatDbModel.TABLE_NAME} SET delivery_receipt_count = delivery_receipt_count + 1 WHERE id = :id")
    fun updateDeliveryRecipientCount(id: Long)

    @Query("UPDATE ${PrivateChatDbModel.TABLE_NAME} SET expires_start = :startTime WHERE id = :id")
    fun updateExpireStart(id: Long, startTime: Long)

    @Query("UPDATE ${PrivateChatDbModel.TABLE_NAME} SET read = 1 WHERE id = :id")
    fun updateMessageRead(id: Long)

    @Query("UPDATE ${PrivateChatDbModel.TABLE_NAME} SET read = 0 WHERE id = :id")
    fun updateMessageUnread(id: Long)


}