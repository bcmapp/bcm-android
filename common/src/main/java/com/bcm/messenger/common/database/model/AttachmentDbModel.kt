package com.bcm.messenger.common.database.model

import android.net.Uri
import androidx.room.*
import androidx.room.ForeignKey.CASCADE

/**
 * Created by Kin on 2019/9/16
 */
@Entity(tableName = AttachmentDbModel.TABLE_NAME,
        foreignKeys = [ForeignKey(entity = PrivateChatDbModel::class, parentColumns = ["id"], childColumns = ["mid"], onDelete = CASCADE)])
open class AttachmentDbModel {
    companion object {
        const val TABLE_NAME = "attachments"
    }

    @PrimaryKey(autoGenerate = true)
    var id = 0L
    var mid = 0L
    @ColumnInfo(name = "content_type")
    var contentType = ""
    @ColumnInfo(name = "type")
    var attachmentType = 0
    var name: String? = null
    @ColumnInfo(name = "file_name")
    var fileName: String? = null
    @ColumnInfo(name = "key")
    var contentKey = ""
    @ColumnInfo(name = "location")
    var contentLocation = ""
    @ColumnInfo(name = "transfer_state")
    var transferState = 0
    @ColumnInfo(name = "uri")
    var dataUri: Uri? = null
    @ColumnInfo(name = "size")
    var dataSize = 0L
    @ColumnInfo(name = "thumbnail_uri")
    var thumbnailUri: Uri? = null
    @ColumnInfo(name = "thumb_aspect_ratio")
    var thumbnailAspectRatio = 0f
    @ColumnInfo(name = "unique_id")
    var uniqueId = 0L
    var digest: ByteArray? = null
    @ColumnInfo(name = "fast_preflight_id")
    var fastPreflightId: String? = null
    var duration = 0L
    var url: String? = null
    @ColumnInfo(name = "data_random")
    var dataRandom: ByteArray? = null
    @ColumnInfo(name = "data_hash")
    var dataHash: String? = null
    @ColumnInfo(name = "thumb_random")
    var thumbRandom: ByteArray? = null
    @ColumnInfo(name = "thumb_hash")
    var thumbHash: String? = null

    enum class AttachmentType(val type: Int) {
        IMAGE(1),
        VIDEO(2),
        DOCUMENT(3),
        AUDIO(4),
        VOICE_NOTE(5)
    }

    enum class TransferState(val state: Int) {
        DONE(0),
        STARTED(1),
        PENDING(2),
        FAILED(3)
    }
}