package com.bcm.messenger.common.grouprepository.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Created by bcm.social.01 on 2019/1/15.
 */
@Entity(tableName = GroupKey.TABLE_NAME, indices = [Index(value = ["version","gid"], unique = true)])
class GroupKey {
    companion object {
        const val TABLE_NAME = "group_key_store"
    }

    @PrimaryKey(autoGenerate = true)
    var id:Long? = null
    var gid:Long = 0L
    var version:Long = 0L
    var key:String = ""
}