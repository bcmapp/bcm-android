package com.bcm.messenger.common.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Created by Kin on 2019/9/16
 */
@Entity(tableName = IdentityDbModel.TABLE_NAME)
open class IdentityDbModel {
    companion object {
        const val TABLE_NAME = "identities"
    }

    @PrimaryKey(autoGenerate = true)
    var id = 0L
    var uid = ""
    open var key = ""
    @ColumnInfo(name = "first_use")
    var firstUse = 0
    var timestamp = 0L
    var verified = 0
    @ColumnInfo(name = "non_blocking_approval")
    var nonBlockingApproval =  0
}