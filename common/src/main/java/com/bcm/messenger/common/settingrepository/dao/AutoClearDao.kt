package com.bcm.messenger.common.settingrepository.dao

import androidx.room.*
import com.bcm.messenger.common.settingrepository.entity.AutoClearBean

/**
 * 自动清理表
 * Created by zjl on 2018/9/25.
 */
const val AUTO_CLEAR = "auto_clear"

@Dao
interface AutoClearDao {

    @Query("SELECT * FROM $AUTO_CLEAR")
    fun getAllData(): List<AutoClearBean>

    @Query("SELECT * FROM $AUTO_CLEAR WHERE threadId = :threadId LIMIT 1")
    fun getThreadData(threadId: Long): AutoClearBean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertData(bean: AutoClearBean)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateData(bean: AutoClearBean)

    @Delete
    fun deleteData(bean: AutoClearBean)

    @Delete
    fun deleteAllData(list: List<AutoClearBean>)
}