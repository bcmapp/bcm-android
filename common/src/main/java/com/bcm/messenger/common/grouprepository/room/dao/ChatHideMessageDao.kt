package com.bcm.messenger.common.grouprepository.room.dao

import androidx.room.*
import com.bcm.messenger.common.grouprepository.room.entity.ChatHideMessage

/**
 * Created by Kin on 2019/5/8
 */
@Dao
interface ChatHideMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveHideMessage(message: ChatHideMessage): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveHideMessages(messages: List<ChatHideMessage>)

    @Query("SELECT * FROM chat_hide_msg WHERE id = :id")
    fun queryHideMessage(id: Long): ChatHideMessage

    @Query("DELETE FROM chat_hide_msg WHERE id = :id")
    fun deleteHideMessage(id: Long)

    @Delete
    fun deleteHideMessage(message: ChatHideMessage)

    @Query("SELECT * FROM chat_hide_msg LIMIT 100 OFFSET :page")
    fun queryByPage(page: Int): List<ChatHideMessage>
}