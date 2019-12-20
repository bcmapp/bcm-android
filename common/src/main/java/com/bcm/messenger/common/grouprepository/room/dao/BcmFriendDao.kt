package com.bcm.messenger.common.grouprepository.room.dao

import androidx.room.*
import com.bcm.messenger.common.grouprepository.room.entity.BcmFriend

/**
 * bcm.social.01 2019/3/13.
 */
@Dao
interface BcmFriendDao {

    @Query("SELECT * FROM ${BcmFriend.TABLE_NAME} WHERE (state = ${BcmFriend.CONTACT} OR state = ${BcmFriend.ADDING})")
    fun queryFriendList(): List<BcmFriend>

    @Query("SELECT * FROM ${BcmFriend.TABLE_NAME} WHERE state = ${BcmFriend.ADDING}")
    fun queryAddingList(): List<BcmFriend>

    @Query("SELECT * FROM ${BcmFriend.TABLE_NAME} WHERE state = ${BcmFriend.DELETING}")
    fun queryDeletedList(): List<BcmFriend>

    @Query("SELECT * FROM ${BcmFriend.TABLE_NAME} WHERE uid = :uid and (state = ${BcmFriend.ADDING} OR state = ${BcmFriend.DELETING})")
    fun queryHandingList(uid: String): BcmFriend

    @Query("SELECT * FROM ${BcmFriend.TABLE_NAME} WHERE (state = ${BcmFriend.ADDING} OR state = ${BcmFriend.DELETING})")
    fun queryHandingList(): List<BcmFriend>

    @Query("SELECT * FROM ${BcmFriend.TABLE_NAME} WHERE uid = :uid")
    fun queryFriend(uid: String): BcmFriend?

    @Delete
    fun deleteFriends(list: List<BcmFriend>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveFriends(list: List<BcmFriend>)

    @Query("DELETE FROM ${BcmFriend.TABLE_NAME} WHERE (state = ${BcmFriend.ADDING} OR state = ${BcmFriend.DELETING})")
    fun clearHandlingList()

    @Query("SELECT * FROM ${BcmFriend.TABLE_NAME}")
    fun queryAll(): List<BcmFriend>
}