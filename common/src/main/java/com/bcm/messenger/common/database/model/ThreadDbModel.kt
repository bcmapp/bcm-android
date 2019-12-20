package com.bcm.messenger.common.database.model

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Created by Kin on 2019/9/10
 */

@Entity(tableName = ThreadDbModel.TABLE_NAME)
open class ThreadDbModel {
    companion object {
        const val TABLE_NAME = "thread"
    }

    @PrimaryKey(autoGenerate = true)
    var id = 0L
    var timestamp = 0L
    @ColumnInfo(name = "message_count")
    var messageCount = 0L
    @ColumnInfo(name = "unread_count")
    var unreadCount = 0
    open var uid = ""
    @ColumnInfo(name = "snippet_content")
    var snippetContent = ""
    @ColumnInfo(name = "snippet_type")
    var snippetType = 0L
    @ColumnInfo(name = "snippet_uri")
    var snippetUri: Uri? = null
    @ColumnInfo(name = "read")
    var read = 0
    @ColumnInfo(name = "has_sent")
    var hasSent = 0
    @ColumnInfo(name = "distribution_type")
    var distributionType = 0
    @ColumnInfo(name = "expires_time")
    var expiresTime = 0L
    @ColumnInfo(name = "last_seen")
    var lastSeenTime = 0L
    @ColumnInfo(name = "pin_time")
    var pinTime = 0L
    @ColumnInfo(name = "live_state")
    var liveState = 0
    @ColumnInfo(name = "decrypt_fail_data")
    var decryptFailData = ""
    @ColumnInfo(name = "profile_req")
    var profileRequest = 0
}