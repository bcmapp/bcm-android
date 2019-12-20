package com.bcm.messenger.common.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Created by Kin on 2019/9/16
 */
@Entity(tableName = DraftDbModel.TABLE_NAME)
class DraftDbModel {
    companion object {
        const val TABLE_NAME = "drafts"
    }

    @PrimaryKey(autoGenerate = true)
    var id = 0L
    @ColumnInfo(name = "thread_id")
    var threadId = 0L
    var type = ""
    var value = ""
}