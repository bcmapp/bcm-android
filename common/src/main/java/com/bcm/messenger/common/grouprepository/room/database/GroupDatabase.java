package com.bcm.messenger.common.grouprepository.room.database;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.grouprepository.room.dao.AdHocChannelDao;
import com.bcm.messenger.common.grouprepository.room.dao.AdHocMessageDao;
import com.bcm.messenger.common.grouprepository.room.dao.AdHocSessionDao;
import com.bcm.messenger.common.grouprepository.room.dao.BcmFriendDao;
import com.bcm.messenger.common.grouprepository.room.dao.ChatHideMessageDao;
import com.bcm.messenger.common.grouprepository.room.dao.FriendRequestDao;
import com.bcm.messenger.common.grouprepository.room.dao.GroupAvatarParamsDao;
import com.bcm.messenger.common.grouprepository.room.dao.GroupInfoDao;
import com.bcm.messenger.common.grouprepository.room.dao.GroupJoinInfoDao;
import com.bcm.messenger.common.grouprepository.room.dao.GroupKeyDao;
import com.bcm.messenger.common.grouprepository.room.dao.GroupLiveInfoDao;
import com.bcm.messenger.common.grouprepository.room.dao.GroupMemberDao;
import com.bcm.messenger.common.grouprepository.room.dao.GroupMessageDao;
import com.bcm.messenger.common.grouprepository.room.dao.NoteRecordDao;
import com.bcm.messenger.common.grouprepository.room.dao.WalletDao;
import com.bcm.messenger.common.grouprepository.room.entity.AdHocChannelInfo;
import com.bcm.messenger.common.grouprepository.room.entity.AdHocMessageDBEntity;
import com.bcm.messenger.common.grouprepository.room.entity.AdHocSessionInfo;
import com.bcm.messenger.common.grouprepository.room.entity.BcmFriend;
import com.bcm.messenger.common.grouprepository.room.entity.BcmFriendRequest;
import com.bcm.messenger.common.grouprepository.room.entity.ChatHideMessage;
import com.bcm.messenger.common.grouprepository.room.entity.GroupAvatarParams;
import com.bcm.messenger.common.grouprepository.room.entity.GroupInfo;
import com.bcm.messenger.common.grouprepository.room.entity.GroupJoinRequestInfo;
import com.bcm.messenger.common.grouprepository.room.entity.GroupKey;
import com.bcm.messenger.common.grouprepository.room.entity.GroupLiveInfo;
import com.bcm.messenger.common.grouprepository.room.entity.GroupMember;
import com.bcm.messenger.common.grouprepository.room.entity.GroupMessage;
import com.bcm.messenger.common.grouprepository.room.entity.NoteRecord;
import com.bcm.messenger.common.grouprepository.room.entity.WalletData;
import com.bcm.messenger.common.grouprepository.room.entity.WalletTransaction;

import com.bcm.messenger.utility.AppContextHolder;
import com.bcm.messenger.utility.ClassHelper;
import com.bcm.messenger.utility.logger.ALog;

/**
 * GroupDatabase
 *
 * @deprecated Use UserDatabase instead.
 */
@Database(entities = {GroupInfo.class,
        GroupMessage.class,
        GroupLiveInfo.class,
        GroupMember.class,
        WalletData.class,
        WalletTransaction.class,
        BcmFriend.class,
        ChatHideMessage.class,
        BcmFriendRequest.class,
        GroupJoinRequestInfo.class,
        GroupAvatarParams.class,
        NoteRecord.class,
        AdHocChannelInfo.class,
        AdHocMessageDBEntity.class,
        AdHocSessionInfo.class,
        GroupKey.class}, version = GroupDatabase.GROUP_DATABASE_VERSION, exportSchema = false)
public abstract class GroupDatabase extends RoomDatabase {

    public static final int GROUP_DATABASE_VERSION = 24;

    /**
     * @return The DAO for the GroupInfo table.
     */
    @SuppressWarnings("WeakerAccess")
    public abstract GroupInfoDao groupInfo();

    /**
     * @return The DAO for the GroupMessage table.
     */
    @SuppressWarnings("WeakerAccess")
    public abstract GroupMessageDao GroupMessage();

    /**
     * @return The DAO for the GroupUser table.
     */
    @SuppressWarnings("WeakerAccess")
    public abstract GroupMemberDao groupMemberDao();

    /**
     * @return The DAO for the groupLive table.
     */
    @SuppressWarnings("WeakerAccess")
    public abstract GroupLiveInfoDao groupLiveInfo();

    /**
     * dao
     *
     * @return
     */
    @SuppressWarnings("WeakerAccess")
    public abstract WalletDao walletDao();

    /**
     * BCM 
     *
     * @return
     */
    @SuppressWarnings("WeakerAccess")
    public abstract BcmFriendDao bcmFriendDao();

    /**
     * 
     *
     * @return DAO
     */
    public abstract ChatHideMessageDao chatControlMessageDao();

    /**
     * 
     *
     * @return DAO
     */
    public abstract FriendRequestDao friendRequestDao();

    /**
     * 
     *
     * @return DAO
     */
    public abstract GroupAvatarParamsDao groupAvatarParamsDao();

    /**
     * 
     *
     * @return DAO
     */
    public abstract NoteRecordDao noteRecordDao();


    /**
     * AdHocDAO
     *
     * @return DAO
     */
    public abstract AdHocChannelDao adHocChannelDao();

    /**
     * AdHocDAO
     * @return Dao
     */
    public abstract AdHocSessionDao adHocSessionDao();

    /**
     * AdHocDAO
     * @return DAO
     */
    public abstract AdHocMessageDao adHocMessageDao();

    /**
     * 
     *
     * @return
     */
    @SuppressWarnings("WeakerAccess")
    public abstract GroupJoinInfoDao groupJoinInfoDao();

    /**
     * 
     *
     * @return
     */
    @SuppressWarnings("WeakerAccess")
    public abstract GroupKeyDao groupKeyDao();



    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            //groupInfo 
            database.execSQL("ALTER TABLE group_info "
                    + " ADD COLUMN 'channel_key' TEXT");
            database.execSQL("ALTER TABLE group_info "
                    + " ADD COLUMN 'role' INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE group_info "
                    + " ADD COLUMN 'illegal' INTEGER NOT NULL DEFAULT 0");
            //message 
            database.execSQL("ALTER TABLE group_message "
                    + " ADD COLUMN 'encrypt_level' INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            //groupMessage 
            database.execSQL("ALTER TABLE group_message "
                    + " ADD COLUMN 'ext_content' TEXT");
        }
    };

    /**
     * ， 
     */
    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE group_info "
                    + " ADD COLUMN 'notice_content' TEXT");
            database.execSQL("ALTER TABLE group_info "
                    + " ADD COLUMN 'notice_update_time' INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE group_info "
                    + " ADD COLUMN 'pinMid' INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE group_info "
                    + " ADD COLUMN 'is_show_notice' INTEGER NOT NULL DEFAULT 1");
            database.execSQL("ALTER TABLE group_info "
                    + " ADD COLUMN 'hasPin' INTEGER NOT NULL DEFAULT 0");
        }
    };


    /**
     * ，grouplive 
     */
    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            try {
                database.execSQL("CREATE TABLE IF NOT EXISTS `group_live_info` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `gid` INTEGER NOT NULL, `isLiving` INTEGER NOT NULL, `source_url` TEXT, `start_time` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `liveId` INTEGER NOT NULL, `confirmed` INTEGER NOT NULL)");
                database.execSQL("CREATE  INDEX `index_group_live_info__id` ON `group_live_info` (`_id`)");
                database.execSQL("CREATE  INDEX `index_group_live_info_liveId` ON `group_live_info` (`liveId`)");
            } catch (Exception e) {
                ALog.e("Database", "+++++++++++++++++++++++database upgrade(4,5)  error " + e);
            }

        }
    };


    /**
     * ，，， action 
     */
    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            try {
                database.execSQL("ALTER TABLE group_live_info "
                        + " ADD COLUMN 'liveStatus' INTEGER NOT NULL DEFAULT 0");
                database.execSQL("ALTER TABLE group_live_info "
                        + " ADD COLUMN 'currentSeekTime' INTEGER NOT NULL DEFAULT 0");
                database.execSQL("ALTER TABLE group_live_info "
                        + " ADD COLUMN 'currentActionTime' INTEGER NOT NULL DEFAULT 0");
            } catch (Exception e) {
                ALog.e("Database", "+++++++++++++++++++++++database upgrade(5,6)  error " + e);
            }

        }
    };

    /**
     * ，，， action 
     */
    private static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            try {
                database.execSQL("ALTER TABLE group_live_info "
                        + " ADD COLUMN 'source_type' INTEGER NOT NULL DEFAULT 0");
            } catch (Exception e) {
                ALog.e("Database", "+++++++++++++++++++++++database upgrade(6,7)  error " + e);
            }

        }
    };

    /**
     * ，
     */
    private static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            try {
                database.execSQL("DROP TABLE IF EXISTS `group_user`");
                database.execSQL("CREATE TABLE IF NOT EXISTS `group_member_table` (`uuid` TEXT NOT NULL, `uid` TEXT NOT NULL, `gid` INTEGER NOT NULL, `role` INTEGER NOT NULL, `join_time` INTEGER NOT NULL, PRIMARY KEY(`uuid`))");
            } catch (Exception e) {
                ALog.e("Database", "+++++++++++++++++++++++database upgrade(7,8)  error " + e);
            }

        }
    };

    private static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            try {
                database.execSQL("CREATE TABLE IF NOT EXISTS `idiom_table` (`idiom` TEXT NOT NULL, `index` INTEGER NOT NULL, PRIMARY KEY(`idiom`))");
            } catch (Exception e) {
                ALog.e("Database", "+++++++++++++++++++++++database upgrade(8,9)  error " + e);
            }

        }
    };


    private static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            ALog.i("GroupDatabase", "upgrade database MIGRATION_9_10");
            try {
                database.execSQL("CREATE TABLE IF NOT EXISTS `draw_history` (`_d_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `_g_id` INTEGER NOT NULL, `user_id` TEXT, `draw_type` TEXT, `draw_time` INTEGER NOT NULL, `hash` TEXT, `amount` TEXT, `zero_bits` INTEGER NOT NULL, `draw_flag` INTEGER NOT NULL, `memo` TEXT, FOREIGN KEY(`_g_id`) REFERENCES `game_history`(`_g_id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
                database.execSQL("CREATE  INDEX `index_draw_history__g_id` ON `draw_history` (`_g_id`)");
                database.execSQL("CREATE  INDEX `index_draw_history_user_id` ON `draw_history` (`user_id`)");
                database.execSQL("CREATE TABLE IF NOT EXISTS `game_history` (`_g_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `gid` INTEGER NOT NULL, `group_hash` TEXT, `game_type` TEXT, `start_time` INTEGER NOT NULL, `end_time` INTEGER NOT NULL)");
                database.execSQL("CREATE UNIQUE INDEX `index_game_history_game_type_start_time_end_time` ON `game_history` (`game_type`, `start_time`, `end_time`)");
                database.execSQL("CREATE  INDEX `index_game_history_gid` ON `game_history` (`gid`)");
                database.execSQL("CREATE TABLE IF NOT EXISTS `wallet_data` (`_w_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `owner` TEXT, `balance` TEXT, `extra` TEXT)");
                database.execSQL("CREATE  INDEX `index_wallet_data__w_id` ON `wallet_data` (`_w_id`)");
                database.execSQL("CREATE  INDEX `index_wallet_data_owner` ON `wallet_data` (`owner`)");
                database.execSQL("CREATE TABLE IF NOT EXISTS `wallet_transaction` (`_t_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `_w_id` INTEGER NOT NULL, `tx_id` TEXT, `op_type` TEXT, `amount` TEXT, `from` TEXT, `to` TEXT, `timestamp` INTEGER NOT NULL, `memo` TEXT, FOREIGN KEY(`_w_id`) REFERENCES `wallet_data`(`_w_id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
                database.execSQL("CREATE  INDEX `index_wallet_transaction__t_id` ON `wallet_transaction` (`_t_id`)");
                database.execSQL("CREATE  INDEX `index_wallet_transaction__w_id` ON `wallet_transaction` (`_w_id`)");
                database.execSQL("CREATE  INDEX `index_wallet_transaction_tx_id` ON `wallet_transaction` (`tx_id`)");
            } catch (Exception e) {
                ALog.e("Database", "+++++++++++++++++++++++database upgrade(9,10)  error " + e);
            }

        }
    };

    private static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            ALog.i("GroupDatabase", "upgrade database MIGRATION_10_11");
            try {
                database.execSQL("CREATE TABLE IF NOT EXISTS `group_bcm_friend` (`uid` TEXT NOT NULL, `tag` TEXT NOT NULL, `state` INTEGER NOT NULL, PRIMARY KEY(`uid`))");
            } catch (Exception e) {
                ALog.e("Database", "+++++++++++++++++++++++database upgrade(10,11)  error " + e);
            }

        }
    };

    private static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            ALog.i("GroupDatabase", "upgrade database MIGRATION_11_12");
            try {
                database.execSQL("ALTER TABLE group_info "
                        + " ADD COLUMN 'member_sync_state' TEXT");
            } catch (Exception e) {
                ALog.e("Database", "+++++++++++++++++++++++database upgrade(11,12)  error " + e);
            }

        }
    };

    private static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            ALog.i("GroupDatabase", "upgrade database MIGRATION_12_13");
            try {
                database.execSQL("DROP TABLE IF EXISTS `group_member_table`");

                database.execSQL("CREATE TABLE IF NOT EXISTS `group_member_table_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `uid` TEXT NOT NULL, `gid` INTEGER NOT NULL, `role` INTEGER NOT NULL, `join_time` INTEGER NOT NULL, `nickname` TEXT NOT NULL, `group_nickname` TEXT NOT NULL, `profile_keys` TEXT NOT NULL)");
                database.execSQL("CREATE UNIQUE INDEX `index_group_member_table_new_uid_gid` ON `group_member_table_new` (`uid`, `gid`)");
            } catch (Exception e) {
                ALog.e("Database", "+++++++++++++++++++++++database upgrade(12,13)  error " + e);
            }

        }
    };

    private static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            ALog.i("GroupDatabase", "upgrade database MIGRATION_13_14");
            try {
                database.execSQL("ALTER TABLE " + GroupMessage.TABLE_NAME
                        + " ADD COLUMN 'identity_iv' TEXT");
            } catch (Exception e) {
                ALog.e("Database", "+++++++++++++++++++++++database upgrade(13,14)  error " + e);
            }

        }
    };

    private static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            try {
                database.execSQL("CREATE TABLE IF NOT EXISTS `chat_hide_msg` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `send_time` INTEGER NOT NULL, `body` TEXT NOT NULL, `dest_addr` TEXT NOT NULL)");
            } catch (Exception e) {
                ALog.e("Database", "+++++++++++++++++++++++database upgrade(14,15)  error " + e);
            }
        }
    };

    private static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            try {
                database.execSQL("CREATE TABLE IF NOT EXISTS `friend_request` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `proposer` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `memo` TEXT NOT NULL, `signature` TEXT NOT NULL, `unread` INTEGER NOT NULL, `approve` INTEGER NOT NULL)");
            } catch (Exception e) {
                ALog.e("Database", "+++++++++++++++++++++++database upgrade(15, 16)  error " + e);
            }
        }
    };

    private static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            try {
                database.execSQL("ALTER TABLE " + GroupInfo.TABLE_NAME
                        + " ADD COLUMN 'share_qr_code_setting' TEXT ");
                database.execSQL("ALTER TABLE " + GroupInfo.TABLE_NAME
                        + " ADD COLUMN 'share_sig' TEXT ");
                database.execSQL("ALTER TABLE " + GroupInfo.TABLE_NAME
                        + " ADD COLUMN 'share_and_owner_confirm_sig' TEXT");
                database.execSQL("ALTER TABLE " + GroupInfo.TABLE_NAME
                        + " ADD COLUMN 'group_info_secret' TEXT");
                database.execSQL("ALTER TABLE " + GroupInfo.TABLE_NAME
                        + " ADD COLUMN `owner_confirm` INTEGER");
                database.execSQL("ALTER TABLE " + GroupInfo.TABLE_NAME
                        + " ADD COLUMN `share_enabled` INTEGER");
                database.execSQL("ALTER TABLE " + GroupInfo.TABLE_NAME
                        + " ADD COLUMN `share_code` TEXT");
                database.execSQL("ALTER TABLE " + GroupInfo.TABLE_NAME
                        + " ADD COLUMN `share_epoch` INTEGER");
                database.execSQL("ALTER TABLE " + GroupInfo.TABLE_NAME + " ADD COLUMN `group_splice_name` TEXT");
                database.execSQL("ALTER TABLE " + GroupInfo.TABLE_NAME + " ADD COLUMN `chn_splice_name` TEXT");
                database.execSQL("ALTER TABLE " + GroupInfo.TABLE_NAME + " ADD COLUMN `splice_avatar` TEXT");

                database.execSQL("CREATE TABLE IF NOT EXISTS `group_join_requests` (`req_id` INTEGER NOT NULL, `identity_key` TEXT NOT NULL, `inviter` TEXT NOT NULL, `inviter_identity_key` TEXT NOT NULL, `read` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `status` INTEGER NOT NULL, `comment` TEXT NOT NULL, `mid` INTEGER NOT NULL, `gid` INTEGER NOT NULL, `uid` TEXT NOT NULL, PRIMARY KEY(`req_id`))");
                database.execSQL("CREATE UNIQUE INDEX `index_group_join_requests_uid_gid_mid` ON `group_join_requests` (`uid`, `gid`, `mid`)");

                database.execSQL("CREATE TABLE IF NOT EXISTS `group_avatar_params` (`gid` INTEGER NOT NULL, `uid1` TEXT NOT NULL, `uid2` TEXT NOT NULL, `uid3` TEXT NOT NULL, `uid4` TEXT NOT NULL, `user1Hash` TEXT NOT NULL, `user2Hash` TEXT NOT NULL, `user3Hash` TEXT NOT NULL, `user4Hash` TEXT NOT NULL, PRIMARY KEY(`gid`))");
            } catch (Exception e) {
                ALog.e("Database", "+++++++++++++++++++++++database upgrade(15, 16)  error " + e);
            }
        }
    };


    private static final Migration MIGRATION_17_18 = new Migration(17, 18) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            ALog.i("Database", "+++++++++++++++++++++++database upgrade(17, 18) ");
            try {
                database.execSQL("CREATE TABLE IF NOT EXISTS `note_record` (`_id` TEXT NOT NULL, `topic` TEXT NOT NULL, `defaultTopic` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `author` TEXT NOT NULL, `pin` INTEGER NOT NULL, `edit_position` INTEGER NOT NULL, `note_url` TEXT NOT NULL, `key` TEXT NOT NULL, PRIMARY KEY(`_id`))");
            } catch (Exception e) {
                ALog.e("Database", "+++++++++++++++++++++++database upgrade(17, 18)  error " + e);
            }
        }
    };

    private static final Migration MIGRATION_18_19 = new Migration(18, 19) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            ALog.i("Database", "+++++++++++++++++++++++database upgrade(18, 19) ");
            try {
                database.execSQL("ALTER TABLE " + NoteRecord.TABLE_NAME
                        + " ADD COLUMN `digest` TEXT");
            } catch (Exception e) {
                ALog.e("Database", "+++++++++++++++++++++++database upgrade(18, 19)  error " + e);
            }
        }
    };

    private static final Migration MIGRATION_19_20 = new Migration(19, 20) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            ALog.i("Database", "+++++++++++++++++++++++database upgrade(19, 20) ");
            try {
                database.execSQL("DROP TABLE IF EXISTS `draw_history`");
                database.execSQL("DROP TABLE IF EXISTS `game_history`");
                database.execSQL("DROP TABLE IF EXISTS `idiom_table`");
            } catch (Exception e) {
                ALog.e("Database", "+++++++++++++++++++++++database upgrade(19, 20)  error " + e);
            }
        }
    };

    private static final Migration MIGRATION_20_21 = new Migration(20, 21) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            ALog.i("Database", "+++++++++++++++++++++++database upgrade(20, 21) ");
            try {
                database.execSQL("CREATE TABLE IF NOT EXISTS `ad_hoc_channel_1` (`cid` TEXT NOT NULL, `channel_name` TEXT NOT NULL, `passwd` TEXT NOT NULL, PRIMARY KEY(`cid`))");
                database.execSQL("CREATE TABLE IF NOT EXISTS `adhoc_session_message` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `session_id` TEXT NOT NULL, `message_id` TEXT NOT NULL, `from_id` TEXT NOT NULL, `from_nick` TEXT NOT NULL, `text` TEXT NOT NULL, `state` INTEGER NOT NULL, `is_read` INTEGER NOT NULL, `time` INTEGER NOT NULL, `is_send` INTEGER NOT NULL, `ext_content` TEXT, `attachment_uri` TEXT, `thumbnail_uri` TEXT, `attachment_state` INTEGER NOT NULL)");
                database.execSQL("CREATE INDEX `index_adhoc_session_message_session_id` ON `adhoc_session_message` (`session_id`)");
                database.execSQL("CREATE INDEX `index_adhoc_session_message_from_id` ON `adhoc_session_message` (`from_id`)");
                database.execSQL("CREATE INDEX `index_adhoc_session_message_message_id` ON `adhoc_session_message` (`message_id`)");
                database.execSQL("CREATE TABLE IF NOT EXISTS `ad_hoc_sessions` (`session_id` TEXT NOT NULL, `cid` TEXT NOT NULL, `uid` TEXT NOT NULL, `pin` INTEGER NOT NULL, `mute` INTEGER NOT NULL, `at_me` INTEGER NOT NULL, `unread_count` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `last_message` TEXT NOT NULL, `last_state` INTEGER NOT NULL, `draft` TEXT NOT NULL, PRIMARY KEY(`session_id`))");
            } catch (Exception e) {
                ALog.e("Database", "+++++++++++++++++++++++database upgrade(20, 21)  error " + e);
            }
        }
    };

    private static final Migration MIGRATION_21_22 = new Migration(21, 22) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            ALog.i("Database", "+++++++++++++++++++++++database upgrade(21, 22) ");
            try {
                database.execSQL("ALTER TABLE " + GroupInfo.TABLE_NAME + " ADD COLUMN `share_link` TEXT ");

            } catch (Exception e) {
                ALog.e("Database", "+++++++++++++++++++++++database upgrade(21, 22)  error " + e);
            }
        }
    };

    private static final Migration MIGRATION_22_23 = new Migration(22, 23) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            ALog.i("Database", "+++++++++++++++++++++++database upgrade(22, 23) ");
            try {

                database.execSQL("ALTER TABLE " + GroupInfo.TABLE_NAME
                        + " ADD COLUMN `ephemeral_key` TEXT");
                database.execSQL("ALTER TABLE " + GroupInfo.TABLE_NAME
                        + " ADD COLUMN `version` INTEGER NOT NULL DEFAULT 0");
                database.execSQL("ALTER TABLE " + GroupInfo.TABLE_NAME
                        + " ADD COLUMN `key_version` INTEGER NOT NULL DEFAULT 0");
                database.execSQL("ALTER TABLE " + GroupMessage.TABLE_NAME
                        + " ADD COLUMN `key_version` INTEGER NOT NULL DEFAULT 0");

                database.execSQL("CREATE TABLE IF NOT EXISTS `group_key_store` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `gid` INTEGER NOT NULL, `version` INTEGER NOT NULL, `key` TEXT NOT NULL)");
                database.execSQL("CREATE UNIQUE INDEX `index_group_key_store_version_gid` ON `group_key_store` (`version`, `gid`)");
            } catch (Exception e) {
                ALog.e("Database", "+++++++++++++++++++++++database upgrade(22, 23)  error " + e);
            }
        }
    };

    private static final Migration MIGRATION_23_24 = new Migration(23, 24) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE " + GroupMessage.TABLE_NAME + " ADD COLUMN `thumbnail_uri` TEXT");
            database.execSQL("ALTER TABLE " + GroupMessage.TABLE_NAME + " ADD COLUMN `data_random` BLOB");
            database.execSQL("ALTER TABLE " + GroupMessage.TABLE_NAME + " ADD COLUMN `data_hash` TEXT");
            database.execSQL("ALTER TABLE " + GroupMessage.TABLE_NAME + " ADD COLUMN `thumb_random` BLOB");
            database.execSQL("ALTER TABLE " + GroupMessage.TABLE_NAME + " ADD COLUMN `thumb_hash` TEXT");
            database.execSQL("ALTER TABLE " + GroupMessage.TABLE_NAME + " ADD COLUMN `attachment_size` INTEGER NOT NULL DEFAULT 0");

            database.execSQL("ALTER TABLE " + GroupInfo.TABLE_NAME + " ADD COLUMN `profile_encrypted` INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static GroupDatabase allocDatabase(AccountContext accountContext) {
        return Room
                .databaseBuilder(AppContextHolder.APP_CONTEXT, GroupDatabase.class, "new_group" + accountContext.getUid())
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .addMigrations(MIGRATION_4_5)
                .addMigrations(MIGRATION_5_6)
                .addMigrations(MIGRATION_6_7)
                .addMigrations(MIGRATION_7_8)
                .addMigrations(MIGRATION_8_9)
                .addMigrations(MIGRATION_9_10)
                .addMigrations(MIGRATION_10_11)
                .addMigrations(MIGRATION_11_12)
                .addMigrations(MIGRATION_12_13)
                .addMigrations(MIGRATION_13_14)
                .addMigrations(MIGRATION_14_15)
                .addMigrations(MIGRATION_15_16)
                .addMigrations(MIGRATION_16_17)
                .addMigrations(MIGRATION_17_18)
                .addMigrations(MIGRATION_18_19)
                .addMigrations(MIGRATION_19_20)
                .addMigrations(MIGRATION_20_21)
                .addMigrations(MIGRATION_21_22)
                .addMigrations(MIGRATION_22_23)
                .addMigrations(MIGRATION_23_24)
                .build();
    }

}
