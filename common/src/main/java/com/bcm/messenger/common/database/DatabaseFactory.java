/**
 * Copyright (C) 2011 Whisper Systems
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.bcm.messenger.common.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

import com.bcm.messenger.common.contacts.ContactsDatabase;
import com.bcm.messenger.common.provider.AMESelfData;
import com.bcm.messenger.utility.logger.ALog;

import java.io.File;
import java.util.Locale;

/**
 * Database factory from signal.
 *
 * @deprecated Use UserDatabase instead.
 */
public class DatabaseFactory {
    private static final String TAG = "DatabaseFactory";
   
    private static final int DATABASE_VERSION = 59;

    private static final int UNREAD_COUNT_VERSION = 46;
    private static final int ATTACHMENT_UPDATE_VERSION = 47;


    private static final int SMS_CALL_VERSION = 49;

    
    private static final int RECIPIENT_UPDATE_VERSION = 51;

    
    private static final int RECIPEINT_LOCAL_VERSION = 52;

    private static final int INTRODUCED_MESSAGE_PAYLOAD_TYPE = 53;

    private static final int PIN_TIME_CHANGE = 54;

    private static final int GROUP_LIVE_VERSION = 55;

    
    private static final int DECRYPT_FAIL_VERSION = 56;

    
    private static final int ADD_PRIVACY_PROFILE_VERSION = 57;

    
    private static final int ADD_RELATIONSHIP_VERSION = 58;

    
    private static final int AWS_ATTACHMENT_VERSION = 59;

    private static final String DATABASE_NAME = "messages%s.db";
    private static final Object lock = new Object();

    private static DatabaseFactory instance;

    private DatabaseHelper databaseHelper;

    private final SmsDatabase sms;
    private final EncryptingSmsDatabase encryptingSms;
    private final MmsDatabase mms;
    private final AttachmentDatabase attachments;
    private final MediaDatabase media;
    private final ThreadDatabase thread;
    private final MmsSmsDatabase mmsSmsDatabase;
    private final IdentityDatabase identityDatabase;
    private final DraftDatabase draftDatabase;
    private final PushDatabase pushDatabase;
    private final GroupDatabase groupDatabase;
    private final RecipientDatabase recipientDatabase;
    private final ContactsDatabase contactsDatabase;
    private final GroupReceiptDatabase groupReceiptDatabase;
    private String uid;

    public static DatabaseFactory getInstance(Context context) {
        synchronized (lock) {
            String uid = AMESelfData.INSTANCE.getUid();
            if (instance == null) {
                instance = new DatabaseFactory(context.getApplicationContext(), uid);
            } else if (!uid.equals(instance.uid)) {
                instance.reset(context, AMESelfData.INSTANCE.getUid());
            }
            return instance;
        }
    }

    public static boolean isDatabaseExist(Context context) {
        return new File(context.getFilesDir().getParent() + "/databases/messages" + AMESelfData.INSTANCE.getUid() + ".db").exists();
    }

    public static MmsSmsDatabase getMmsSmsDatabase(Context context) {
        return getInstance(context).mmsSmsDatabase;
    }

    public static ThreadDatabase getThreadDatabase(Context context) {
        return getInstance(context).thread;
    }

    public static SmsDatabase getSmsDatabase(Context context) {
        return getInstance(context).sms;
    }

    public static MmsDatabase getMmsDatabase(Context context) {
        return getInstance(context).mms;
    }

    public static EncryptingSmsDatabase getEncryptingSmsDatabase(Context context) {
        return getInstance(context).encryptingSms;
    }

    public static AttachmentDatabase getAttachmentDatabase(Context context) {
        return getInstance(context).attachments;
    }

    public static MediaDatabase getMediaDatabase(Context context) {
        return getInstance(context).media;
    }

    public static IdentityDatabase getIdentityDatabase(Context context) {
        return getInstance(context).identityDatabase;
    }

    public static DraftDatabase getDraftDatabase(Context context) {
        return getInstance(context).draftDatabase;
    }

    public static PushDatabase getPushDatabase(Context context) {
        return getInstance(context).pushDatabase;
    }

    public static GroupDatabase getGroupDatabase(Context context) {
        return getInstance(context).groupDatabase;
    }

    @Nullable
    public static RecipientDatabase getRecipientDatabase(Context context) {
        if (!AMESelfData.INSTANCE.isLogin()) {
            return null;
        } else {
            return getInstance(context).recipientDatabase;
        }
    }

    public static ContactsDatabase getContactsDatabase(Context context) {
        return getInstance(context).contactsDatabase;
    }

    public static GroupReceiptDatabase getGroupReceiptDatabase(Context context) {
        return getInstance(context).groupReceiptDatabase;
    }

    private DatabaseFactory(Context context, String uid) {
        this.uid = uid;

        this.databaseHelper = new DatabaseHelper(context, getMessageDBName(uid), null, DATABASE_VERSION);
        this.sms = new SmsDatabase(context, databaseHelper);
        this.encryptingSms = new EncryptingSmsDatabase(context, databaseHelper);
        this.mms = new MmsDatabase(context, databaseHelper);
        this.attachments = new AttachmentDatabase(context, databaseHelper);
        this.media = new MediaDatabase(context, databaseHelper);
        this.thread = new ThreadDatabase(context, databaseHelper);
        this.mmsSmsDatabase = new MmsSmsDatabase(context, databaseHelper);
        this.identityDatabase = new IdentityDatabase(context, databaseHelper);
        this.draftDatabase = new DraftDatabase(context, databaseHelper);
        this.pushDatabase = new PushDatabase(context, databaseHelper);
        this.groupDatabase = new GroupDatabase(context, databaseHelper);
        this.recipientDatabase = new RecipientDatabase(context, databaseHelper);
        this.groupReceiptDatabase = new GroupReceiptDatabase(context, databaseHelper);
        this.contactsDatabase = new ContactsDatabase(context);
        com.bcm.messenger.common.grouprepository.room.database.GroupDatabase.getInstance();
//        UserDatabase.Companion.getDatabase();
    }

    
    public void reset(Context context, String uid) {
        ALog.d(TAG, "reset " + uid);


        try {
            this.uid = uid;
            DatabaseHelper old = this.databaseHelper;
            this.databaseHelper = null;
            if (!uid.isEmpty()) {
                this.databaseHelper = new DatabaseHelper(context, getMessageDBName(uid), null, DATABASE_VERSION);
            }

            this.sms.reset(databaseHelper);
            this.encryptingSms.reset(databaseHelper);
            this.media.reset(databaseHelper);
            this.mms.reset(databaseHelper);
            this.attachments.reset(databaseHelper);
            this.thread.reset(databaseHelper);
            this.mmsSmsDatabase.reset(databaseHelper);
            this.identityDatabase.reset(databaseHelper);
            this.draftDatabase.reset(databaseHelper);
            this.pushDatabase.reset(databaseHelper);
            this.groupDatabase.reset(databaseHelper);
            this.recipientDatabase.reset(databaseHelper);
            this.groupReceiptDatabase.reset(databaseHelper);
            com.bcm.messenger.common.grouprepository.room.database.GroupDatabase.resetInstance();
//            UserDatabase.Companion.resetDatabase();

            if (old != null) {
                old.close();
            }
        } catch (Throwable e) {
            ALog.e(TAG, e);
        }

    }

    private String getMessageDBName(String uid) {
        return String.format(Locale.US, DATABASE_NAME, uid);
    }

    
    public void deleteAllDatabase() {
        this.databaseHelper.getWritableDatabase().execSQL("DELETE FROM " + SmsDatabase.TABLE_NAME);
        this.databaseHelper.getWritableDatabase().execSQL("DELETE FROM " + MmsDatabase.TABLE_NAME);
        this.databaseHelper.getWritableDatabase().execSQL("DELETE FROM " + AttachmentDatabase.TABLE_NAME);
        this.databaseHelper.getWritableDatabase().execSQL("DELETE FROM " + ThreadDatabase.TABLE_NAME);
        this.databaseHelper.getWritableDatabase().execSQL("DELETE FROM " + IdentityDatabase.TABLE_NAME);
        this.databaseHelper.getWritableDatabase().execSQL("DELETE FROM " + DraftDatabase.TABLE_NAME);
        this.databaseHelper.getWritableDatabase().execSQL("DELETE FROM " + PushDatabase.TABLE_NAME);
        this.databaseHelper.getWritableDatabase().execSQL("DELETE FROM " + GroupDatabase.TABLE_NAME);
        this.databaseHelper.getWritableDatabase().execSQL("DELETE FROM " + RecipientDatabase.TABLE_NAME);
        this.databaseHelper.getWritableDatabase().execSQL("DELETE FROM " + GroupReceiptDatabase.TABLE_NAME);
    }

    public void close() {
        this.databaseHelper.close();
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {

        private static final String TAG = DatabaseHelper.class.getSimpleName();

        private final Context context;

        public DatabaseHelper(Context context, String name, CursorFactory factory, int version) {
            super(context, name, factory, version);
            this.context = context.getApplicationContext();
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SmsDatabase.CREATE_TABLE);
            db.execSQL(MmsDatabase.CREATE_TABLE);
            db.execSQL(AttachmentDatabase.CREATE_TABLE);
            db.execSQL(ThreadDatabase.CREATE_TABLE);
            db.execSQL(IdentityDatabase.CREATE_TABLE);
            db.execSQL(DraftDatabase.CREATE_TABLE);
            db.execSQL(PushDatabase.CREATE_TABLE);
            db.execSQL(GroupDatabase.CREATE_TABLE);
            db.execSQL(RecipientDatabase.CREATE_TABLE);
            db.execSQL(GroupReceiptDatabase.CREATE_TABLE);

            executeStatements(db, SmsDatabase.CREATE_INDEXS);
            executeStatements(db, MmsDatabase.CREATE_INDEXS);
            executeStatements(db, AttachmentDatabase.CREATE_INDEXS);
            executeStatements(db, ThreadDatabase.CREATE_INDEXS);
            executeStatements(db, DraftDatabase.CREATE_INDEXS);
            executeStatements(db, GroupDatabase.CREATE_INDEXS);
            executeStatements(db, GroupReceiptDatabase.CREATE_INDEXES);
        }

       
        private void doRecipientMigrationTo58(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion >= RECIPEINT_LOCAL_VERSION && oldVersion < ADD_RELATIONSHIP_VERSION) {
                db.execSQL(RecipientDatabase.ALTER_TABLE_ADD_RELATIONSHIP);
                db.execSQL(RecipientDatabase.ALTER_TABLE_ADD_SUPPORT_FEATURES);
            }
        }

     
        private void doRecipientMigrationTo57(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion >= RECIPEINT_LOCAL_VERSION && oldVersion < ADD_PRIVACY_PROFILE_VERSION) {
                db.execSQL(RecipientDatabase.ALTER_TABLE_ADD_PRIVACY);
            }
        }

        private void doRecipientMigrationTo52(SQLiteDatabase db, int oldVersion, int newVersion) {
            
            if (oldVersion < RECIPEINT_LOCAL_VERSION) {
                db.execSQL(RecipientDatabase.DROP_TABLE);
                db.execSQL(RecipientDatabase.CREATE_TABLE);
            }
        }

        private void doSMSMigrationTo49(SQLiteDatabase db, int oldVersion, int newVersion) {
            
            if (oldVersion < SMS_CALL_VERSION) {
                db.execSQL(SmsDatabase.DROP_TABLE);
                db.execSQL(SmsDatabase.CREATE_TABLE);
            }
        }

        private void doAttachmentMigrationTo47(SQLiteDatabase db, int oldVersion, int newVersion) {
            
            if (oldVersion < ATTACHMENT_UPDATE_VERSION) {
                db.execSQL(AttachmentDatabase.DROP_TABLE);
                db.execSQL(AttachmentDatabase.CREATE_TABLE);
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.beginTransaction();

            doLegacyMigration(db, oldVersion, newVersion);
            doAttachmentMigrationTo47(db, oldVersion, newVersion);
            doSMSMigrationTo49(db, oldVersion, newVersion);
            doRecipientMigrationTo52(db, oldVersion, newVersion);
            doRecipientMigrationTo57(db, oldVersion, newVersion);
            doRecipientMigrationTo58(db, oldVersion, newVersion);

            db.setTransactionSuccessful();
            db.endTransaction();

        }

        
        private void doLegacyMigration(SQLiteDatabase db, int oldVersion, int newVersion) {

            if (oldVersion < UNREAD_COUNT_VERSION) {
                db.execSQL("ALTER TABLE thread ADD COLUMN unread_count INTEGER DEFAULT 0");

                try (Cursor cursor = db.query("thread", new String[]{"_id"}, "read = 0", null, null, null, null)) {
                    while (cursor != null && cursor.moveToNext()) {
                        long threadId = cursor.getLong(0);
                        int unreadCount = 0;

                        try (Cursor smsCursor = db.rawQuery("SELECT COUNT(*) FROM sms WHERE thread_id = ? AND read = '0'", new String[]{String.valueOf(threadId)})) {
                            if (smsCursor != null && smsCursor.moveToFirst()) {
                                unreadCount += smsCursor.getInt(0);
                            }
                        }

                        try (Cursor mmsCursor = db.rawQuery("SELECT COUNT(*) FROM mms WHERE thread_id = ? AND read = '0'", new String[]{String.valueOf(threadId)})) {
                            if (mmsCursor != null && mmsCursor.moveToFirst()) {
                                unreadCount += mmsCursor.getInt(0);
                            }
                        }

                        db.execSQL("UPDATE thread SET unread_count = ? WHERE _id = ?",
                                new String[]{String.valueOf(unreadCount),
                                        String.valueOf(threadId)});
                    }
                }
            }

            if (oldVersion < INTRODUCED_MESSAGE_PAYLOAD_TYPE) {
                db.execSQL("ALTER TABLE sms ADD COLUMN payload_type INTEGER DEFAULT 0");
            }

            if (oldVersion < PIN_TIME_CHANGE) {
                db.execSQL("UPDATE thread set pin_time = ? WHERE pin_time = ?", new String[]{
                        String.valueOf(System.currentTimeMillis()), String.valueOf(0)
                });
                db.execSQL("UPDATE thread set pin_time = ? WHERE pin_time = ?", new String[]{
                        String.valueOf(0), String.valueOf(1)
                });
            }

            if (oldVersion < GROUP_LIVE_VERSION) {
                db.execSQL("ALTER TABLE thread ADD COLUMN live_state INTEGER DEFAULT -1");
            }

            if (oldVersion < DECRYPT_FAIL_VERSION) {
                db.execSQL("ALTER TABLE thread ADD COLUMN decrypt_fail_data TEXT DEFAULT NULL");
                db.execSQL("ALTER TABLE push ADD COLUMN source_registration_id INTEGER DEFAULT 0");
            }

            if (oldVersion < ADD_PRIVACY_PROFILE_VERSION) {
                db.execSQL("ALTER TABLE thread ADD COLUMN profile_request INTEGER DEFAULT 0");
            }

            if (oldVersion < AWS_ATTACHMENT_VERSION) {
                db.execSQL("ALTER TABLE part ADD COLUMN url TEXT DEFAULT NULL");
            }
        }

        private void executeStatements(SQLiteDatabase db, String[] statements) {
            for (String statement : statements)
                db.execSQL(statement);
        }

    }
}
