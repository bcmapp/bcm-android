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
package com.bcm.messenger.common.deprecated;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;

import com.bcm.messenger.common.AccountContext;

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

    private DatabaseHelper databaseHelper;

    private final SmsDatabase sms;
    private final EncryptingSmsDatabase encryptingSms;
    private final MmsDatabase mms;
    private final AttachmentDatabase attachments;
    private final ThreadDatabase thread;
    private final MmsSmsDatabase mmsSmsDatabase;
    private final IdentityDatabase identityDatabase;
    private final DraftDatabase draftDatabase;
    private final PushDatabase pushDatabase;
    private final RecipientDatabase recipientDatabase;
    private final com.bcm.messenger.common.grouprepository.room.database.GroupDatabase newGroupDatabase;
    private String uid;

    public static boolean isDatabaseExist(AccountContext accountContext, Context context) {
        return new File(context.getFilesDir().getParent() + "/databases/messages" + accountContext.getUid() + ".db").exists();
    }

    public SmsDatabase getSms() {
        return sms;
    }

    public EncryptingSmsDatabase getEncryptingSms() {
        return encryptingSms;
    }

    public MmsDatabase getMms() {
        return mms;
    }

    public AttachmentDatabase getAttachments() {
        return attachments;
    }

    public ThreadDatabase getThread() {
        return thread;
    }

    public MmsSmsDatabase getMmsSmsDatabase() {
        return mmsSmsDatabase;
    }

    public IdentityDatabase getIdentityDatabase() {
        return identityDatabase;
    }

    public DraftDatabase getDraftDatabase() {
        return draftDatabase;
    }

    public PushDatabase getPushDatabase() {
        return pushDatabase;
    }

    public RecipientDatabase getRecipientDatabase() {
        return recipientDatabase;
    }

    public com.bcm.messenger.common.grouprepository.room.database.GroupDatabase getNewGroupDatabase() {
        return newGroupDatabase;
    }

    public DatabaseFactory(AccountContext accountContext, Context context) {
        this.uid = accountContext.getUid();

        this.databaseHelper = new DatabaseHelper(context, getMessageDBName(accountContext.getUid()), null, DATABASE_VERSION);
        this.sms = new SmsDatabase(context, accountContext, databaseHelper);
        this.encryptingSms = new EncryptingSmsDatabase(context, accountContext, databaseHelper);
        this.mms = new MmsDatabase(context, accountContext, databaseHelper);
        this.attachments = new AttachmentDatabase(context, accountContext, databaseHelper);
        this.thread = new ThreadDatabase(context, accountContext, databaseHelper);
        this.mmsSmsDatabase = new MmsSmsDatabase(context, accountContext, databaseHelper);
        this.identityDatabase = new IdentityDatabase(context, accountContext, databaseHelper);
        this.draftDatabase = new DraftDatabase(context, accountContext, databaseHelper);
        this.pushDatabase = new PushDatabase(context, accountContext, databaseHelper);
        this.recipientDatabase = new RecipientDatabase(context, accountContext, databaseHelper);
        this.newGroupDatabase = com.bcm.messenger.common.grouprepository.room.database.GroupDatabase.allocDatabase(accountContext);
//        UserDatabase.Companion.getDatabase();
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
        this.databaseHelper.getWritableDatabase().execSQL("DELETE FROM " + RecipientDatabase.TABLE_NAME);
    }

    public void close() {
        this.databaseHelper.close();
        this.newGroupDatabase.close();
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
            db.execSQL(RecipientDatabase.CREATE_TABLE);

            executeStatements(db, SmsDatabase.CREATE_INDEXS);
            executeStatements(db, MmsDatabase.CREATE_INDEXS);
            executeStatements(db, AttachmentDatabase.CREATE_INDEXS);
            executeStatements(db, ThreadDatabase.CREATE_INDEXS);
            executeStatements(db, DraftDatabase.CREATE_INDEXS);
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
