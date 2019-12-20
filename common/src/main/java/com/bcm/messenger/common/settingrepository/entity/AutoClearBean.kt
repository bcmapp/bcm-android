package com.bcm.messenger.common.settingrepository.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.bcm.messenger.utility.proguard.NotGuard
import com.bcm.messenger.common.settingrepository.dao.AUTO_CLEAR

/**
 * 自动清理数据
 * Created by zjl on 2018/9/25.
 */

@Entity(tableName = AUTO_CLEAR)
data class AutoClearBean(@PrimaryKey @ColumnInfo(name = "threadId") var threadId: Long,
                         @ColumnInfo(name = "clearTime") var clearTime: Long,
                         @ColumnInfo(name = "frequency") var frequency: Int,
                         @ColumnInfo(name = "period") var period: Int) : NotGuard