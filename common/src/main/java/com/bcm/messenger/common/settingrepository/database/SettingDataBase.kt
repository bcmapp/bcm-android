package com.bcm.messenger.common.settingrepository.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.bcm.messenger.common.settingrepository.dao.AutoClearDao
import com.bcm.messenger.common.settingrepository.entity.AutoClearBean
import com.bcm.messenger.utility.AppContextHolder

/**
 * 
 * Created by zjl on 2018/9/25.
 */
@Database(entities = [(AutoClearBean::class)], version = SettingDataBase.SETTING_DATABASE_VERSION, exportSchema = false)
abstract class SettingDataBase : RoomDatabase() {

    abstract fun getAutoClearDao(): AutoClearDao

    companion object {
        const val SETTING_DATABASE_VERSION = 1
        val db = Room.databaseBuilder(AppContextHolder.APP_CONTEXT, SettingDataBase::class.java, "settings").build()
    }
}