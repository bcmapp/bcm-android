/*
 * Copyright (C) 2011 Whisper Systems
 * Copyright (C) 2013-2017 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.bcm.messenger.common.deprecated;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;
import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.R;
import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.common.crypto.MasterCipher;
import com.bcm.messenger.common.preferences.TextSecurePreferences;
import com.bcm.messenger.common.recipients.Recipient;
import com.bcm.messenger.utility.StringAppearanceUtil;
import com.bcm.messenger.utility.logger.ALog;

import org.jetbrains.annotations.NotNull;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.util.Pair;

import java.util.List;

@Deprecated
public class ThreadDatabase extends Database {

    private static final String TAG = ThreadDatabase.class.getSimpleName();

    static final String TABLE_NAME = "thread";
    public static final String ID = "_id";
    public static final String DATE = "date";
    public static final String MESSAGE_COUNT = "message_count";
    public static final String ADDRESS = "recipient_ids";
    public static final String SNIPPET = "snippet";
    private static final String SNIPPET_CHARSET = "snippet_cs";
    public static final String READ = "read";
    public static final String UNREAD_COUNT = "unread_count";
    public static final String TYPE = "type";
    private static final String ERROR = "error";
    public static final String SNIPPET_TYPE = "snippet_type";
    public static final String SNIPPET_URI = "snippet_uri";
    public static final String ARCHIVED = "archived";
    public static final String STATUS = "status";
    public static final String DELIVERY_RECEIPT_COUNT = "delivery_receipt_count";
    public static final String READ_RECEIPT_COUNT = "read_receipt_count";
    public static final String EXPIRES_IN = "expires_in";
    public static final String LAST_SEEN = "last_seen";
    private static final String HAS_SENT = "has_sent";
    private static final String LIVE_STATE = "live_state";
    private static final String PIN = "pin_time";
    private static final String DECRYPT_FAIL_DATA = "decrypt_fail_data";
    private static final String PROFILE_REQUEST = "profile_request";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" +
            ID + " INTEGER PRIMARY KEY, " + DATE + " INTEGER DEFAULT 0, " +
            MESSAGE_COUNT + " INTEGER DEFAULT 0, " + ADDRESS + " TEXT, " + SNIPPET + " TEXT, " +
            SNIPPET_CHARSET + " INTEGER DEFAULT 0, " + READ + " INTEGER DEFAULT 1, " +
            TYPE + " INTEGER DEFAULT 0, " + ERROR + " INTEGER DEFAULT 0, " +
            SNIPPET_TYPE + " INTEGER DEFAULT 0, " + SNIPPET_URI + " TEXT DEFAULT NULL, " +
            ARCHIVED + " INTEGER DEFAULT 0, " + STATUS + " INTEGER DEFAULT 0, " +
            DELIVERY_RECEIPT_COUNT + " INTEGER DEFAULT 0, " + EXPIRES_IN + " INTEGER DEFAULT 0, " +
            LAST_SEEN + " INTEGER DEFAULT 0, " + HAS_SENT + " INTEGER DEFAULT 0, " +
            READ_RECEIPT_COUNT + " INTEGER DEFAULT 0, " + UNREAD_COUNT + " INTEGER DEFAULT 0, " +
            PIN + " INTEGER DEFAULT 0, " + LIVE_STATE + " INTEGER DEFAULT -1, " +
            DECRYPT_FAIL_DATA + " TEXT, " + PROFILE_REQUEST + " INTEGER DEFAULT 0);";

    static final String[] CREATE_INDEXS = {
            "CREATE INDEX IF NOT EXISTS thread_recipient_ids_index ON " + TABLE_NAME + " (" + ADDRESS + ");",
            "CREATE INDEX IF NOT EXISTS archived_count_index ON " + TABLE_NAME + " (" + ARCHIVED + ", " + MESSAGE_COUNT + ");",
    };

    public static final String DROP_TABLE = "DROP TABLE " + TABLE_NAME;

    private static final String[] THREAD_PROJECTION = {
            ID, DATE, MESSAGE_COUNT, ADDRESS, SNIPPET, SNIPPET_CHARSET, READ, UNREAD_COUNT, TYPE, ERROR, SNIPPET_TYPE,
            SNIPPET_URI, ARCHIVED, STATUS, DELIVERY_RECEIPT_COUNT, EXPIRES_IN, LAST_SEEN, READ_RECEIPT_COUNT, PIN, LIVE_STATE,
            DECRYPT_FAIL_DATA, PROFILE_REQUEST
    };

    private static final List<String> TYPED_THREAD_PROJECTION = Stream.of(THREAD_PROJECTION)
            .map(columnName -> TABLE_NAME + "." + columnName)
            .toList();

    private static final List<String> COMBINED_THREAD_RECIPIENT_GROUP_PROJECTION = Stream.concat(Stream.concat(Stream.of(TYPED_THREAD_PROJECTION),
            Stream.of(RecipientDatabase.TYPED_RECIPIENT_PROJECTION)),
            Stream.of(GroupDatabase.TYPED_GROUP_PROJECTION))
            .toList();

    public ThreadDatabase(Context context, AccountContext accountContext, SQLiteOpenHelper databaseHelper) {
        super(context, accountContext, databaseHelper);
    }

    public Cursor getConversationList() {
        return getConversationList("0");
    }

    private Cursor getConversationList(String archived) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        String query = createCoversationListQuery(ARCHIVED + " = ?  AND " + MESSAGE_COUNT + " != 0");
        Cursor cursor = db.rawQuery(query, new String[]{archived});

        return cursor;
    }

    public String getDecryptFailData(long threadId) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, new String[]{DECRYPT_FAIL_DATA}, ID_WHERE, new String[]{Long.toString(threadId)}, null, null, null);
        try {
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                return cursor.getString(cursor.getColumnIndexOrThrow(DECRYPT_FAIL_DATA));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    public Pair<Long, Boolean> getLastSeenAndHasSent(long threadId) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, new String[]{LAST_SEEN, HAS_SENT}, ID_WHERE, new String[]{String.valueOf(threadId)}, null, null, null);

        try {
            if (cursor != null && cursor.moveToFirst()) {
                return new Pair<>(cursor.getLong(0), cursor.getLong(1) == 1);
            }

            return new Pair<>(-1L, false);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public boolean hasProfileRequest(long threadId) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        try (Cursor cursor = db.query(TABLE_NAME, THREAD_PROJECTION, ID_WHERE, new String[]{String.valueOf(threadId)}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int request = cursor.getInt(cursor.getColumnIndex(PROFILE_REQUEST));
                return request == 1;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }

    private @NonNull
    String createCoversationListQuery(@NonNull String where) {
        String projection = StringAppearanceUtil.INSTANCE.join(COMBINED_THREAD_RECIPIENT_GROUP_PROJECTION, ",");
        return "SELECT " + projection + " FROM " + TABLE_NAME +
                " LEFT OUTER JOIN " + RecipientDatabase.TABLE_NAME +
                " ON " + TABLE_NAME + "." + ADDRESS + " = " + RecipientDatabase.TABLE_NAME + "." + RecipientDatabase.ADDRESS +
                " LEFT OUTER JOIN " + GroupDatabase.TABLE_NAME +
                " ON " + TABLE_NAME + "." + ADDRESS + " = " + GroupDatabase.TABLE_NAME + "." + GroupDatabase.GROUP_ID +
                " WHERE " + where +
                " ORDER BY " + TABLE_NAME + "." + PIN + " DESC, " + TABLE_NAME + "." + DATE + " DESC";
    }

    @Override
    public void reset(SQLiteOpenHelper databaseHelper) {
        super.reset(databaseHelper);
    }

    public interface ProgressListener {
        void onProgress(int complete, int total);
    }

    public Reader readerFor(Cursor cursor, MasterCipher masterCipher) {
        return new Reader(accountContext, cursor, masterCipher);
    }

    public static class DistributionTypes {
        public static final int DEFAULT = 2;
        public static final int BROADCAST = 1;
        public static final int CONVERSATION = 2;
        public static final int ARCHIVE = 3;
        public static final int INBOX_ZERO = 4;
        public static final int NEW_GROUP = 5;


    }

    public class Reader {

        private final Cursor cursor;
        private final MasterCipher masterCipher;
        private final AccountContext accountContext;

        public Reader(AccountContext accountContext, Cursor cursor, MasterCipher masterCipher) {
            this.cursor = cursor;
            this.masterCipher = masterCipher;
            this.accountContext = accountContext;
        }

        public boolean resetCursor() {
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                return true;
            } else {
                return false;
            }
        }

        public ThreadRecord getNext() {
            if (cursor == null || !cursor.moveToNext()) {
                return null;
            }

            return getCurrent();
        }

        public ThreadRecord getNextForMigrate() {
            if (cursor == null || !cursor.moveToNext()) {
                return null;
            }

            return getCurrentForMigrate();
        }

        public @NotNull
        Address getAddress() {
            return Address.from(accountContext, cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.ADDRESS)));
        }

        public ThreadRecord getCurrentForMigrate() {
            long threadId = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.ID));
            int distributionType = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.TYPE));
            Address address = Address.from(accountContext, cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.ADDRESS)));

            DisplayRecord.Body body;
            if (distributionType == DistributionTypes.NEW_GROUP) {
                body = getPlaintextBodyForNewGroup(cursor);
            } else {
                body = getPlaintextBody(cursor);
            }
            long date = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.DATE));
            long count = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.MESSAGE_COUNT));
            int unreadCount = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.UNREAD_COUNT));
            long type = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET_TYPE));
            boolean archived = cursor.getInt(cursor.getColumnIndex(ThreadDatabase.ARCHIVED)) != 0;
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.STATUS));
            int deliveryReceiptCount = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.DELIVERY_RECEIPT_COUNT));
            int readReceiptCount = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.READ_RECEIPT_COUNT));
            long expiresIn = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.EXPIRES_IN));
            long lastSeen = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.LAST_SEEN));
            long pin = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.PIN));
            int live_state = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.LIVE_STATE));
            Uri snippetUri = getSnippetUri(cursor);

            if (!TextSecurePreferences.isReadReceiptsEnabled(accountContext)) {
                readReceiptCount = 0;
            }

            ThreadRecord record = new ThreadRecord(context, body, snippetUri, null, date, count,
                    unreadCount, threadId, deliveryReceiptCount, status, type,
                    distributionType, archived, expiresIn, lastSeen, readReceiptCount, pin, live_state);
            record.uid = address.serialize();
            return record;
        }

        public ThreadRecord getCurrent() {
            long threadId = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.ID));
            int distributionType = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.TYPE));
            Address address = Address.from(accountContext, cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.ADDRESS)));

            Recipient recipient = Recipient.from(accountContext, address.serialize(), true);

            DisplayRecord.Body body;
            if (distributionType == DistributionTypes.NEW_GROUP) {
                body = getPlaintextBodyForNewGroup(cursor);
            } else {
                body = getPlaintextBody(cursor);
            }
            long date = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.DATE));
            long count = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.MESSAGE_COUNT));
            int unreadCount = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.UNREAD_COUNT));
            long type = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET_TYPE));
            boolean archived = cursor.getInt(cursor.getColumnIndex(ThreadDatabase.ARCHIVED)) != 0;
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.STATUS));
            int deliveryReceiptCount = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.DELIVERY_RECEIPT_COUNT));
            int readReceiptCount = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.READ_RECEIPT_COUNT));
            long expiresIn = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.EXPIRES_IN));
            long lastSeen = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.LAST_SEEN));
            long pin = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.PIN));
            int live_state = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.LIVE_STATE));
            Uri snippetUri = getSnippetUri(cursor);

            if (!TextSecurePreferences.isReadReceiptsEnabled(accountContext)) {
                readReceiptCount = 0;
            }

            return new ThreadRecord(context, body, snippetUri, recipient, date, count,
                    unreadCount, threadId, deliveryReceiptCount, status, type,
                    distributionType, archived, expiresIn, lastSeen, readReceiptCount, pin, live_state);

        }

        private DisplayRecord.Body getPlaintextBody(Cursor cursor) {
            try {
                long type = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET_TYPE));
                String body = cursor.getString(cursor.getColumnIndexOrThrow(SNIPPET));
                if (!TextUtils.isEmpty(body) && masterCipher != null && MmsSmsColumns.Types.isSymmetricEncryption(type)) {
                    return new DisplayRecord.Body(masterCipher.decryptBody(body), true);
                } else if (!TextUtils.isEmpty(body) && masterCipher == null && MmsSmsColumns.Types.isSymmetricEncryption(type)) {
                    ALog.e("DisplayRecord", "cipher must not be null");
                    return new DisplayRecord.Body(body, false);
                } else {
                    return new DisplayRecord.Body(body, true);
                }
            } catch (InvalidMessageException e) {
                Log.w("ThreadDatabase", e);
                return new DisplayRecord.Body(context.getString(R.string.ThreadDatabase_error_decrypting_message), true);
            }
        }

        private DisplayRecord.Body getPlaintextBodyForNewGroup(Cursor cursor) {
            return new DisplayRecord.Body(cursor.getString(cursor.getColumnIndexOrThrow(SNIPPET)), true);
        }

        private @Nullable
        Uri getSnippetUri(Cursor cursor) {
            if (cursor.isNull(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET_URI))) {
                return null;
            }

            try {
                return Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET_URI)));
            } catch (IllegalArgumentException e) {
                Log.w(TAG, e);
                return null;
            }
        }

        public void close() {
            cursor.close();
        }
    }
}
