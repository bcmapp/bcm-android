package com.bcm.messenger.common.grouprepository.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = AdHocMessageDBEntity.TABLE_NAME)
class AdHocMessageDBEntity {

    /**
     * The unique ID of the groupMessage.
     */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    var id: Long = 0

    /**
     * 会话ID
     */
    @ColumnInfo(index = true, name = "session_id")
    var sessionId: String = ""

    /**
     * 消息ID
     */
    @ColumnInfo(index = true, name = "message_id")
    var messageId: String = ""
    /**
     * 来源ID
     */
    @ColumnInfo(index = true, name = "from_id")
    var fromId: String = ""

    @ColumnInfo(name = "from_nick")
    var nickname: String = ""

    /**
     * message content
     */
    @ColumnInfo(name = "text")
    var text = ""

    /**
     * 状态（发送成功，发送失败，解析失败等）
     */
    @ColumnInfo(name = "state")
    var state: Int = STATE_FAILURE

    /**
     * 0：未读，1：已读
     */
    @ColumnInfo(name = "is_read")
    var read = STATE_UNREAD

    @ColumnInfo(name = "time")
    var time: Long = 0//创建时间

    /**
     * 0: send , 1. receive
     */
    @ColumnInfo(name = "is_send")
    var sentByMe = ACTION_SEND

    /**
     * 扩展内容（目前只有@内容）
     */
    @ColumnInfo(name = "ext_content")
    var extContent: String? = null

    @ColumnInfo(name = "attachment_uri")
    var attachmentUri: String? = null

    @ColumnInfo(name = "thumbnail_uri")
    var thumbnailUri: String? = null

    @ColumnInfo(name = "attachment_state")
    var attachmentState: Int = STATE_FAILURE

    companion object {
        const val TABLE_NAME = "adhoc_session_message"

        //消息阅读状态
        const val STATE_UNREAD = 0
        const val STATE_READ = 1


        //发送型消息或接收型消息
        const val ACTION_SEND = 0
        const val ACTION_RECEIVE = 1

        //消息状态
        const val STATE_SENDING = 2
        const val STATE_SUCCESS = 1
        const val STATE_FAILURE = 0

    }

}
