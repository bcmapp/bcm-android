package com.bcm.messenger.common.grouprepository.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * bcm.social.01 2019/3/13.
 */
@Entity(tableName = BcmFriend.TABLE_NAME)
class BcmFriend(@PrimaryKey var uid: String = "", var tag: String = "", var state: Int = CONTACT) {

    companion object {
        const val TABLE_NAME = "group_bcm_friend"
        const val ADDING = 1
        const val DELETING = 2
        const val CONTACT = 0
        const val DATA = 0xFF
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BcmFriend

        if (uid != other.uid) return false

        return true
    }

    override fun hashCode(): Int {
        return uid.hashCode()
    }


}