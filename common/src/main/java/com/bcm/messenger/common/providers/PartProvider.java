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
package com.bcm.messenger.common.providers;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.MemoryFile;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bcm.messenger.common.BuildConfig;
import com.bcm.messenger.common.attachments.AttachmentId;
import com.bcm.messenger.common.core.AmeGroupMessage;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.database.records.AttachmentRecord;
import com.bcm.messenger.common.database.repositories.AttachmentRepo;
import com.bcm.messenger.common.database.repositories.Repository;
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager;
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail;
import com.bcm.messenger.common.mms.GroupUriParser;
import com.bcm.messenger.common.mms.HistoryUriParser;
import com.bcm.messenger.common.mms.PartUriParser;
import com.bcm.messenger.common.provider.AMELogin;
import com.bcm.messenger.utility.MemoryFileUtil;
import com.bcm.messenger.utility.Util;
import com.bcm.messenger.utility.logger.ALog;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class PartProvider extends ContentProvider {

    private static final String TAG = PartProvider.class.getSimpleName();

    private static final String CONTENT_URI_STRING = "content://" + BuildConfig.BCM_APPLICATION_ID + ".partprovider/part";
    private static final String GROUP_URI_STRING = "content://" + BuildConfig.BCM_APPLICATION_ID + ".partprovider/group/origin";
    private static final String UNENCRYPTED_URI_STRING = "content://" + BuildConfig.BCM_APPLICATION_ID + ".partprovider/unencrypted/origin";
    private static final Uri CONTENT_URI = Uri.parse(CONTENT_URI_STRING);
    private static final Uri GROUP_URI = Uri.parse(GROUP_URI_STRING);
    private static final Uri UNENCRYPTED_URI = Uri.parse(UNENCRYPTED_URI_STRING);
    private static final int SINGLE_ROW = 1;
    private static final int GROUP_ROW = 2;
    private static final int UNENCRYPTED_ROW = 3;

    private static final UriMatcher uriMatcher;

    static {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(BuildConfig.BCM_APPLICATION_ID + ".partprovider", "part/*/#", SINGLE_ROW);
        uriMatcher.addURI(BuildConfig.BCM_APPLICATION_ID + ".partprovider", "group/origin/*/#", GROUP_ROW);
        uriMatcher.addURI(BuildConfig.BCM_APPLICATION_ID + ".partprovider", "unencrypted/origin/data/user/0/" + BuildConfig.BCM_APPLICATION_ID + "/files/*/*/*", UNENCRYPTED_ROW);
    }

    @Override
    public boolean onCreate() {
        Log.w(TAG, "onCreate()");
        return true;
    }

    public static Uri getContentUri(AttachmentId attachmentId) {
        Uri uri = Uri.withAppendedPath(CONTENT_URI, String.valueOf(attachmentId.getUniqueId()));
        return ContentUris.withAppendedId(uri, attachmentId.getRowId());
    }

    public static @NonNull
    Uri getGroupUri(long gid, long indexId) {
        Uri uri = Uri.withAppendedPath(GROUP_URI, String.valueOf(gid));
        return ContentUris.withAppendedId(uri, indexId);
    }

    public static @NonNull
    Uri getUnencryptedUri(@NonNull String path) {
        return Uri.withAppendedPath(UNENCRYPTED_URI, path.substring(1));
    }

    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        final MasterSecret masterSecret = AMELogin.INSTANCE.getMajorContext().getMasterSecret();
        Log.w(TAG, "openFile() called!");

        if (masterSecret == null) {
            Log.w(TAG, "masterSecret was null, abandoning.");
            return null;
        }

        switch (uriMatcher.match(uri)) {
            case SINGLE_ROW:
                Log.w(TAG, "Parting out a single row...");
                try {
                    final PartUriParser partUri = new PartUriParser(uri);
                    return getParcelStreamForAttachment(masterSecret, partUri.getPartId());
                } catch (IOException ioe) {
                    Log.w(TAG, ioe);
                    throw new FileNotFoundException("Error opening file");
                }
            case GROUP_ROW:
                try {
                    final GroupUriParser uriParser = new GroupUriParser(uri);
                    return getGroupStreamForAttachment(masterSecret, uriParser.getGid(), uriParser.getIndexId());
                } catch (IOException ioe) {
                    Log.w(TAG, ioe);
                    throw new FileNotFoundException("Error opening file");
                }
            case UNENCRYPTED_ROW:
                try {
                    final HistoryUriParser uriParser = new HistoryUriParser(uri);
                    return ParcelFileDescriptor.open(new File(uriParser.getPath()), ParcelFileDescriptor.MODE_READ_ONLY);
                } catch (IOException ioe) {
                    Log.w(TAG, ioe);
                    throw new FileNotFoundException("Error opening file");
                }
            default:
                throw new FileNotFoundException("Request for bad part.");
        }
    }

    @Override
    public int delete(@NonNull Uri arg0, String arg1, String[] arg2) {
        Log.w(TAG, "delete() called");
        return 0;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        Log.w(TAG, "getType() called: " + uri);

        switch (uriMatcher.match(uri)) {
            case SINGLE_ROW:
                PartUriParser partUriParser = new PartUriParser(uri);
                AttachmentRepo repo = Repository.getAttachmentRepo(AMELogin.INSTANCE.getMajorContext());
                if (repo == null) {
                    ALog.w(TAG, "AttachmentRepo is null!");
                    return null;
                }
                AttachmentRecord attachmentRecord = repo.getAttachment(partUriParser.getPartId().getRowId(), partUriParser.getPartId().getUniqueId());

                if (attachmentRecord != null) {
                    return attachmentRecord.getContentType();
                }
            case GROUP_ROW:
                GroupUriParser uriParser = new GroupUriParser(uri);
                AmeGroupMessageDetail messageDetail = MessageDataManager.INSTANCE.fetchOneMessageByGidAndIndexId(AMELogin.INSTANCE.getMajorContext(), uriParser.getGid(), uriParser.getIndexId());

                if (messageDetail == null || !messageDetail.getMessage().isFile()) return null;

                return ((AmeGroupMessage.FileContent) messageDetail.getMessage().getContent()).getMimeType();
            default:
                break;
        }

        return null;
    }

    @Override
    public Uri insert(@NonNull Uri arg0, ContentValues arg1) {
        Log.w(TAG, "insert() called");
        return null;
    }

    @Override
    public Cursor query(@NonNull Uri url, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Log.w(TAG, "query() called: " + url);

        if (projection == null || projection.length <= 0) return null;

        switch (uriMatcher.match(url)) {
            case SINGLE_ROW:
                PartUriParser partUri = new PartUriParser(url);
                AttachmentRepo repo = Repository.getAttachmentRepo(AMELogin.INSTANCE.getMajorContext());
                if (repo == null) {
                    ALog.w(TAG, "AttachmentRepo is null!");
                    return null;
                }
                AttachmentRecord attachmentRecord = repo.getAttachment(partUri.getPartId().getRowId(), partUri.getPartId().getUniqueId());

                if (attachmentRecord == null) return null;

                MatrixCursor matrixCursor = new MatrixCursor(projection, 1);
                Object[] resultRow = new Object[projection.length];

                for (int i = 0; i < projection.length; i++) {
                    if (OpenableColumns.DISPLAY_NAME.equals(projection[i])) {
                        resultRow[i] = attachmentRecord.getFileName();
                    }
                }

                matrixCursor.addRow(resultRow);
                return matrixCursor;
            case GROUP_ROW:
                GroupUriParser uriParser = new GroupUriParser(url);
                AmeGroupMessageDetail messageDetail = MessageDataManager.INSTANCE.fetchOneMessageByGidAndIndexId(AMELogin.INSTANCE.getMajorContext(), uriParser.getGid(), uriParser.getIndexId());

                if (messageDetail == null || !messageDetail.getMessage().isFile()) return null;

                MatrixCursor groupMatrixCursor = new MatrixCursor(projection, 1);
                Object[] groupResultRow = new Object[projection.length];

                for (int i = 0; i < projection.length; i++) {
                    if (OpenableColumns.DISPLAY_NAME.equals(projection[i])) {
                        groupResultRow[i] = ((AmeGroupMessage.FileContent) messageDetail.getMessage().getContent()).getFileName();
                    }
                }

                groupMatrixCursor.addRow(groupResultRow);
                return groupMatrixCursor;
            case UNENCRYPTED_ROW:
                MatrixCursor ueMatrixCursor = new MatrixCursor(projection, 1);
                Object[] ueResultRow = new Object[projection.length];

                for (int i = 0; i < projection.length; i++) {
                    if (OpenableColumns.DISPLAY_NAME.equals(projection[i])) {
                        ueResultRow[i] = url.getLastPathSegment();
                    }
                }

                ueMatrixCursor.addRow(ueResultRow);
                return ueMatrixCursor;
            default:
                break;
        }

        return null;
    }

    @Override
    public int update(@NonNull Uri arg0, ContentValues arg1, String arg2, String[] arg3) {
        Log.w(TAG, "update() called");
        return 0;
    }

    private ParcelFileDescriptor getParcelStreamForAttachment(MasterSecret masterSecret, AttachmentId attachmentId) throws IOException {
        AttachmentRepo repo = Repository.getAttachmentRepo(AMELogin.INSTANCE.getMajorContext());
        if (repo == null) {
            ALog.w(TAG, "AttachmentRepo is null!");
            return null;
        }

        InputStream stream = repo.getAttachmentStream(masterSecret, attachmentId.getRowId(), attachmentId.getUniqueId(), 0);
        if (stream == null) {
            throw new FileNotFoundException("Attachment file not found");
        }
        long plaintextLength = Util.getStreamLength(stream);
        MemoryFile memoryFile = new MemoryFile(attachmentId.toString(), Util.toIntExact(plaintextLength));

        InputStream in = repo.getAttachmentStream(masterSecret, attachmentId.getRowId(), attachmentId.getUniqueId(), 0);
        OutputStream out = memoryFile.getOutputStream();

        Util.copy(in, out);
        Util.close(out);
        Util.close(in);

        return MemoryFileUtil.getParcelFileDescriptor(memoryFile);
    }

    private ParcelFileDescriptor getGroupStreamForAttachment(MasterSecret masterSecret, long gid, long indexId) throws IOException {
        InputStream stream = MessageDataManager.INSTANCE.getFileStream(masterSecret.getAccountContext(), masterSecret, gid, indexId, 0);
        if (stream == null) {
            throw new FileNotFoundException("Attachment file not found");
        }
        long plaintextLength = Util.getStreamLength(stream);
        MemoryFile memoryFile = new MemoryFile("(gid: " + gid + ", index id: " + indexId + ")", Util.toIntExact(plaintextLength));

        InputStream in = MessageDataManager.INSTANCE.getFileStream(masterSecret.getAccountContext(), masterSecret, gid, indexId, 0);
        OutputStream out = memoryFile.getOutputStream();

        Util.copy(in, out);
        Util.close(out);
        Util.close(in);

        return MemoryFileUtil.getParcelFileDescriptor(memoryFile);
    }
}
