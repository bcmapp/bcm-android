package com.bcm.messenger.common.grouprepository.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = GroupAvatarParams.TABLE_NAME)
class GroupAvatarParams {
    companion object {
        const val TABLE_NAME = "group_avatar_params"
    }

    @PrimaryKey
    var gid:Long = 0
    var uid1:String = ""
    var uid2:String = ""
    var uid3:String = ""
    var uid4:String = ""
    var user1Hash:String = ""
    var user2Hash:String = ""
    var user3Hash:String = ""
    var user4Hash:String = ""


    fun toUserList():List<String> {
        return listOf(uid1, uid2, uid3, uid4).filter { it.isNotEmpty() }
    }
}