package com.bcm.messenger.common.grouprepository.room.dao

import androidx.room.*
import com.bcm.messenger.common.grouprepository.room.entity.BcmFriendRequest

/**
 * Created by Kin on 2019/5/17
 */

@Dao
interface FriendRequestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(request: BcmFriendRequest): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(requestList: List<BcmFriendRequest>)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(request: BcmFriendRequest)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(requestList: List<BcmFriendRequest>)

    @Query("SELECT * FROM friend_request WHERE approve = -1 ORDER BY id DESC")
    fun queryAll(): List<BcmFriendRequest>

    @Query("SELECT * FROM friend_request WHERE id = :id")
    fun query(id: Long): BcmFriendRequest

    @Query("SELECT * FROM friend_request WHERE (proposer = :proposer AND approve = -1)")
    fun queryExistsRequests(proposer: String): List<BcmFriendRequest>

    @Query("SELECT * FROM friend_request WHERE approve = -1 ORDER BY id DESC LIMIT :pageSize OFFSET :page ")
    fun queryByPage(page: Int, pageSize: Int): List<BcmFriendRequest>

    @Query("SELECT COUNT(*) FROM friend_request WHERE approve = -1")
    fun queryUnhandledCount(): Int

    @Query("SELECT COUNT(*) FROM friend_request WHERE unread = 1")
    fun queryUnreadCount(): Int

    @Query("SELECT * FROM friend_request WHERE unread = 1 ORDER BY id DESC")
    fun queryUnreadRequest(): List<BcmFriendRequest>

    @Delete
    fun delete(request: BcmFriendRequest)

    @Delete
    fun delete(requestList: List<BcmFriendRequest>)
}