package com.bcm.messenger.common.mms;

import android.content.ContentUris;
import android.content.Context;
import android.content.UriMatcher;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.bcm.messenger.common.BuildConfig;
import com.bcm.messenger.common.database.repositories.Repository;
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager;

import com.bcm.messenger.common.attachments.AttachmentId;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.providers.PersistentBlobProvider;
import com.bcm.messenger.common.providers.PartProvider;
import com.bcm.messenger.common.providers.SingleUseBlobProvider;

import java.io.IOException;
import java.io.InputStream;

public class PartAuthority {

    private static final String PART_URI_STRING = "content://" + BuildConfig.BCM_APPLICATION_ID + "/part";
    private static final String THUMB_URI_STRING = "content://" + BuildConfig.BCM_APPLICATION_ID + "/thumb";
    private static final String GROUP_URI_STRING = "content://" + BuildConfig.BCM_APPLICATION_ID + "/group/origin";
    private static final String GROUP_THUMB_URI_STRING = "content://" + BuildConfig.BCM_APPLICATION_ID + "/group/thumb";
    private static final Uri PART_CONTENT_URI = Uri.parse(PART_URI_STRING);
    private static final Uri THUMB_CONTENT_URI = Uri.parse(THUMB_URI_STRING);
    private static final Uri GROUP_CONTENT_URI = Uri.parse(GROUP_URI_STRING);
    private static final Uri GROUP_THUMB_CONTENT_URI = Uri.parse(GROUP_THUMB_URI_STRING);

    private static final int PART_ROW = 1;
    private static final int THUMB_ROW = 2;
    private static final int PERSISTENT_ROW = 3;
    private static final int SINGLE_USE_ROW = 4;
    private static final int GROUP_ROW = 5;
    private static final int GROUP_THUMB_ROW = 6;

    private static final UriMatcher uriMatcher;

    static {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(BuildConfig.BCM_APPLICATION_ID, "part/*/#", PART_ROW);
        uriMatcher.addURI(BuildConfig.BCM_APPLICATION_ID, "thumb/*/#", THUMB_ROW);
        uriMatcher.addURI(BuildConfig.BCM_APPLICATION_ID, "group/origin/*/#", GROUP_ROW);
        uriMatcher.addURI(BuildConfig.BCM_APPLICATION_ID, "group/thumb/*/#", GROUP_THUMB_ROW);
        uriMatcher.addURI(PersistentBlobProvider.AUTHORITY, PersistentBlobProvider.EXPECTED_PATH_OLD, PERSISTENT_ROW);
        uriMatcher.addURI(PersistentBlobProvider.AUTHORITY, PersistentBlobProvider.EXPECTED_PATH_NEW, PERSISTENT_ROW);
        uriMatcher.addURI(SingleUseBlobProvider.AUTHORITY, SingleUseBlobProvider.PATH, SINGLE_USE_ROW);
    }


    //
    public static InputStream getAttachmentStream(@NonNull Context context, MasterSecret masterSecret, @NonNull Uri uri)
            throws IOException {
        int match = uriMatcher.match(uri);
        try {
            PartUriParser parser = new PartUriParser(uri);
            GroupUriParser groupParser = new GroupUriParser(uri);
            switch (match) {
                case PART_ROW:
                    return Repository.getAttachmentRepo().getAttachmentStream(masterSecret, parser.getPartId().getRowId(), parser.getPartId().getUniqueId(), 0);
                case THUMB_ROW:
                    return Repository.getAttachmentRepo().getThumbnailStream(masterSecret, parser.getPartId().getRowId(), parser.getPartId().getUniqueId());
                case PERSISTENT_ROW:
                    return PersistentBlobProvider.getInstance(context).getStream(masterSecret, ContentUris.parseId(uri));
                case SINGLE_USE_ROW:
                    return SingleUseBlobProvider.getInstance().getStream(ContentUris.parseId(uri));
                case GROUP_ROW:
                    return MessageDataManager.INSTANCE.getFileStream(masterSecret, groupParser.getGid(), groupParser.getIndexId(), 0);
                case GROUP_THUMB_ROW:
                    return MessageDataManager.INSTANCE.getThumbnailStream(masterSecret, groupParser.getGid(), groupParser.getIndexId());
                default:
                    return context.getContentResolver().openInputStream(uri);
            }
        } catch (SecurityException se) {
            throw new IOException(se);
        }
    }

    public static @NonNull Uri getAttachmentPublicUri(Uri uri) {
        PartUriParser partUri = new PartUriParser(uri);
        return PartProvider.getContentUri(partUri.getPartId());
    }

    public static @NonNull Uri getGroupPublicUri(Uri uri) {
        GroupUriParser uriParser = new GroupUriParser(uri);
        return PartProvider.getGroupUri(uriParser.getGid(), uriParser.getIndexId());
    }

    public static @NonNull Uri getAttachmentDataUri(AttachmentId attachmentId) {
        Uri uri = Uri.withAppendedPath(PART_CONTENT_URI, String.valueOf(attachmentId.getUniqueId()));
        return ContentUris.withAppendedId(uri, attachmentId.getRowId());
    }

    public static @NonNull Uri getAttachmentDataUri(long rowId, long uniqueId) {
        Uri uri = Uri.withAppendedPath(PART_CONTENT_URI, String.valueOf(uniqueId));
        return ContentUris.withAppendedId(uri, rowId);
    }

    public static @NonNull Uri getAttachmentThumbnailUri(AttachmentId attachmentId) {
        Uri uri = Uri.withAppendedPath(THUMB_CONTENT_URI, String.valueOf(attachmentId.getUniqueId()));
        return ContentUris.withAppendedId(uri, attachmentId.getRowId());
    }

    public static @NonNull Uri getAttachmentThumbnailUri(long rowId, long uniqueId) {
        Uri uri = Uri.withAppendedPath(THUMB_CONTENT_URI, String.valueOf(uniqueId));
        return ContentUris.withAppendedId(uri, rowId);
    }

    public static @NonNull Uri getGroupAttachmentUri(long gid, long rowId) {
        Uri uri = Uri.withAppendedPath(GROUP_CONTENT_URI, String.valueOf(gid));
        return ContentUris.withAppendedId(uri, rowId);
    }

    public static @NonNull Uri getGroupThumbnailUri(long gid, long rowId) {
        Uri uri = Uri.withAppendedPath(GROUP_THUMB_CONTENT_URI, String.valueOf(gid));
        return ContentUris.withAppendedId(uri, rowId);
    }

    public static boolean isGroupUri(Uri uri) {
        return uriMatcher.match(uri) == GROUP_ROW;
    }

    public static boolean isLocalUri(final @NonNull Uri uri) {
        int match = uriMatcher.match(uri);
        switch (match) {
            case PART_ROW:
            case THUMB_ROW:
            case GROUP_ROW:
            case GROUP_THUMB_ROW:
            case PERSISTENT_ROW:
            case SINGLE_USE_ROW:
                return true;
        }
        return false;
    }
}
