package com.bcm.messenger.common.grouprepository.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = NoteRecord.TABLE_NAME)
class NoteRecord(@PrimaryKey
                 @ColumnInfo(name = "_id")
                 var topicId:String = "",
                 var topic:String = "",
                 var defaultTopic:String = "",
                 var timestamp:Long = 0L,
                 var author:String = "",
                 var pin:Boolean = false,
                 @ColumnInfo(name = "edit_position")
                 var editPosition:Int = 0,
                 @ColumnInfo(name = "note_url")
                 var noteUrl:String = "",
                 var key:String = "",
                 var digest:String? = null
                    ) {
    companion object {
        const val TABLE_NAME = "note_record"
    }
}