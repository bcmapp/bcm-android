package com.bcm.messenger.common.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.bcm.messenger.common.database.model.ThreadDbModel
import com.bcm.messenger.common.database.records.ThreadRecord

/**
 * Created by Kin on 2019/9/16
 */
@Dao
interface ThreadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertThread(thread: ThreadDbModel): Long

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateThread(thread: ThreadDbModel)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateThreads(threads: List<ThreadDbModel>)

    @Delete
    fun deleteThread(thread: ThreadDbModel)

    @Query("DELETE FROM ${ThreadDbModel.TABLE_NAME} WHERE id = :threadId")
    fun deleteThread(threadId: Long)

    @Query("DELETE FROM ${ThreadDbModel.TABLE_NAME}")
    fun deleteAllThreads()

    @Query("SELECT * FROM ${ThreadDbModel.TABLE_NAME} ORDER BY pin_time DESC, timestamp DESC")
    fun queryAllThreads(): List<ThreadRecord>

    @Query("SELECT * FROM ${ThreadDbModel.TABLE_NAME} ORDER BY pin_time DESC, timestamp DESC")
    fun queryAllThreadsWithLiveData(): LiveData<List<ThreadRecord>>

    @Query("SELECT id FROM ${ThreadDbModel.TABLE_NAME} WHERE uid = :uid")
    fun queryThreadId(uid: String): Long

    @Query("SELECT * FROM ${ThreadDbModel.TABLE_NAME} WHERE id = :threadId")
    fun queryThread(threadId: Long): ThreadRecord?

    @Query("SELECT * FROM ${ThreadDbModel.TABLE_NAME} WHERE uid = :uid")
    fun queryThread(uid: String): ThreadRecord?

    // Update SQLs
    @Query("UPDATE ${ThreadDbModel.TABLE_NAME} SET snippet_content = :body, snippet_uri = :dataUri, timestamp = :date, snippet_type = :status WHERE id = :id")
    fun updateSnippet(id: Long, body: String?, dataUri: String?, date: Long, status: Long)

    @Query("UPDATE ${ThreadDbModel.TABLE_NAME} SET snippet_content = :body, snippet_uri = :dataUri, timestamp = :date, snippet_type = :status, message_count = :messageCount WHERE id = :id")
    fun updateSnippet(id: Long, body: String?, dataUri: String?, date: Long, status: Long, messageCount: Long)

    @Query("UPDATE ${ThreadDbModel.TABLE_NAME} SET read = 1, unread_count = 0 WHERE id = :id")
    fun updateRead(id: Long)

    @Query("UPDATE ${ThreadDbModel.TABLE_NAME} SET read = 1, unread_count = 0, last_seen = :lastSeenTime WHERE id = :id")
    fun updateReadAndLastSeen(id: Long, lastSeenTime: Long)

    @Query("UPDATE ${ThreadDbModel.TABLE_NAME} SET read = 0, unread_count = unread_count + :increaseUnreadCount WHERE id = :id")
    fun updateUnreadCount(id: Long, increaseUnreadCount: Int)

    @Query("UPDATE ${ThreadDbModel.TABLE_NAME} SET pin_time = :pinTime WHERE id = :id")
    fun updatePinTime(id: Long, pinTime: Long)

    @Query("UPDATE ${ThreadDbModel.TABLE_NAME} SET last_seen = :lastSeenTime WHERE id = :id")
    fun updateLastSeenTime(id: Long, lastSeenTime: Long)

    @Query("UPDATE ${ThreadDbModel.TABLE_NAME} SET decrypt_fail_data = :data WHERE id = :id")
    fun updateDecryptFailData(id: Long, data: String?)

    @Query("UPDATE ${ThreadDbModel.TABLE_NAME} SET has_sent = :hasSent WHERE id = :id")
    fun updateHasSent(id: Long, hasSent: Int)

    @Query("UPDATE ${ThreadDbModel.TABLE_NAME} SET read = :read, unread_count = :unreadCount WHERE id = :id")
    fun updateReadState(id: Long, read: Int, unreadCount: Int)

    @Query("UPDATE ${ThreadDbModel.TABLE_NAME} SET profile_req = :request WHERE id = :id")
    fun updateProfileRequest(id: Long, request: Int)

    @Query("UPDATE ${ThreadDbModel.TABLE_NAME} SET live_state = :status WHERE id = :id")
    fun updateLiveStatus(id: Long, status: Int)
}