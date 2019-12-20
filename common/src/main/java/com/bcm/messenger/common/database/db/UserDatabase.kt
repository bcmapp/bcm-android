package com.bcm.messenger.common.database.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.bcm.messenger.common.database.converters.PrivacyProfileConverter
import com.bcm.messenger.common.database.converters.UriConverter
import com.bcm.messenger.common.database.dao.*
import com.bcm.messenger.common.database.model.*
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.room.dao.*
import com.bcm.messenger.common.grouprepository.room.entity.*
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.utils.isReleaseBuild
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import com.commonsware.cwac.saferoom.SafeHelperFactory
import net.sqlcipher.database.SQLiteException
import java.io.File

/**
 * Created by Kin on 2019/9/10
 */

@Database(entities = [
        PrivateChatDbModel::class,
        ThreadDbModel::class,
        RecipientDbModel::class,
        IdentityDbModel::class,
        DraftDbModel::class,
        PushDbModel::class,
        AttachmentDbModel::class,
        AdHocChannelInfo::class,
        AdHocMessageDBEntity::class,
        AdHocSessionInfo::class,
        BcmFriend::class,
        BcmFriendRequest::class,
        ChatHideMessage::class,
        GroupAvatarParams::class,
        GroupInfo::class,
        GroupJoinRequestInfo::class,
        GroupLiveInfo::class,
        GroupMember::class,
        GroupMessage::class,
        NoteRecord::class,
        GroupKey::class
], version = UserDatabase.USER_DATABASE_VERSION, exportSchema = false)
@TypeConverters(value = [
    UriConverter::class,
    PrivacyProfileConverter::class
])
abstract class UserDatabase : RoomDatabase() {
    companion object {
        private const val TAG = "UserDatabase"

        const val USER_DATABASE_VERSION = 3

        @Volatile
        private var sInstance: UserDatabase? = null

        fun getDatabase(): UserDatabase {
            if (sInstance == null) {
                synchronized(UserDatabase::class.java) {
                    if (sInstance == null) {
                        sInstance = openDatabase()
                    }
                }
            }
            return sInstance!!
        }

        @Synchronized
        fun closeDatabase() {
            try {
                sInstance?.close()
                sInstance = null
            } catch (tr: Throwable) {
                ALog.e(TAG, "Close database failed. Reason is ${tr.message}")
            }
        }

        fun resetDatabase() {
            closeDatabase()
            getDatabase()
            Repository.getInstance().reset()
        }

        private fun openDatabase(needCheckDb: Boolean = true): UserDatabase {
            val masterSecret = BCMEncryptUtils.getMasterSecret(AppContextHolder.APP_CONTEXT)
            val options = SafeHelperFactory.Options.builder().setClearPassphrase(false).build()
            val factory = SafeHelperFactory(masterSecret?.encryptionKey?.encoded, options)
            val dbBuilder = Room.databaseBuilder(AppContextHolder.APP_CONTEXT, UserDatabase::class.java, "user_${AMESelfData.uid}.db")
                    .addMigrations(
                            MIGRATION_1_2,
                            MIGRATION_2_3
                    )
            // Do encryption if version is release build.
            val db = if (isReleaseBuild()) {
                dbBuilder.openHelperFactory(factory).allowMainThreadQueries().build()
            } else {
                dbBuilder.build()
            }

            if (needCheckDb) {
                try {
                    db.getThreadDao().queryThread("check_db")
                } catch (tr: Throwable) {
                    ALog.e(TAG, "Database error", tr)
                    if (tr is SQLiteException && tr.message?.startsWith("file is not a database") == true) {
                        ALog.e(TAG, "Database psw error, must delete old database!!")
                        try {
                            db.close()
                        } catch (tr2: Throwable) {
                        }
                        File("${AppContextHolder.APP_CONTEXT.filesDir.parent}/databases/user_${AMESelfData.uid}.db").delete()
                        File("${AppContextHolder.APP_CONTEXT.filesDir.parent}/databases/user_${AMESelfData.uid}.db-shm").delete()
                        File("${AppContextHolder.APP_CONTEXT.filesDir.parent}/databases/user_${AMESelfData.uid}.db-wal").delete()

                        return openDatabase(false)
                    }
                }
            }

            return db
        }
    }

    // Add DAO abstract functions

    abstract fun getPrivateChatDao(): PrivateChatDao

    abstract fun getThreadDao(): ThreadDao

    abstract fun getAttachmentDao(): AttachmentDao

    abstract fun getRecipientDao(): RecipientDao

    abstract fun getIdentityDao(): IdentityDao

    abstract fun getDraftDao(): DraftDao

    abstract fun getPushDao(): PushDao

    abstract fun groupInfoDao(): GroupInfoDao

    abstract fun groupMessageDao(): GroupMessageDao

    abstract fun groupMemberDao(): GroupMemberDao

    abstract fun groupLiveInfoDao(): GroupLiveInfoDao

    abstract fun bcmFriendDao(): BcmFriendDao

    abstract fun chatControlMessageDao(): ChatHideMessageDao

    abstract fun friendRequestDao(): FriendRequestDao

    abstract fun groupAvatarParamsDao(): GroupAvatarParamsDao

    abstract fun noteRecordDao(): NoteRecordDao

    abstract fun adHocChannelDao(): AdHocChannelDao

    abstract fun adHocSessionDao(): AdHocSessionDao

    abstract fun adHocMessageDao(): AdHocMessageDao

    abstract fun groupJoinInfoDao(): GroupJoinInfoDao

    abstract fun groupKeyDao(): GroupKeyDao
}