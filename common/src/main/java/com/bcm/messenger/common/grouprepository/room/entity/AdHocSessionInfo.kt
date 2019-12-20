package com.bcm.messenger.common.grouprepository.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = AdHocSessionInfo.TABLE_NAME)
data class AdHocSessionInfo(
        @PrimaryKey
        @ColumnInfo(name = "session_id")
        val sessionId: String = "",
        var cid: String = "",
        var uid: String = "",
        var pin: Boolean = false,
        var mute: Boolean = false,
        @ColumnInfo(name = "at_me")
        var atMe: Boolean = false,
        @ColumnInfo(name = "unread_count")
        var unreadCount: Int = 0,
        var timestamp: Long = 0,
        @ColumnInfo(name = "last_message")
        var lastMessage: String = "",
        @ColumnInfo(name = "last_state")
        var lastState: Int = AdHocMessageDBEntity.STATE_SUCCESS,
        var draft:String = "") {

    companion object {
        const val TABLE_NAME = "ad_hoc_sessions"


    }
}