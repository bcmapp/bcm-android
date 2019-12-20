package com.bcm.messenger.common.grouprepository.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = AdHocChannelInfo.TABLE_NAME)
data class AdHocChannelInfo(
        @PrimaryKey
        val cid:String,
        @ColumnInfo(name = "channel_name")
        val channelName:String,
        val passwd:String
) {
    companion object {
        const val TABLE_NAME = "ad_hoc_channel_1"
    }

}