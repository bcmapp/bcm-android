package com.bcm.messenger.common.grouprepository.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Created by Kin on 2019/5/8
 */
private const val CHAT_CONTROL_TABLE_NAME = "chat_hide_msg"

@Entity(tableName = CHAT_CONTROL_TABLE_NAME)
class ChatHideMessage {
    @PrimaryKey var id: Long? = null
    @ColumnInfo(name = "send_time") var sendTime = 0L
    @ColumnInfo(name = "body") var content = ""
    @ColumnInfo(name = "dest_addr") var destinationAddress = ""
}