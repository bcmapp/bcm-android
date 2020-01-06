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
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.R;
import com.bcm.messenger.common.attachments.Attachment;
import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.common.crypto.MasterCipher;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.database.documents.IdentityKeyMismatch;
import com.bcm.messenger.common.database.documents.IdentityKeyMismatchList;
import com.bcm.messenger.common.database.documents.NetworkFailure;
import com.bcm.messenger.common.database.documents.NetworkFailureList;
import com.bcm.messenger.common.mms.OutgoingMediaMessage;
import com.bcm.messenger.common.mms.SlideDeck;
import com.bcm.messenger.common.preferences.TextSecurePreferences;
import com.bcm.messenger.common.recipients.Recipient;
import com.bcm.messenger.utility.AmeTimeUtil;
import com.bcm.messenger.utility.GsonUtils;
import com.bcm.messenger.utility.StringAppearanceUtil;
import com.google.gson.JsonParseException;

import org.whispersystems.libsignal.InvalidMessageException;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.List;

@Deprecated
public class MmsDatabase extends MessagingDatabase {

    private static final String TAG = MmsDatabase.class.getSimpleName();

    public static final String TABLE_NAME = "mms";
    static final String DATE_SENT = "date";
    static final String DATE_RECEIVED = "date_received";
    public static final String MESSAGE_BOX = "msg_box";
    static final String CONTENT_LOCATION = "ct_l";
    static final String EXPIRY = "exp";
    public static final String MESSAGE_TYPE = "m_type";
    static final String MESSAGE_SIZE = "m_size";
    static final String STATUS = "st";
    static final String TRANSACTION_ID = "tr_id";
    static final String PART_COUNT = "part_count";
    static final String NETWORK_FAILURE = "network_failures";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY, " +
            THREAD_ID + " INTEGER, " + DATE_SENT + " INTEGER, " + DATE_RECEIVED + " INTEGER, " + MESSAGE_BOX + " INTEGER, " +
            READ + " INTEGER DEFAULT 0, " + "m_id" + " TEXT, " + "sub" + " TEXT, " +
            "sub_cs" + " INTEGER, " + BODY + " TEXT, " + PART_COUNT + " INTEGER, " +
            "ct_t" + " TEXT, " + CONTENT_LOCATION + " TEXT, " + ADDRESS + " TEXT, " +
            ADDRESS_DEVICE_ID + " INTEGER, " +
            EXPIRY + " INTEGER, " + "m_cls" + " TEXT, " + MESSAGE_TYPE + " INTEGER, " +
            "v" + " INTEGER, " + MESSAGE_SIZE + " INTEGER, " + "pri" + " INTEGER, " +
            "rr" + " INTEGER, " + "rpt_a" + " INTEGER, " + "resp_st" + " INTEGER, " +
            STATUS + " INTEGER, " + TRANSACTION_ID + " TEXT, " + "retr_st" + " INTEGER, " +
            "retr_txt" + " TEXT, " + "retr_txt_cs" + " INTEGER, " + "read_status" + " INTEGER, " +
            "ct_cls" + " INTEGER, " + "resp_txt" + " TEXT, " + "d_tm" + " INTEGER, " +
            DELIVERY_RECEIPT_COUNT + " INTEGER DEFAULT 0, " + MISMATCHED_IDENTITIES + " TEXT DEFAULT NULL, " +
            NETWORK_FAILURE + " TEXT DEFAULT NULL," + "d_rpt" + " INTEGER, " +
            SUBSCRIPTION_ID + " INTEGER DEFAULT -1, " + EXPIRES_IN + " INTEGER DEFAULT 0, " +
            EXPIRE_STARTED + " INTEGER DEFAULT 0, " + NOTIFIED + " INTEGER DEFAULT 0, " +
            READ_RECEIPT_COUNT + " INTEGER DEFAULT 0);";

    public static final String[] CREATE_INDEXS = {
            "CREATE INDEX IF NOT EXISTS mms_thread_id_index ON " + TABLE_NAME + " (" + THREAD_ID + ");",
            "CREATE INDEX IF NOT EXISTS mms_read_index ON " + TABLE_NAME + " (" + READ + ");",
            "CREATE INDEX IF NOT EXISTS mms_read_and_notified_and_thread_id_index ON " + TABLE_NAME + "(" + READ + "," + NOTIFIED + "," + THREAD_ID + ");",
            "CREATE INDEX IF NOT EXISTS mms_message_box_index ON " + TABLE_NAME + " (" + MESSAGE_BOX + ");",
            "CREATE INDEX IF NOT EXISTS mms_date_sent_index ON " + TABLE_NAME + " (" + DATE_SENT + ");",
            "CREATE INDEX IF NOT EXISTS mms_thread_date_index ON " + TABLE_NAME + " (" + THREAD_ID + ", " + DATE_RECEIVED + ");"
    };

    private static final String[] MMS_PROJECTION = new String[]{
            MmsDatabase.TABLE_NAME + "." + ID + " AS " + ID,
            THREAD_ID, DATE_SENT + " AS " + NORMALIZED_DATE_SENT,
            DATE_RECEIVED + " AS " + NORMALIZED_DATE_RECEIVED,
            MESSAGE_BOX, READ,
            CONTENT_LOCATION, EXPIRY, MESSAGE_TYPE,
            MESSAGE_SIZE, STATUS, TRANSACTION_ID,
            BODY, PART_COUNT, ADDRESS, ADDRESS_DEVICE_ID,
            DELIVERY_RECEIPT_COUNT, READ_RECEIPT_COUNT, MISMATCHED_IDENTITIES, NETWORK_FAILURE, SUBSCRIPTION_ID,
            EXPIRES_IN, EXPIRE_STARTED, NOTIFIED,
            AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.ROW_ID + " AS " + AttachmentDatabase.ATTACHMENT_ID_ALIAS,
            AttachmentDatabase.UNIQUE_ID,
            AttachmentDatabase.MMS_ID,
            AttachmentDatabase.SIZE,
            AttachmentDatabase.FILE_NAME,
            AttachmentDatabase.DATA,
            AttachmentDatabase.THUMBNAIL,
            AttachmentDatabase.CONTENT_TYPE,
            AttachmentDatabase.CONTENT_LOCATION,
            AttachmentDatabase.DIGEST,
            AttachmentDatabase.FAST_PREFLIGHT_ID,
            AttachmentDatabase.VOICE_NOTE,
            AttachmentDatabase.CONTENT_DISPOSITION,
            AttachmentDatabase.NAME,
            AttachmentDatabase.TRANSFER_STATE,
            AttachmentDatabase.DURATION,
            AttachmentDatabase.URL
    };

    private static final String RAW_ID_WHERE = TABLE_NAME + "._id = ?";

    
    public static final String DROP_TABLE = "DROP TABLE " + TABLE_NAME;

    public MmsDatabase(Context context, AccountContext accountContext, SQLiteOpenHelper databaseHelper) {
        super(context, accountContext, databaseHelper);
    }

    @Override
    protected String getTableName() {
        return TABLE_NAME;
    }

    private Cursor rawQuery(@NonNull String where, @Nullable String[] arguments) {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        return database.rawQuery("SELECT " + StringAppearanceUtil.INSTANCE.join(MMS_PROJECTION, ",") +
                " FROM " + MmsDatabase.TABLE_NAME + " LEFT OUTER JOIN " + AttachmentDatabase.TABLE_NAME +
                " ON (" + MmsDatabase.TABLE_NAME + "." + MmsDatabase.ID + " = " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.MMS_ID + ")" +
                " WHERE " + where, arguments);
    }

    public Cursor getMessage(long messageId) {
        Cursor cursor = rawQuery(RAW_ID_WHERE, new String[]{messageId + ""});
        return cursor;
    }

    public Reader readerFor(MasterSecret masterSecret, Cursor cursor) {
        return new Reader(masterSecret, cursor);
    }

    public OutgoingMessageReader readerFor(OutgoingMediaMessage message, long threadId) {
        return new OutgoingMessageReader(message, threadId);
    }

    public static class Status {
        public static final int DOWNLOAD_INITIALIZED = 1;
        public static final int DOWNLOAD_NO_CONNECTIVITY = 2;
        public static final int DOWNLOAD_CONNECTING = 3;
        public static final int DOWNLOAD_SOFT_FAILURE = 4;
        public static final int DOWNLOAD_HARD_FAILURE = 5;
        public static final int DOWNLOAD_APN_UNAVAILABLE = 6;
    }

    public class OutgoingMessageReader {

        private final OutgoingMediaMessage message;
        private final long id;
        private final long threadId;

        public OutgoingMessageReader(OutgoingMediaMessage message, long threadId) {
            try {
                this.message = message;
                this.id = SecureRandom.getInstance("SHA1PRNG").nextLong();
                this.threadId = threadId;
            } catch (NoSuchAlgorithmException e) {
                throw new AssertionError(e);
            }
        }

        public MessageRecord getCurrent() {
            SlideDeck slideDeck = new SlideDeck(context, message.getAttachments());

            long time = AmeTimeUtil.INSTANCE.serverTimeMillis();
            return new MediaMmsMessageRecord(context, id, message.getRecipient(), message.getRecipient(),
                    1, time, time,
                    0, threadId, new DisplayRecord.Body(message.getBody(), true),
                    slideDeck, slideDeck.getSlides().size(),
                    message.isSecure() ? MmsSmsColumns.Types.getOutgoingEncryptedMessageType() : MmsSmsColumns.Types.getOutgoingSmsMessageType(),
                    new LinkedList<IdentityKeyMismatch>(),
                    new LinkedList<NetworkFailure>(),
                    message.getSubscriptionId(),
                    message.getExpiresIn(),
                    System.currentTimeMillis(), 0);
        }
    }

    public class Reader {

        private final Cursor cursor;
        private final MasterSecret masterSecret;
        private final MasterCipher masterCipher;

        public Reader(MasterSecret masterSecret, Cursor cursor) {
            this.cursor = cursor;
            this.masterSecret = masterSecret;

            if (masterSecret != null)
                masterCipher = new MasterCipher(masterSecret);
            else
                masterCipher = null;
        }

        public MessageRecord getCurrent(AttachmentDatabase attachmentDatabase) {
            return getMediaMmsMessageRecord(attachmentDatabase, cursor);
        }

        public MessageRecord getCurrentForMigrate(AttachmentDatabase attachmentDatabase) {
            long id = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.ID));
            long dateSent = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.NORMALIZED_DATE_SENT));
            long dateReceived = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.NORMALIZED_DATE_RECEIVED));
            long box = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.MESSAGE_BOX));
            long threadId = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.THREAD_ID));
            String address = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.ADDRESS));
            int addressDeviceId = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.ADDRESS_DEVICE_ID));
            int deliveryReceiptCount = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.DELIVERY_RECEIPT_COUNT));
            int readReceiptCount = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.READ_RECEIPT_COUNT));
            DisplayRecord.Body body = getBody(cursor);
            int partCount = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.PART_COUNT));
            String mismatchDocument = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.MISMATCHED_IDENTITIES));
            String networkDocument = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.NETWORK_FAILURE));
            int subscriptionId = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.SUBSCRIPTION_ID));
            long expiresIn = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.EXPIRES_IN));
            long expireStarted = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.EXPIRE_STARTED));

            if (!TextSecurePreferences.isReadReceiptsEnabled(accountContext)) {
                readReceiptCount = 0;
            }

            List<IdentityKeyMismatch> mismatches = getMismatchedIdentities(mismatchDocument);
            List<NetworkFailure> networkFailures = getFailures(networkDocument);
            SlideDeck slideDeck = getSlideDeck(attachmentDatabase, cursor);

            MediaMmsMessageRecord record = new MediaMmsMessageRecord(context, id, null, null,
                    addressDeviceId, dateSent, dateReceived, deliveryReceiptCount,
                    threadId, body, slideDeck, partCount, box, mismatches,
                    networkFailures, subscriptionId, expiresIn, expireStarted,
                    readReceiptCount);
            record.read = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.READ));
            record.uid = address;
            return record;
        }

        private MediaMmsMessageRecord getMediaMmsMessageRecord(AttachmentDatabase attachmentDatabase, Cursor cursor) {
            long id = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.ID));
            long dateSent = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.NORMALIZED_DATE_SENT));
            long dateReceived = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.NORMALIZED_DATE_RECEIVED));
            long box = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.MESSAGE_BOX));
            long threadId = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.THREAD_ID));
            String address = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.ADDRESS));
            int addressDeviceId = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.ADDRESS_DEVICE_ID));
            int deliveryReceiptCount = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.DELIVERY_RECEIPT_COUNT));
            int readReceiptCount = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.READ_RECEIPT_COUNT));
            DisplayRecord.Body body = getBody(cursor);
            int partCount = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.PART_COUNT));
            String mismatchDocument = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.MISMATCHED_IDENTITIES));
            String networkDocument = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.NETWORK_FAILURE));
            int subscriptionId = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.SUBSCRIPTION_ID));
            long expiresIn = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.EXPIRES_IN));
            long expireStarted = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.EXPIRE_STARTED));

            if (!TextSecurePreferences.isReadReceiptsEnabled(accountContext)) {
                readReceiptCount = 0;
            }

            Recipient recipient = getRecipientFor(address);
            List<IdentityKeyMismatch> mismatches = getMismatchedIdentities(mismatchDocument);
            List<NetworkFailure> networkFailures = getFailures(networkDocument);
            SlideDeck slideDeck = getSlideDeck(attachmentDatabase, cursor);

            MediaMmsMessageRecord record = new MediaMmsMessageRecord(context, id, recipient, recipient,
                    addressDeviceId, dateSent, dateReceived, deliveryReceiptCount,
                    threadId, body, slideDeck, partCount, box, mismatches,
                    networkFailures, subscriptionId, expiresIn, expireStarted,
                    readReceiptCount);
            record.read = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.READ));
            return record;
        }

        private Recipient getRecipientFor(String serialized) {
            Address address;

            if (TextUtils.isEmpty(serialized) || "insert-address-token".equals(serialized)) {
                address = Address.UNKNOWN;
            } else {
                address = Address.from(accountContext, serialized);

            }
            return Recipient.from(address, true);
        }

        private List<IdentityKeyMismatch> getMismatchedIdentities(String document) {
            if (!TextUtils.isEmpty(document)) {
                try {
                    return GsonUtils.INSTANCE.fromJson(document, IdentityKeyMismatchList.class).list();
                } catch (JsonParseException e) {
                    Log.w(TAG, e);
                }
            }

            return new LinkedList<>();
        }

        private List<NetworkFailure> getFailures(String document) {
            if (!TextUtils.isEmpty(document)) {
                try {
                    return GsonUtils.INSTANCE.fromJson(document, NetworkFailureList.class).list();
                } catch (JsonParseException ioe) {
                    Log.w(TAG, ioe);
                }
            }

            return new LinkedList<>();
        }

        private DisplayRecord.Body getBody(Cursor cursor) {
            try {
                String body = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.BODY));
                long box = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.MESSAGE_BOX));

                if (!TextUtils.isEmpty(body) && masterCipher != null && Types.isSymmetricEncryption(box)) {
                    return new DisplayRecord.Body(masterCipher.decryptBody(body), true);
                } else if (!TextUtils.isEmpty(body) && masterCipher == null && Types.isSymmetricEncryption(box)) {
                    return new DisplayRecord.Body(body, false);
                } else if (!TextUtils.isEmpty(body) && Types.isAsymmetricEncryption(box)) {
                    return new DisplayRecord.Body(body, false);
                } else {
                    return new DisplayRecord.Body(body == null ? "" : body, true);
                }
            } catch (InvalidMessageException e) {
                Log.w("MmsDatabase", e);
                return new DisplayRecord.Body(context.getString(R.string.MmsDatabase_error_decrypting_message), true);
            }
        }

        private SlideDeck getSlideDeck(@NonNull AttachmentDatabase attachmentDatabase, @NonNull Cursor cursor) {
            Attachment attachment = attachmentDatabase.getAttachment(masterSecret, cursor);
            return new SlideDeck(context, attachment);
        }

        public void close() {
            cursor.close();
        }
    }
}
