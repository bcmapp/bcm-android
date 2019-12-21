package com.bcm.messenger.common.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Created by Kin on 2019/9/10
 */
@Entity(tableName = PrivateChatDbModel.TABLE_NAME)
open class PrivateChatDbModel {
    companion object {
        const val TABLE_NAME = "private_chat"
    }

    @PrimaryKey(autoGenerate = true)
    var id = 0L
    @ColumnInfo(name = "thread_id")
    var threadId = -1L
    var uid = ""
    @ColumnInfo(name = "address_device")
    var addressDevice = 0
    @ColumnInfo(name = "date_receive")
    var dateReceive = 0L
    @ColumnInfo(name = "date_sent")
    var dateSent = 0L
    var read = 0
    var type = 0L // ，Key，
    @ColumnInfo(name = "message_type")
    var messageType = 0
    var body = ""
    @ColumnInfo(name = "attachment_count")
    var attachmentCount = 0
    @ColumnInfo(name = "expires_time")
    var expiresTime = 0L
    @ColumnInfo(name = "expires_start")
    var expiresStartTime = 0L
    @ColumnInfo(name = "read_recipient_count")
    var readRecipientCount = 0
    @ColumnInfo(name = "delivery_receipt_count")
    var deliveryReceiptCount = 0
    @ColumnInfo(name = "call_type")
    var callType = 0
    @ColumnInfo(name = "call_duration")
    var callDuration = 0L
    @ColumnInfo(name = "payload_type")
    var payloadType = 0

    enum class MessageType(val type: Int) {
        TEXT(1),
        MEDIA(2),
        DOCUMENT(3),
        LOCATION(4),
        CALL(5)
    }

    enum class CallType(val type: Int) {
        AUDIO(0),
        VIDEO(1)
    }
}