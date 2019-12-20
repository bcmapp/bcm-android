package com.bcm.messenger.common.grouprepository.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Created by HunTZ on 2019/5/17
 */
private const val FRIEND_REQUEST_TABLE_NAME = "friend_request"

@Entity(tableName = FRIEND_REQUEST_TABLE_NAME)
data class BcmFriendRequest(
        @ColumnInfo(name = "proposer") var proposer: String = "",
        @ColumnInfo(name = "timestamp") var timestamp: Long = 0L,
        @ColumnInfo(name = "memo") var memo: String = "",
        @ColumnInfo(name = "signature") var requestSignature: String = "",
        @ColumnInfo(name = "unread") var unread: Long = UNREAD,
        @ColumnInfo(name = "approve") var approve: Long = NOT_DONE
) {
    companion object {
        const val ACCEPT = 0L
        const val REJECT = 1L
        const val NOT_DONE = -1L

        const val READ = 0L
        const val UNREAD = 1L
    }

    @PrimaryKey(autoGenerate = true) var id: Long? = null

    fun accept() {
        approve = ACCEPT
    }

    fun reject() {
        approve = REJECT
    }

    fun isAccepted() = approve == ACCEPT

    fun isRejected() = approve == REJECT

    fun isNeverHandle() = approve == NOT_DONE

    fun isUnread() = unread == UNREAD

    fun setRead() {
        unread = READ
    }

    fun copy(): BcmFriendRequest {
        val newReq = BcmFriendRequest(proposer, timestamp, memo, requestSignature, unread, approve)
        newReq.id = id
        return newReq
    }
}