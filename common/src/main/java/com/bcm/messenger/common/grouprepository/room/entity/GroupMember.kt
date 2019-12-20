package com.bcm.messenger.common.grouprepository.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Created by bcm.social.01 on 2019/1/15.
 */
@Entity(tableName = GroupMember.TABLE_NAME, indices = [Index(value = ["uid","gid"], unique = true)])
class GroupMember {
    companion object {
        const val TABLE_NAME = "group_member_table_new"
    }

    @PrimaryKey(autoGenerate = true)
    var id:Long? = null
    var uid:String = ""
    var gid:Long = 0L
    var role:Long = 0L
    @ColumnInfo(name = "join_time")
    var joinTime:Long = 0L

    var nickname: String = ""
    @ColumnInfo(name = "group_nickname")
    var customNickname: String = ""
    @ColumnInfo(name = "profile_keys")
    var profileKeyConfig: String = ""
}