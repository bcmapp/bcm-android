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
package com.bcm.messenger.common.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MergeCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;
import com.bcm.messenger.common.R;
import com.bcm.messenger.common.attachments.Attachment;
import com.bcm.messenger.common.attachments.DatabaseAttachment;
import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.common.crypto.MasterCipher;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.database.MessagingDatabase.MarkedMessageInfo;
import com.bcm.messenger.common.database.model.DisplayRecord;
import com.bcm.messenger.common.database.model.MediaMmsMessageRecord;
import com.bcm.messenger.common.database.model.MessageRecord;
import com.bcm.messenger.common.database.model.ThreadRecord;
import com.bcm.messenger.common.grouprepository.manager.GroupInfoDataManager;
import com.bcm.messenger.common.grouprepository.manager.GroupLiveInfoManager;
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager;
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail;
import com.bcm.messenger.common.grouprepository.modeltransform.GroupMessageTransform;
import com.bcm.messenger.common.grouprepository.room.entity.GroupLiveInfo;
import com.bcm.messenger.common.mms.PartAuthority;
import com.bcm.messenger.common.mms.Slide;
import com.bcm.messenger.common.mms.SlideDeck;
import com.bcm.messenger.common.preferences.TextSecurePreferences;
import com.bcm.messenger.common.recipients.Recipient;
import com.bcm.messenger.common.utils.ConversationUtils;
import com.bcm.messenger.common.utils.GroupUtil;
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils;
import com.bcm.messenger.utility.AmeTimeUtil;
import com.bcm.messenger.utility.AppContextHolder;
import com.bcm.messenger.utility.DelimiterUtil;
import com.bcm.messenger.utility.StringAppearanceUtil;
import com.bcm.messenger.utility.Util;
import com.bcm.messenger.utility.logger.ALog;

import org.jetbrains.annotations.NotNull;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.util.Pair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    private ConcurrentHashMap<Long, Address> threadCache = new ConcurrentHashMap<>();

    public ThreadDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);

        initCache();
    }

    private void initCache() {
        threadCache.clear();

        Cursor cursor = null;
        try {
            cursor = this.getConversationList();
            while (cursor != null && cursor.moveToNext()) {
                String address = cursor.getString(cursor.getColumnIndexOrThrow(ADDRESS));
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
                threadCache.put(id, Address.from(AppContextHolder.APP_CONTEXT, address));
            }
        } catch (Throwable e) {
            ALog.e(TAG, "thread cache init failed", e);
        }
        finally {
            if (cursor != null){
                try {
                    cursor.close();
                } catch (Throwable e) {
                    ALog.e(TAG, "thread cache init close cursor failed", e);
                }
            }
        }
    }

    
    private long createThreadForRecipient(Address address, boolean group, int distributionType) {
        ContentValues contentValues = new ContentValues(5);
        long date = System.currentTimeMillis();
        contentValues.put(DATE, date - date % 1000);
        contentValues.put(ADDRESS, address.serialize());
        contentValues.put(LAST_SEEN, 0);
        if (group) {
            contentValues.put(TYPE, distributionType);
        }
        contentValues.put(PIN, 0L);
        contentValues.put(MESSAGE_COUNT, 0);

        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        long threadId = db.insert(TABLE_NAME, null, contentValues);
        notifyConversationListListeners();

        threadCache.put(threadId, address);
        return threadId;
    }

    private void updateThread(long threadId, long count, String body, @Nullable Uri attachment,
                              long date, int status, int deliveryReceiptCount, long type, boolean unarchive,
                              long expiresIn, int readReceiptCount) {
        ContentValues contentValues = new ContentValues(7);
        contentValues.put(DATE, date - date % 1000);
        contentValues.put(MESSAGE_COUNT, count);
        contentValues.put(SNIPPET, body);
        contentValues.put(SNIPPET_URI, attachment == null ? null : attachment.toString());
        contentValues.put(SNIPPET_TYPE, type);
        contentValues.put(STATUS, status);
        contentValues.put(DELIVERY_RECEIPT_COUNT, deliveryReceiptCount);
        contentValues.put(READ_RECEIPT_COUNT, readReceiptCount);
        contentValues.put(EXPIRES_IN, expiresIn);

        if (unarchive) {
            contentValues.put(ARCHIVED, 0);
        }

        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.update(TABLE_NAME, contentValues, ID + " = ?", new String[]{threadId + ""});
        notifyConversationListListeners();
    }

    public void updateSnippet(long threadId, String snippet, @Nullable Uri attachment, long date, long type, boolean unarchive) {
        ContentValues contentValues = new ContentValues(4);

        contentValues.put(DATE, date - date % 1000);
        contentValues.put(SNIPPET, snippet);
        contentValues.put(SNIPPET_TYPE, type);
        contentValues.put(SNIPPET_URI, attachment == null ? null : attachment.toString());

        if (unarchive) {
            contentValues.put(ARCHIVED, 0);
        }

        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.update(TABLE_NAME, contentValues, ID + " = ?", new String[]{threadId + ""});
        notifyConversationListListeners();
    }


    private void deleteThread(long threadId) {
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.delete(TABLE_NAME, ID_WHERE, new String[]{threadId + ""});
        threadCache.remove(threadId);
        notifyConversationListListeners();
    }

    private void deleteThreads(Set<Long> threadIds) {
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        String where = "";

        for (long threadId : threadIds) {
            where += ID + " = '" + threadId + "' OR ";
            threadCache.remove(threadId);
        }

        where = where.substring(0, where.length() - 4);

        db.delete(TABLE_NAME, where, null);

        notifyConversationListListeners();
    }

    private void deleteAllThreads() {
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.delete(TABLE_NAME, null, null);

        threadCache.clear();
        notifyConversationListListeners();
    }

    public void trimAllThreads(int length, ProgressListener listener) {
        Cursor cursor = null;
        int threadCount = 0;
        int complete = 0;

        try {
            cursor = this.getConversationList();

            if (cursor != null) {
                threadCount = cursor.getCount();
            }
            while (cursor != null && cursor.moveToNext()) {
                long threadId = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
                trimThread(threadId, length);

                listener.onProgress(++complete, threadCount);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public List<Address> getThreadList() {
        return new ArrayList<>(threadCache.values());
    }

    public void trimThread(long threadId, int length) {
        Log.w("ThreadDatabase", "Trimming thread: " + threadId + " to: " + length);
        Cursor cursor = null;

        try {
            cursor = DatabaseFactory.getMmsSmsDatabase(context).getConversation(threadId);

            if (cursor != null && length > 0 && cursor.getCount() > length) {
                Log.w("ThreadDatabase", "Cursor count is greater than length!");
                cursor.moveToPosition(length - 1);

                long lastTweetDate = cursor.getLong(cursor.getColumnIndexOrThrow(MmsSmsColumns.NORMALIZED_DATE_RECEIVED));

                Log.w("ThreadDatabase", "Cut off tweet date: " + lastTweetDate);

                DatabaseFactory.getSmsDatabase(context).deleteMessagesInThreadBeforeDate(threadId, lastTweetDate);
                DatabaseFactory.getMmsDatabase(context).deleteMessagesInThreadBeforeDate(threadId, lastTweetDate);

                update(threadId, false);
                notifyConversationListeners(threadId);
                notifyConversationListeners(threadId);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public List<MarkedMessageInfo> setRead(long threadId, boolean lastSeen) {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(READ, 1);
        contentValues.put(UNREAD_COUNT, 0);

        if (lastSeen) {
            contentValues.put(LAST_SEEN, System.currentTimeMillis());
        }

        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.update(TABLE_NAME, contentValues, ID_WHERE, new String[]{threadId + ""});

        final List<MarkedMessageInfo> smsRecords = DatabaseFactory.getSmsDatabase(context).setMessagesRead(threadId);
        final List<MarkedMessageInfo> mmsRecords = DatabaseFactory.getMmsDatabase(context).setMessagesRead(threadId);

        notifyConversationListListeners();

        return new LinkedList<MarkedMessageInfo>() {{
            addAll(smsRecords);
            addAll(mmsRecords);
        }};
    }

   
    public void setReadList(List<ThreadRecord> threads, boolean lastSeen) {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(READ, 1);
        contentValues.put(UNREAD_COUNT, 0);

        if (lastSeen) {
            contentValues.put(LAST_SEEN, System.currentTimeMillis());
        }

        SQLiteDatabase db = databaseHelper.getWritableDatabase();

        for (int i = 0; i < threads.size(); i++) {
            ThreadRecord threadRecord = threads.get(i);
            long threadId = threadRecord.getThreadId();
            db.update(TABLE_NAME, contentValues, ID_WHERE, new String[]{threadId + ""});
            if (threadRecord.isNewGroup()) {
                long gid = threadRecord.getRecipient().getGroupId();
                MessageDataManager.INSTANCE.setGroupMessageRead(gid);
            } else {
                final List<MarkedMessageInfo> smsRecords = DatabaseFactory.getSmsDatabase(context).setMessagesRead(threadId);
                final List<MarkedMessageInfo> mmsRecords = DatabaseFactory.getMmsDatabase(context).setMessagesRead(threadId);
            }
        }

        notifyConversationListListeners();
    }


  
    public void setReadForNewGroup(long threadId, long gid, long lastSeen) {
        MessageDataManager.INSTANCE.setGroupMessageRead(gid);
        ContentValues contentValues = new ContentValues(3);
        contentValues.put(READ, 1);
        contentValues.put(UNREAD_COUNT, 0);
        contentValues.put(LAST_SEEN, lastSeen);
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.update(TABLE_NAME, contentValues, ID_WHERE, new String[]{threadId + ""});

        notifyConversationListListeners();
    }

    public void incrementUnread(long threadId, int amount) {
        boolean broadcastFlag = false;
        if (DatabaseFactory.getMmsSmsDatabase(context).getUnreadCount(threadId) == 0) {
            broadcastFlag = true;
        }
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_NAME + " SET " + READ + " = 0, " +
                        UNREAD_COUNT + " = " + UNREAD_COUNT + " +? WHERE " + ID + " = ?",
                new String[]{String.valueOf(amount),
                        String.valueOf(threadId)});

        if (broadcastFlag) {
            notifyConversationListListeners();
        }
    }

    public void setDistributionType(long threadId, int distributionType) {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(TYPE, distributionType);

        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.update(TABLE_NAME, contentValues, ID_WHERE, new String[]{threadId + ""});
        notifyConversationListListeners();
    }


    
    public int getDistributionType(long threadId) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, new String[]{TYPE}, ID_WHERE, new String[]{String.valueOf(threadId)}, null, null, null);

        try {
            if (cursor != null && cursor.moveToNext()) {
                return cursor.getInt(cursor.getColumnIndexOrThrow(TYPE));
            }

            return DistributionTypes.DEFAULT;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

    }

    public Cursor getFilteredConversationList(@Nullable List<Address> filter) {
        if (filter == null || filter.size() == 0) {
            return null;
        }

        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        List<List<Address>> partitionedAddresses = Util.partition(filter, 900);
        List<Cursor> cursors = new LinkedList<>();

        for (List<Address> addresses : partitionedAddresses) {
            String selection = TABLE_NAME + "." + ADDRESS + " = ?";
            String[] selectionArgs = new String[addresses.size()];

            for (int i = 0; i < addresses.size() - 1; i++) {
                selection += (" OR " + TABLE_NAME + "." + ADDRESS + " = ?");
            }

            int i = 0;
            for (Address address : addresses) {
                selectionArgs[i++] = DelimiterUtil.escape(address.serialize(), ' ');
            }

            String query = createCoversationListQuery(selection);
            cursors.add(db.rawQuery(query, selectionArgs));
        }

        Cursor cursor = cursors.size() > 1 ? new MergeCursor(cursors.toArray(new Cursor[cursors.size()])) : cursors.get(0);
        setNotifyConversationListListeners(cursor);
        return cursor;
    }

    public Cursor getConversationList() {
        return getConversationList("0");
    }

    public Cursor getArchivedConversationList() {
        return getConversationList("1");
    }

    private Cursor getConversationList(String archived) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        String query = createCoversationListQuery(ARCHIVED + " = ?  AND " + MESSAGE_COUNT + " != 0");
        Cursor cursor = db.rawQuery(query, new String[]{archived});
        setNotifyConversationListListeners(cursor);

        return cursor;
    }

    public Cursor getConversationListHasNull() {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        String query = createCoversationListQuery(ARCHIVED + " = ?");
        Cursor cursor = db.rawQuery(query, new String[]{"0"});

        setNotifyConversationListListeners(cursor);

        return cursor;
    }

    public Cursor getDirectShareList() {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        String query = createQuery(MESSAGE_COUNT + " != 0");

        return db.rawQuery(query, null);
    }

    public int getArchivedConversationListCount() {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.query(TABLE_NAME, new String[]{"COUNT(*)"}, ARCHIVED + " = ?",
                    new String[]{"1"}, null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(0);
            }

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return 0;
    }

    @WorkerThread
    public long getPin(long threadId) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();

        try (Cursor cursor = db.query(TABLE_NAME, new String[]{PIN}, ID_WHERE, new String[]{String.valueOf(threadId)}, null, null, null)) {
            if (cursor != null && cursor.moveToNext()) {
                return cursor.getLong(cursor.getColumnIndexOrThrow(PIN));
            }
            
            return 0;
        } catch (Exception ex) {
            return 0;
        }
    }

    public void setPin(long threadId) {
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(PIN, System.currentTimeMillis());

        db.update(TABLE_NAME, contentValues, ID_WHERE, new String[]{threadId + ""});
        notifyConversationListListeners();
    }

    public void deletePin(long threadId) {
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(PIN, 0);

        db.update(TABLE_NAME, contentValues, ID_WHERE, new String[]{threadId + ""});
        notifyConversationListListeners();
    }

    public void archiveConversation(long threadId) {
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(ARCHIVED, 1);

        db.update(TABLE_NAME, contentValues, ID_WHERE, new String[]{threadId + ""});
        notifyConversationListListeners();
    }

    public void unarchiveConversation(long threadId) {
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(ARCHIVED, 0);

        db.update(TABLE_NAME, contentValues, ID_WHERE, new String[]{threadId + ""});
        notifyConversationListListeners();
    }

    public void setLastSeen(long threadId) {
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(LAST_SEEN, System.currentTimeMillis());

        db.update(TABLE_NAME, contentValues, ID_WHERE, new String[]{String.valueOf(threadId)});
        notifyConversationListListeners();
    }

    public void setDecryptFailData(long threadId, String dataJson) {
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(DECRYPT_FAIL_DATA, dataJson);

        db.update(TABLE_NAME, contentValues, ID_WHERE, new String[]{Long.toString(threadId)});
    }

    public void setDecryptFailData(String recipientId, String dataJson) {
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(DECRYPT_FAIL_DATA, dataJson);
        db.update(TABLE_NAME, contentValues, ADDRESS + " = ?", new String[]{recipientId});
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

    public String getDecryptFailData(String recipientId) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, new String[]{DECRYPT_FAIL_DATA}, ADDRESS + " = ?", new String[]{recipientId}, null, null, null);
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

 
    public void deleteConversationContentForNewGroup(long gid, long threadId) {
        DatabaseFactory.getDraftDatabase(context).clearDrafts(threadId);
        deleteThread(threadId);
        GroupLiveInfoManager.Companion.getInstance().deleteLiveInfoWhenLeaveGroup(gid);
        MessageDataManager.INSTANCE.deleteMessagesByGid(gid);
        notifyConversationListListeners();
    }

  
    public void deleteConversationForNewGroup(long gid, long threadId) {
        DatabaseFactory.getDraftDatabase(context).clearDrafts(threadId);
        GroupLiveInfoManager.Companion.getInstance().deleteLiveInfoWhenLeaveGroup(gid);
        MessageDataManager.INSTANCE.deleteMessagesByGid(gid);
        notifyConversationListListeners();
    }

   
    public void clearConversationContent(long threadId) {
        DatabaseFactory.getSmsDatabase(context).deleteThread(threadId);
        DatabaseFactory.getMmsDatabase(context).deleteThread(threadId);
        DatabaseFactory.getDraftDatabase(context).clearDrafts(threadId);
        updateSnippet(threadId, "",
                null,
                System.currentTimeMillis(), MmsSmsColumns.Types.BASE_DRAFT_TYPE, false);
        updateReadState(threadId);

        notifyConversationListeners(threadId);
        notifyConversationListListeners();
    }

   
    public void clearConversationExcept(long threadId, long... messageId) {
        DatabaseFactory.getSmsDatabase(context).deleteMessagesExcept(threadId, messageId);
        DatabaseFactory.getMmsDatabase(context).deleteAllExcept(threadId);
        updateReadState(threadId);

    }

    public void deleteConversationContent(long threadId) {
        DatabaseFactory.getSmsDatabase(context).deleteThread(threadId);
        DatabaseFactory.getMmsDatabase(context).deleteThread(threadId);
        DatabaseFactory.getDraftDatabase(context).clearDrafts(threadId);
        deleteThread(threadId);
        notifyConversationListeners(threadId);
        notifyConversationListListeners();
    }

    public void deleteConversations(Set<Long> selectedConversations) {
        DatabaseFactory.getSmsDatabase(context).deleteThreads(selectedConversations);
        DatabaseFactory.getMmsDatabase(context).deleteThreads(selectedConversations);
        DatabaseFactory.getDraftDatabase(context).clearDrafts(selectedConversations);
        deleteThreads(selectedConversations);
        notifyConversationListeners(selectedConversations);
        notifyConversationListListeners();
    }

    public void deleteAllConversations() {
        DatabaseFactory.getSmsDatabase(context).deleteAllThreads();
        DatabaseFactory.getMmsDatabase(context).deleteAllThreads();
        DatabaseFactory.getDraftDatabase(context).clearAllDrafts();
        deleteAllThreads();
    }

    public long getThreadIdIfExistsFor(Recipient recipient) {
        return getThreadIdIfExistsFor(recipient.getAddress());
    }

    public long getThreadIdIfExistsFor(Address address) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        String where = ADDRESS + " = ?";
        String[] recipientsArg = new String[]{address.serialize()};

        Cursor cursor = null;

        try {
            cursor = db.query(TABLE_NAME, new String[]{ID}, where, recipientsArg, null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndexOrThrow(ID));
            } else {
                return -1L;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public long getThreadIdFor(Recipient recipient) {
        if (recipient.getAddress().isGroup() && GroupUtil.isTTGroup(recipient.getAddress().toGroupString())) {
            return getThreadIdFor(recipient, DistributionTypes.NEW_GROUP);
        } else {
            return getThreadIdFor(recipient, DistributionTypes.DEFAULT);
        }
    }

    public long getThreadIdFor(Recipient recipient, int distributionType) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        String where = ADDRESS + " = ?";
        String[] recipientsArg = new String[]{recipient.getAddress().serialize()};

        Cursor cursor = null;

        synchronized (this) {
            try {
                cursor = db.query(TABLE_NAME, new String[]{ID}, where, recipientsArg, null, null, null);

                if (cursor != null && cursor.moveToFirst()) {
                    return cursor.getLong(cursor.getColumnIndexOrThrow(ID));
                } else {
                    //ALog.i(TAG,ClassHelper.getCallStack());
                    return createThreadForRecipient(recipient.getAddress(), recipient.isGroupRecipient(), distributionType);
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
    }

    public @Nullable
    Recipient getRecipientForThreadId(long threadId) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.query(TABLE_NAME, null, ID + " = ?", new String[]{threadId + ""}, null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                Address address = Address.fromSerialized(cursor.getString(cursor.getColumnIndexOrThrow(ADDRESS)));
                return Recipient.from(context, address, false);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return null;
    }

   
    public void setHasSent(long threadId, boolean hasSent) {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(HAS_SENT, hasSent ? 1 : 0);

        databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues, ID_WHERE,
                new String[]{String.valueOf(threadId)});

    }

  
    void updateReadStateForNewGroup(long threadId, int unreadCount) {
        ALog.i(TAG, "updateReadStateForNewGroup threadId: " + threadId + ", unreadCount: " + unreadCount);
        ContentValues contentValues = new ContentValues();
        contentValues.put(READ, unreadCount == 0 ? 1 : 0);
        contentValues.put(UNREAD_COUNT, unreadCount);

        databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues, ID_WHERE,
                new String[]{String.valueOf(threadId)});

        notifyConversationListListeners();
    }


    void updateReadState(long threadId) {
        int unreadCount = DatabaseFactory.getMmsSmsDatabase(context).getUnreadCount(threadId);

        ContentValues contentValues = new ContentValues();
        contentValues.put(READ, unreadCount == 0 ? 1 : 0);
        contentValues.put(UNREAD_COUNT, unreadCount);

        databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues, ID_WHERE,
                new String[]{String.valueOf(threadId)});

        notifyConversationListListeners();

    }

    
    public void updateLiveState(long gid, int liveState) {
        long threadId;
        if (liveState == GroupLiveInfo.LiveStatus.REMOVED.getValue() || liveState == GroupLiveInfo.LiveStatus.STOPED.getValue()||liveState == GroupLiveInfo.LiveStatus.EMPTY.getValue()) {
            threadId = getThreadIdIfExistsFor(Recipient.recipientFromNewGroupId(AppContextHolder.APP_CONTEXT, gid));
        } else {
            threadId = getThreadIdFor(Recipient.recipientFromNewGroupId(AppContextHolder.APP_CONTEXT, gid));
        }
        if (threadId < 0) {
            return;
        }
        ContentValues contentValues = new ContentValues();
        contentValues.put(LIVE_STATE, liveState);
        databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues, ID_WHERE,
                new String[]{String.valueOf(threadId)});
        notifyConversationListListeners();
    }

   
    public boolean update(long threadId, boolean unarchive) {
        MmsSmsDatabase mmsSmsDatabase = DatabaseFactory.getMmsSmsDatabase(context);
        MmsSmsDatabase.Reader reader = null;
        try {
            reader = mmsSmsDatabase.readerFor(mmsSmsDatabase.getConversationSnippet(threadId));
            MessageRecord record;

            if (reader != null && (record = reader.getNext()) != null) {
                final Slide snipSlide = getAttachmentFor(record);
                ALog.i(TAG, "update threadId " + threadId + ", snipSlide is null: " + (snipSlide == null));
                Uri uri = snipSlide != null ? (snipSlide.getThumbnailUri() == null ? snipSlide.getUri() : snipSlide.getThumbnailUri()) : null;
                
                if (uri == null && snipSlide != null) {
                    try {
                        Attachment att = snipSlide.asAttachment();
                        if (att instanceof DatabaseAttachment) {
                            DatabaseAttachment datt = (DatabaseAttachment) att;
                            uri = PartAuthority.getAttachmentDataUri(datt.getAttachmentId());
                        }
                    } catch (Exception ex) {
                        ALog.e(TAG, "update thread database, find snip uri error", ex);
                    }
                }
                String snipBody = record.getBody().getBody();
                if (TextUtils.isEmpty(snipBody)) {
                    String contentType = snipSlide != null ? snipSlide.getContentType() : null;
                    if (contentType != null) {
                        snipBody = getEncryptedBody(contentType);
                    }
                }
                long count = mmsSmsDatabase.getConversationCount(threadId);
                updateThread(threadId, count, snipBody, uri,
                        record.getTimestamp(), record.getDeliveryStatus(), record.getDeliveryReceiptCount(),
                        record.getType(), unarchive, record.getExpiresIn(), record.getReadReceiptCount());
                notifyConversationListListeners();
                return false;

            } else {
                
//                updateThread(threadId, count, "", null,
//                        System.currentTimeMillis(), -1, 0, -2136997865
//                        , true, 0, 0);
//                notifyConversationListListeners();
                return true;
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    private String getEncryptedBody(String body) {
        try {
            MasterCipher bodyCipher = new MasterCipher(BCMEncryptUtils.INSTANCE.getMasterSecret(AppContextHolder.APP_CONTEXT));
            return bodyCipher.encryptBody(body);
        } catch (Exception ex) {
            return body;
        }
    }

 
    public void updateByNewGroup(long gid) {
        AmeGroupMessageDetail message = GroupMessageTransform.INSTANCE.transformToModel(MessageDataManager.INSTANCE.fetchLastMessage(gid));
        long threadId = getThreadIdIfExistsFor(Recipient.recipientFromNewGroupId(AppContextHolder.APP_CONTEXT, gid));
        if (threadId == -1L && (null == message || null == message.getMessage())) {
            
            ALog.i(TAG, "updateByNewGroup gid: " + gid + ", no thread, no message, return");
            return;
        } else if(threadId == -1L) {
            threadId = getThreadIdFor(Recipient.recipientFromNewGroupId(AppContextHolder.APP_CONTEXT, gid));
        }

        if (null == message || null == message.getMessage()) {
            updateByNewGroup(threadId, 0, "", "", AmeGroupMessageDetail.SendState.SEND_SUCCESS, AmeTimeUtil.INSTANCE.serverTimeMillis());
        } else {
            //Pair<Long, Boolean> lastSeenPair = getLastSeenAndHasSent(threadId);
            long count = MessageDataManager.INSTANCE.countMessageByGid(gid);
            long unreadCount = MessageDataManager.INSTANCE.countUnreadCountFromLastSeen(gid, 0);
            updateReadStateForNewGroup(threadId, (int) unreadCount);

            String body = message.getMessage().toString();
            updateByNewGroup(threadId, count, body, message.getAttachmentUri() == null ? "" : message.getAttachmentUri(), message.getSendState(), message.getSendTime());

            GroupInfoDataManager.INSTANCE.queryOneAmeGroupInfo(gid);
        }
    }

   
    private void updateByNewGroup(long threadId, long count, String body, String attachmentUri, AmeGroupMessageDetail.SendState state, long date) {
        updateThread(threadId, count, body, Uri.parse(attachmentUri),
                date, -1, 0, state.getValue()
                , true, 0, 0);
        notifyConversationListListeners();
    }

  
    public int getAllUnreadThreadCount() {
        return getAllUnreadThread().size();
    }

    public Set<Address> getAllUnreadThread() {
        MasterSecret ms = BCMEncryptUtils.INSTANCE.getMasterSecret(AppContextHolder.APP_CONTEXT);
        Cursor cursor = getConversationListHasNull();
        Reader threadReader = readerFor(cursor, new MasterCipher(ms));
        HashSet<Address> list = new HashSet<>();
        if (threadReader.resetCursor()) {
            ThreadRecord record;
            do {
                record = threadReader.getCurrent();
                if (record != null && record.getUnreadCount() > 0) {
                    if (!record.getRecipient().isMuted()) {
                        list.add(record.getRecipient().getAddress());
                    }
                }
            } while (threadReader.getNext() != null);
        }
        return list;
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

    public void setProfileRequest(long threadId, boolean hasRequest) {
        ContentValues contentValues = new ContentValues();
        if (hasRequest) {
            contentValues.put(PROFILE_REQUEST, 1);
        } else {
            contentValues.put(PROFILE_REQUEST, 0);
        }
        databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues, ID_WHERE, new String[] {String.valueOf(threadId)});
    }

    private @Nullable
    Uri getAttachmentUriFor(MessageRecord record) {
        if (!record.isMms() || record.isMmsNotification() || record.isGroupAction()) {
            return null;
        }

        SlideDeck slideDeck = ((MediaMmsMessageRecord) record).getSlideDeck();
        Slide thumbnail = slideDeck.getThumbnailSlide();

        return thumbnail != null ? thumbnail.getThumbnailUri() : null;
    }

    private @Nullable
    Slide getAttachmentFor(MessageRecord record) {
        if (!record.isMms() || record.isMmsNotification() || record.isGroupAction()) {
            return null;
        }

        SlideDeck slideDeck = ((MediaMmsMessageRecord) record).getSlideDeck();
        Slide slide = slideDeck.getThumbnailSlide();
        if (slide == null) {
            slide = slideDeck.getDocumentSlide();
        }
        if (slide == null) {
            slide = slideDeck.getAudioSlide();
        }
        return slide;

    }

    private @NonNull
    String createQuery(@NonNull String where) {
        String projection = StringAppearanceUtil.INSTANCE.join(COMBINED_THREAD_RECIPIENT_GROUP_PROJECTION, ",");
        return "SELECT " + projection + " FROM " + TABLE_NAME +
                " LEFT OUTER JOIN " + RecipientDatabase.TABLE_NAME +
                " ON " + TABLE_NAME + "." + ADDRESS + " = " + RecipientDatabase.TABLE_NAME + "." + RecipientDatabase.ADDRESS +
                " LEFT OUTER JOIN " + GroupDatabase.TABLE_NAME +
                " ON " + TABLE_NAME + "." + ADDRESS + " = " + GroupDatabase.TABLE_NAME + "." + GroupDatabase.GROUP_ID +
                " WHERE " + where +
                " ORDER BY " + TABLE_NAME + "." + DATE + " DESC";
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
        initCache();
    }

    public interface ProgressListener {
        void onProgress(int complete, int total);
    }

    public Reader readerFor(Cursor cursor, MasterCipher masterCipher) {
        return new Reader(cursor, masterCipher);
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

        public Reader(Cursor cursor, MasterCipher masterCipher) {
            this.cursor = cursor;
            this.masterCipher = masterCipher;
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
            return Address.fromSerialized(cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.ADDRESS)));
        }

        public ThreadRecord getCurrentForMigrate() {
            long threadId = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.ID));
            int distributionType = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.TYPE));
            Address address = Address.fromSerialized(cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.ADDRESS)));

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

            if (!TextSecurePreferences.isReadReceiptsEnabled(context)) {
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
            Address address = Address.fromSerialized(cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.ADDRESS)));

            Recipient recipient = Recipient.from(context, address, true);

            
            ConversationUtils.INSTANCE.addConversationCache(recipient, threadId);

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

            if (!TextSecurePreferences.isReadReceiptsEnabled(context)) {
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
