/*
 * Copyright (C) 2011 Whisper Systems
 * Copyright (C) 2013 - 2017 Open Whisper Systems
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
import android.text.TextUtils;
import android.util.Log;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.common.database.documents.IdentityKeyMismatch;
import com.bcm.messenger.common.database.documents.IdentityKeyMismatchList;
import com.bcm.messenger.common.preferences.TextSecurePreferences;
import com.bcm.messenger.common.provider.AMELogin;
import com.bcm.messenger.common.recipients.Recipient;
import com.bcm.messenger.common.sms.OutgoingTextMessage;
import com.bcm.messenger.utility.AmeTimeUtil;
import com.bcm.messenger.utility.GsonUtils;
import com.google.gson.JsonParseException;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.List;

/**
 * Database for storage of SMS messages.
 *
 * @author Moxie Marlinspike
 */
@Deprecated
public class SmsDatabase extends MessagingDatabase {

    private static final String TAG = SmsDatabase.class.getSimpleName();

    public static final String TABLE_NAME = "sms";
    public static final String PERSON = "person";
    static final String DATE_RECEIVED = "date";
    static final String DATE_SENT = "date_sent";
    public static final String PROTOCOL = "protocol";
    public static final String STATUS = "status";
    public static final String TYPE = "type";
    public static final String REPLY_PATH_PRESENT = "reply_path_present";
    public static final String SUBJECT = "subject";
    public static final String SERVICE_CENTER = "service_center";

    
    public static final String DURATION = "call_duration";
  
    public static final String COMMUNICATION_TYPE = "communication_type";

    
    public static final String PAYLOAD_TYPE = "payload_type";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " integer PRIMARY KEY, " +
            THREAD_ID + " INTEGER, " + ADDRESS + " TEXT, " + ADDRESS_DEVICE_ID + " INTEGER DEFAULT 1, " + PERSON + " INTEGER, " +
            DATE_RECEIVED + " INTEGER, " + DATE_SENT + " INTEGER, " + PROTOCOL + " INTEGER, " + READ + " INTEGER DEFAULT 0, " +
            STATUS + " INTEGER DEFAULT -1," + TYPE + " INTEGER, " + REPLY_PATH_PRESENT + " INTEGER, " +
            DELIVERY_RECEIPT_COUNT + " INTEGER DEFAULT 0," + SUBJECT + " TEXT, " + BODY + " TEXT, " +
            MISMATCHED_IDENTITIES + " TEXT DEFAULT NULL, " + SERVICE_CENTER + " TEXT, " + SUBSCRIPTION_ID + " INTEGER DEFAULT -1, " +
            EXPIRES_IN + " INTEGER DEFAULT 0, " + EXPIRE_STARTED + " INTEGER DEFAULT 0, " + NOTIFIED + " DEFAULT 0, " +
            READ_RECEIPT_COUNT + " INTEGER DEFAULT 0, " + DURATION + " INTEGER DEFAULT 0, " + COMMUNICATION_TYPE + " INTEGER DEFAULT 0, " + PAYLOAD_TYPE + " INTEGER DEFAULT 0 )";

    public static final String[] CREATE_INDEXS = {
            "CREATE INDEX IF NOT EXISTS sms_thread_id_index ON " + TABLE_NAME + " (" + THREAD_ID + ");",
            "CREATE INDEX IF NOT EXISTS sms_read_index ON " + TABLE_NAME + " (" + READ + ");",
            "CREATE INDEX IF NOT EXISTS sms_read_and_notified_and_thread_id_index ON " + TABLE_NAME + "(" + READ + "," + NOTIFIED + "," + THREAD_ID + ");",
            "CREATE INDEX IF NOT EXISTS sms_type_index ON " + TABLE_NAME + " (" + TYPE + ");",
            "CREATE INDEX IF NOT EXISTS sms_date_sent_index ON " + TABLE_NAME + " (" + DATE_SENT + ");",
            "CREATE INDEX IF NOT EXISTS sms_thread_date_index ON " + TABLE_NAME + " (" + THREAD_ID + ", " + DATE_RECEIVED + ");"
    };

   
    public static final String DROP_TABLE = "DROP TABLE " + TABLE_NAME;

    private static final String[] MESSAGE_PROJECTION = new String[]{
            ID, THREAD_ID, ADDRESS, ADDRESS_DEVICE_ID, PERSON,
            DATE_RECEIVED + " AS " + NORMALIZED_DATE_RECEIVED,
            DATE_SENT + " AS " + NORMALIZED_DATE_SENT,
            PROTOCOL, READ, STATUS, TYPE,
            REPLY_PATH_PRESENT, SUBJECT, BODY, SERVICE_CENTER, DELIVERY_RECEIPT_COUNT,
            MISMATCHED_IDENTITIES, SUBSCRIPTION_ID, EXPIRES_IN, EXPIRE_STARTED,
            NOTIFIED, READ_RECEIPT_COUNT, DURATION, COMMUNICATION_TYPE, PAYLOAD_TYPE
    };

    public SmsDatabase(Context context, AccountContext accountContext, SQLiteOpenHelper databaseHelper) {
        super(context, accountContext, databaseHelper);
    }

    @Override
    protected String getTableName() {
        return TABLE_NAME;
    }

    public Cursor getMessage(long messageId) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, MESSAGE_PROJECTION, ID_WHERE, new String[]{messageId + ""},
                null, null, null);
        return cursor;
    }

    public static class Status {
        public static final int STATUS_NONE = -1;
        public static final int STATUS_COMPLETE = 0;
        public static final int STATUS_PENDING = 0x20;
        public static final int STATUS_FAILED = 0x40;
    }

    public Reader readerFor(Cursor cursor) {
        return new Reader(cursor);
    }

    public OutgoingMessageReader readerFor(OutgoingTextMessage message, long threadId) {
        return new OutgoingMessageReader(message, threadId);
    }

    public class OutgoingMessageReader {

        private final OutgoingTextMessage message;
        private final long id;
        private final long threadId;

        public OutgoingMessageReader(OutgoingTextMessage message, long threadId) {
            try {
                this.message = message;
                this.threadId = threadId;
                this.id = SecureRandom.getInstance("SHA1PRNG").nextLong();
            } catch (NoSuchAlgorithmException e) {
                throw new AssertionError(e);
            }
        }

        public MessageRecord getCurrent() {
            long time = AmeTimeUtil.INSTANCE.serverTimeMillis();
            return new SmsMessageRecord(context, id, new DisplayRecord.Body(message.getMessageBody(), true),
                    message.getRecipient(), message.getRecipient(),
                    1, time, time,
                    0, message.isSecureMessage() ? MmsSmsColumns.Types.getOutgoingEncryptedMessageType() : MmsSmsColumns.Types.getOutgoingSmsMessageType(),
                    threadId, 0, new LinkedList<IdentityKeyMismatch>(),
                    message.getSubscriptionId(), message.getExpiresIn(),
                    System.currentTimeMillis(), 0);
        }
    }

    public class Reader {

        private final Cursor cursor;

        public Reader(Cursor cursor) {
            this.cursor = cursor;
        }

        public SmsMessageRecord getNext() {
            if (cursor == null || !cursor.moveToNext()) {
                return null;
            }
            return getCurrent();
        }

        public SmsMessageRecord getNextMigrate() {
            if (cursor == null || !cursor.moveToNext()) {
                return null;
            }
            return getCurrentForMigrate();
        }

        public int getCount() {
            if (cursor == null) {
                return 0;
            } else {
                return cursor.getCount();
            }
        }

        public SmsMessageRecord getCurrentForMigrate() {
            long messageId = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.ID));
            Address address = Address.from(accountContext, cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabase.ADDRESS)));
            int addressDeviceId = cursor.getInt(cursor.getColumnIndexOrThrow(SmsDatabase.ADDRESS_DEVICE_ID));
            long type = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.TYPE));
            long dateReceived = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.NORMALIZED_DATE_RECEIVED));
            long dateSent = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.NORMALIZED_DATE_SENT));
            long threadId = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.THREAD_ID));
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(SmsDatabase.STATUS));
            int deliveryReceiptCount = cursor.getInt(cursor.getColumnIndexOrThrow(SmsDatabase.DELIVERY_RECEIPT_COUNT));
            int readReceiptCount = cursor.getInt(cursor.getColumnIndexOrThrow(SmsDatabase.READ_RECEIPT_COUNT));
            String mismatchDocument = cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabase.MISMATCHED_IDENTITIES));
            int subscriptionId = cursor.getInt(cursor.getColumnIndexOrThrow(SmsDatabase.SUBSCRIPTION_ID));
            long expiresIn = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.EXPIRES_IN));
            long expireStarted = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.EXPIRE_STARTED));

            if (!TextSecurePreferences.isReadReceiptsEnabled(AMELogin.INSTANCE.getMajorContext())) {
                readReceiptCount = 0;
            }

            List<IdentityKeyMismatch> mismatches = getMismatches(mismatchDocument);
            DisplayRecord.Body body = getBody(cursor);

            SmsMessageRecord record = new SmsMessageRecord(context, messageId, body, null,
                    null,
                    addressDeviceId,
                    dateSent, dateReceived, deliveryReceiptCount, type,
                    threadId, status, mismatches, subscriptionId,
                    expiresIn, expireStarted, readReceiptCount);

            record.read = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.READ));
            record.payloadType = cursor.getInt(cursor.getColumnIndexOrThrow(SmsDatabase.PAYLOAD_TYPE));
            record.setDuration(cursor.getLong(cursor.getColumnIndex(SmsDatabase.DURATION)));
            record.setCommunicationType(cursor.getInt(cursor.getColumnIndex(SmsDatabase.COMMUNICATION_TYPE)));
            record.uid = address.serialize();
            return record;
        }

        public SmsMessageRecord getCurrent() {
            long messageId = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.ID));
            Address address = Address.from(accountContext, cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabase.ADDRESS)));
            int addressDeviceId = cursor.getInt(cursor.getColumnIndexOrThrow(SmsDatabase.ADDRESS_DEVICE_ID));
            long type = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.TYPE));
            long dateReceived = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.NORMALIZED_DATE_RECEIVED));
            long dateSent = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.NORMALIZED_DATE_SENT));
            long threadId = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.THREAD_ID));
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(SmsDatabase.STATUS));
            int deliveryReceiptCount = cursor.getInt(cursor.getColumnIndexOrThrow(SmsDatabase.DELIVERY_RECEIPT_COUNT));
            int readReceiptCount = cursor.getInt(cursor.getColumnIndexOrThrow(SmsDatabase.READ_RECEIPT_COUNT));
            String mismatchDocument = cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabase.MISMATCHED_IDENTITIES));
            int subscriptionId = cursor.getInt(cursor.getColumnIndexOrThrow(SmsDatabase.SUBSCRIPTION_ID));
            long expiresIn = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.EXPIRES_IN));
            long expireStarted = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.EXPIRE_STARTED));

            if (!TextSecurePreferences.isReadReceiptsEnabled(AMELogin.INSTANCE.getMajorContext())) {
                readReceiptCount = 0;
            }

            List<IdentityKeyMismatch> mismatches = getMismatches(mismatchDocument);
            Recipient recipient = Recipient.from(accountContext, address.serialize(), true);
            DisplayRecord.Body body = getBody(cursor);

            SmsMessageRecord record = new SmsMessageRecord(context, messageId, body, recipient,
                    recipient,
                    addressDeviceId,
                    dateSent, dateReceived, deliveryReceiptCount, type,
                    threadId, status, mismatches, subscriptionId,
                    expiresIn, expireStarted, readReceiptCount);

            record.read = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.READ));
            record.payloadType = cursor.getInt(cursor.getColumnIndexOrThrow(SmsDatabase.PAYLOAD_TYPE));
            record.setDuration(cursor.getLong(cursor.getColumnIndex(SmsDatabase.DURATION)));
            record.setCommunicationType(cursor.getInt(cursor.getColumnIndex(SmsDatabase.COMMUNICATION_TYPE)));
            return record;
        }

        private List<IdentityKeyMismatch> getMismatches(String document) {
            try {
                if (!TextUtils.isEmpty(document)) {
                    return GsonUtils.INSTANCE.fromJson(document, IdentityKeyMismatchList.class).list();
                }
            } catch (JsonParseException e) {
                Log.w(TAG, e);
            }

            return new LinkedList<>();
        }

        protected DisplayRecord.Body getBody(Cursor cursor) {
            long type = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.TYPE));
            String body = cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabase.BODY));

            if (Types.isSymmetricEncryption(type)) {
                return new DisplayRecord.Body(body, false);
            } else {
                return new DisplayRecord.Body(body, true);
            }
        }

        public void close() {
            cursor.close();
        }
    }

    public interface InsertListener {
        public void onComplete(long messageId);
    }

}
