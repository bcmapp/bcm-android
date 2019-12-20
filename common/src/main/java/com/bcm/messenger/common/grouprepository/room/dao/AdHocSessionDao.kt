package com.bcm.messenger.common.grouprepository.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bcm.messenger.common.grouprepository.room.entity.AdHocSessionInfo
import com.bcm.messenger.utility.AmeTimeUtil

@Dao
interface AdHocSessionDao {
    @Query("SELECT * FROM ${AdHocSessionInfo.TABLE_NAME} WHERE session_id == :sessionId")
    fun querySession(sessionId: String): AdHocSessionInfo?

    @Query("SELECT * FROM ${AdHocSessionInfo.TABLE_NAME} WHERE mute != 1 AND unread_count > 0")
    fun loadAllUnreadSession(): List<AdHocSessionInfo>

    @Query("SELECT * FROM ${AdHocSessionInfo.TABLE_NAME}")
    fun loadAllSession(): List<AdHocSessionInfo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveSession(session: AdHocSessionInfo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveSessions(sessions: List<AdHocSessionInfo>)

    @Query("DELETE FROM ${AdHocSessionInfo.TABLE_NAME} where session_id = :sessionId")
    fun deleteSession(sessionId: String)

    @Query("UPDATE ${AdHocSessionInfo.TABLE_NAME} SET mute = :mute, timestamp =:timestamp WHERE session_id = :sessionId")
    fun updateMute(sessionId: String, mute: Boolean, timestamp: Long = AmeTimeUtil.localTimeMillis())

    @Query("UPDATE ${AdHocSessionInfo.TABLE_NAME} SET draft = :draft, timestamp =:timestamp WHERE session_id = :sessionId")
    fun updateDraft(sessionId: String, draft: String, timestamp: Long = AmeTimeUtil.localTimeMillis())

    @Query("UPDATE ${AdHocSessionInfo.TABLE_NAME} SET pin = :pin, timestamp =:timestamp  WHERE session_id = :sessionId")
    fun updatePin(sessionId: String, pin: Boolean, timestamp: Long = AmeTimeUtil.localTimeMillis())

    @Query("UPDATE ${AdHocSessionInfo.TABLE_NAME} SET unread_count = :unreadCount, timestamp =:timestamp WHERE session_id = :sessionId")
    fun updateUnread(sessionId: String, unreadCount: Int, timestamp: Long = AmeTimeUtil.localTimeMillis())

    @Query("UPDATE ${AdHocSessionInfo.TABLE_NAME} SET last_message = :lastMessage, last_state = :lastState, timestamp =:timestamp WHERE session_id = :sessionId")
    fun updateLastMessage(sessionId: String, lastMessage: String, lastState: Int, timestamp: Long = AmeTimeUtil.localTimeMillis())

    @Query("UPDATE ${AdHocSessionInfo.TABLE_NAME} SET at_me = :atMe, timestamp =:timestamp WHERE session_id = :sessionId")
    fun updateAtMe(sessionId: String, atMe: Boolean, timestamp: Long = AmeTimeUtil.localTimeMillis())
}