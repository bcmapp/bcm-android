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
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.attachments.AttachmentId;
import com.bcm.messenger.common.attachments.DatabaseAttachment;
import com.bcm.messenger.common.crypto.DecryptingPartInputStream;
import com.bcm.messenger.common.crypto.MasterCipher;
import com.bcm.messenger.common.crypto.MasterSecret;

import org.whispersystems.libsignal.InvalidMessageException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@Deprecated
public class AttachmentDatabase extends Database {

    private static final String TAG = AttachmentDatabase.class.getSimpleName();

    static final String TABLE_NAME = "part";
    static final String ROW_ID = "_id";
    static final String ATTACHMENT_ID_ALIAS = "attachment_id";
    static final String MMS_ID = "mid";
    static final String CONTENT_TYPE = "ct";
    static final String NAME = "name";
    static final String CONTENT_DISPOSITION = "cd";
    static final String CONTENT_LOCATION = "cl";
    static final String DATA = "_data";
    static final String TRANSFER_STATE = "pending_push";
    static final String SIZE = "data_size";
    static final String FILE_NAME = "file_name";
    static final String THUMBNAIL = "thumbnail";
    static final String THUMBNAIL_ASPECT_RATIO = "aspect_ratio";
    static final String UNIQUE_ID = "unique_id";
    static final String DIGEST = "digest";
    static final String VOICE_NOTE = "voice_note";
    static final String DURATION = "data_duration";
    static final String URL = "url"; // AWS S3 download url
    public static final String FAST_PREFLIGHT_ID = "fast_preflight_id";

    public static final int TRANSFER_PROGRESS_DONE = 0;
    public static final int TRANSFER_PROGRESS_STARTED = 1;
    public static final int TRANSFER_PROGRESS_PENDING = 2;
    public static final int TRANSFER_PROGRESS_FAILED = 3;

    private static final String PART_ID_WHERE = ROW_ID + " = ? AND " + UNIQUE_ID + " = ?";

    private static final String[] PROJECTION = new String[]{ROW_ID + " AS " + ATTACHMENT_ID_ALIAS,
            MMS_ID, CONTENT_TYPE, NAME, CONTENT_DISPOSITION,
            CONTENT_LOCATION, DATA, THUMBNAIL, TRANSFER_STATE,
            SIZE, FILE_NAME, THUMBNAIL, THUMBNAIL_ASPECT_RATIO,
            UNIQUE_ID, DIGEST, FAST_PREFLIGHT_ID, VOICE_NOTE, DURATION, URL};

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ROW_ID + " INTEGER PRIMARY KEY, " +
            MMS_ID + " INTEGER, " + "seq" + " INTEGER DEFAULT 0, " +
            CONTENT_TYPE + " TEXT, " + NAME + " TEXT, " + "chset" + " INTEGER, " +
            CONTENT_DISPOSITION + " TEXT, " + "fn" + " TEXT, " + "cid" + " TEXT, " +
            CONTENT_LOCATION + " TEXT, " + "ctt_s" + " INTEGER, " +
            "ctt_t" + " TEXT, " + "encrypted" + " INTEGER, " +
            TRANSFER_STATE + " INTEGER, " + DATA + " TEXT, " + SIZE + " INTEGER, " +
            FILE_NAME + " TEXT, " + THUMBNAIL + " TEXT, " + THUMBNAIL_ASPECT_RATIO + " REAL, " +
            UNIQUE_ID + " INTEGER NOT NULL, " + DIGEST + " BLOB, " + FAST_PREFLIGHT_ID + " TEXT, " +
            VOICE_NOTE + " INTEGER DEFAULT 0, " + DURATION + " INTEGER DEFAULT 0, " +
            URL + " TEXT);";

    public static final String DROP_TABLE = "DROP TABLE " + TABLE_NAME;

    public static final String[] CREATE_INDEXS = {
            "CREATE INDEX IF NOT EXISTS part_mms_id_index ON " + TABLE_NAME + " (" + MMS_ID + ");",
            "CREATE INDEX IF NOT EXISTS pending_push_index ON " + TABLE_NAME + " (" + TRANSFER_STATE + ");",
    };

    static final String ALTER_ADD_URL = "ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + URL + " TEXT;";

    public AttachmentDatabase(Context context, AccountContext accountContext, SQLiteOpenHelper databaseHelper) {
        super(context, accountContext, databaseHelper);
    }

    public @NonNull
    InputStream getAttachmentStream(MasterSecret masterSecret, AttachmentId attachmentId)
            throws IOException {
        InputStream dataStream = getDataStream(masterSecret, attachmentId, DATA);

        if (dataStream == null)
            throw new IOException("No stream for: " + attachmentId);
        else
            return dataStream;
    }

    @VisibleForTesting
    protected @Nullable
    InputStream getDataStream(MasterSecret masterSecret, AttachmentId attachmentId, String dataType) {
        File dataFile = getAttachmentDataFile(attachmentId, dataType);

        try {
            if (dataFile != null)
                return DecryptingPartInputStream.createFor(masterSecret, dataFile);
            else
                return null;
        } catch (IOException e) {
            Log.w(TAG, e);
            return null;
        }
    }

    private @Nullable
    File getAttachmentDataFile(@NonNull AttachmentId attachmentId,
                               @NonNull String dataType) {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = database.query(TABLE_NAME, new String[]{dataType}, PART_ID_WHERE, attachmentId.toStrings(),
                    null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                if (cursor.isNull(0)) {
                    return null;
                }

                return new File(cursor.getString(0));
            } else {
                return null;
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }

    }


    DatabaseAttachment getAttachment(MasterSecret masterSecret, Cursor cursor) {
        String encryptedFileName = cursor.getString(cursor.getColumnIndexOrThrow(FILE_NAME));
        String fileName = null;

        if (masterSecret != null && !TextUtils.isEmpty(encryptedFileName)) {
            try {
                fileName = new MasterCipher(masterSecret).decryptBody(encryptedFileName);
            } catch (InvalidMessageException e) {
                Log.w(TAG, e);
            }
        } else {
            fileName = encryptedFileName;
        }

        DatabaseAttachment attachment = new DatabaseAttachment(new AttachmentId(cursor.getLong(cursor.getColumnIndexOrThrow(ATTACHMENT_ID_ALIAS)),
                cursor.getLong(cursor.getColumnIndexOrThrow(UNIQUE_ID))),
                cursor.getLong(cursor.getColumnIndexOrThrow(MMS_ID)),
                !cursor.isNull(cursor.getColumnIndexOrThrow(DATA)),
                !cursor.isNull(cursor.getColumnIndexOrThrow(THUMBNAIL)),
                cursor.getString(cursor.getColumnIndexOrThrow(CONTENT_TYPE)),
                cursor.getInt(cursor.getColumnIndexOrThrow(TRANSFER_STATE)),
                cursor.getLong(cursor.getColumnIndexOrThrow(SIZE)),
                cursor.getLong(cursor.getColumnIndexOrThrow(DURATION)),
                fileName,
                cursor.getString(cursor.getColumnIndexOrThrow(CONTENT_LOCATION)),
                cursor.getString(cursor.getColumnIndexOrThrow(CONTENT_DISPOSITION)),
                cursor.getString(cursor.getColumnIndexOrThrow(NAME)),
                cursor.getBlob(cursor.getColumnIndexOrThrow(DIGEST)),
                cursor.getString(cursor.getColumnIndexOrThrow(FAST_PREFLIGHT_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(URL)),
                cursor.getInt(cursor.getColumnIndexOrThrow(VOICE_NOTE)) == 1);

        String dataUri = cursor.getString(cursor.getColumnIndexOrThrow(DATA));
        if (dataUri != null) {
            attachment.realDataUri = Uri.parse(dataUri);
        }
        String thumbnailUri = cursor.getString(cursor.getColumnIndexOrThrow(THUMBNAIL));
        if (thumbnailUri != null) {
            attachment.realThumbnailUri = Uri.parse(thumbnailUri);
        }
        attachment.aspectRatio = cursor.getFloat(cursor.getColumnIndexOrThrow(THUMBNAIL_ASPECT_RATIO));
        return attachment;
    }
}
