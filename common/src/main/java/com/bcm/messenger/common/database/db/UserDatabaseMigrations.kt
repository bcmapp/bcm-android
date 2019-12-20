package com.bcm.messenger.common.database.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bcm.messenger.common.database.model.AttachmentDbModel
import com.bcm.messenger.common.grouprepository.room.entity.GroupInfo
import com.bcm.messenger.common.grouprepository.room.entity.GroupMessage

/**
 * Created by Kin on 2019/9/10
 */

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // MARK: Database version 1 to 2 dose nothing, just refresh the db.
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE ${GroupMessage.TABLE_NAME} ADD COLUMN `thumbnail_uri` TEXT")
        database.execSQL("ALTER TABLE ${GroupMessage.TABLE_NAME} ADD COLUMN `data_random` BLOB")
        database.execSQL("ALTER TABLE ${GroupMessage.TABLE_NAME} ADD COLUMN `data_hash` TEXT")
        database.execSQL("ALTER TABLE ${GroupMessage.TABLE_NAME} ADD COLUMN `thumb_random` BLOB")
        database.execSQL("ALTER TABLE ${GroupMessage.TABLE_NAME} ADD COLUMN `thumb_hash` TEXT")
        database.execSQL("ALTER TABLE ${GroupMessage.TABLE_NAME} ADD COLUMN `attachment_size` INTEGER NOT NULL DEFAULT 0")

        database.execSQL("ALTER TABLE ${AttachmentDbModel.TABLE_NAME} ADD COLUMN `data_random` BLOB")
        database.execSQL("ALTER TABLE ${AttachmentDbModel.TABLE_NAME} ADD COLUMN `data_hash` TEXT")
        database.execSQL("ALTER TABLE ${AttachmentDbModel.TABLE_NAME} ADD COLUMN `thumb_random` BLOB")
        database.execSQL("ALTER TABLE ${AttachmentDbModel.TABLE_NAME} ADD COLUMN `thumb_hash` TEXT")

        database.execSQL("ALTER TABLE ${GroupInfo.TABLE_NAME} ADD COLUMN `profile_encrypted` INTEGER NOT NULL DEFAULT 0")
    }
}