package com.bcm.messenger.common.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Created by Kin on 2019/9/16
 */
@Entity(tableName = PushDbModel.TABLE_NAME)
class PushDbModel {
    companion object {
        const val TABLE_NAME = "push"
    }

    @PrimaryKey(autoGenerate = true)
    var id = 0L
    var type = 0
    @ColumnInfo(name = "source_uid")
    var sourceUid = ""
    @ColumnInfo(name = "device_id")
    var deviceId = 0
    var content = ""
    @ColumnInfo(name = "legacy_msg")
    var legacyMessage = ""
    @ColumnInfo(name = "source_registration_id")
    var sourceRegistrationId = 0
    var timestamp = 0L
}