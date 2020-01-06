package com.bcm.messenger.common.deprecated;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.utility.Base64;
import com.google.protobuf.ByteString;

import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.IOException;

import kotlin.Pair;

@Deprecated
public class PushDatabase extends Database {

    private static final String TAG = PushDatabase.class.getSimpleName();

    public static final String TABLE_NAME = "push";
    public static final String ID = "_id";
    public static final String TYPE = "type";
    public static final String SOURCE = "source";
    public static final String DEVICE_ID = "device_id";
    public static final String LEGACY_MSG = "body";
    public static final String CONTENT = "content";
    public static final String TIMESTAMP = "timestamp";
    public static final String SOURCE_REG_ID = "source_registration_id";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY, " +
            TYPE + " INTEGER, " + SOURCE + " TEXT, " + DEVICE_ID + " INTEGER, " + LEGACY_MSG + " TEXT, " + CONTENT + " TEXT, " + SOURCE_REG_ID + " INTEGER, " + TIMESTAMP + " INTEGER );";

   
    public static final String DROP_TABLE = "DROP TABLE " + TABLE_NAME;

    public PushDatabase(Context context, AccountContext accountContext, SQLiteOpenHelper databaseHelper) {
        super(context, accountContext, databaseHelper);
    }

    public Cursor getPending() {
        return databaseHelper.getReadableDatabase().query(TABLE_NAME, null, null, null, null, null, null);
    }

    public void delete(long id) {
        databaseHelper.getWritableDatabase().delete(TABLE_NAME, ID_WHERE, new String[]{id + ""});
    }

    public Reader readerFor(Cursor cursor) {
        return new Reader(cursor);
    }

    public static class Reader {
        private final Cursor cursor;

        public Reader(Cursor cursor) {
            this.cursor = cursor;
        }

        public Pair<Long, SignalServiceProtos.Envelope> getNextEnvelop() {
            try {
                if (cursor == null || !cursor.moveToNext())
                    return null;

                long id = cursor.getLong(cursor.getColumnIndexOrThrow(ID));

                return new Pair<>(id, envelopeFromCursor(cursor));
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }

        public SignalServiceProtos.Envelope getNext() {
            try {
                if (cursor == null || !cursor.moveToNext())
                    return null;
                return envelopeFromCursor(cursor);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }

        public void close() {
            this.cursor.close();
        }
    }

    private static SignalServiceProtos.Envelope envelopeFromCursor(Cursor cursor) throws IOException {
        if (cursor == null)
            return null;
        int type = cursor.getInt(cursor.getColumnIndexOrThrow(TYPE));
        String source = cursor.getString(cursor.getColumnIndexOrThrow(SOURCE));
        int deviceId = cursor.getInt(cursor.getColumnIndexOrThrow(DEVICE_ID));
        String legacyMessage = cursor.getString(cursor.getColumnIndexOrThrow(LEGACY_MSG));
        String content = cursor.getString(cursor.getColumnIndexOrThrow(CONTENT));
        long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(TIMESTAMP));
        int sourceRegId = cursor.getInt(cursor.getColumnIndexOrThrow(SOURCE_REG_ID));


        return SignalServiceProtos.Envelope.newBuilder()
                .setType(SignalServiceProtos.Envelope.Type.valueOf(type))
                .setSource(source)
                .setSourceDevice(deviceId)
                .setRelay("")
                .setTimestamp(timestamp)
                .setLegacyMessage(Util.isEmpty(legacyMessage) ? null : ByteString.copyFrom(Base64.decode(legacyMessage)))
                .setContent(Util.isEmpty(content) ? null : ByteString.copyFrom(Base64.decode(content)))
                .setSourceRegistration(sourceRegId)
                .build();

    }
}
