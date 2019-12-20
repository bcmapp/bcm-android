package com.bcm.messenger.common.attachments;

import android.net.Uri;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.mms.PartAuthority;

public class DatabaseAttachment extends Attachment {

    private final AttachmentId attachmentId;
    private final long mmsId;
    private boolean hasData;
    private boolean hasThumbnail;

    public Uri realDataUri = null;
    public Uri realThumbnailUri = null;
    public float aspectRatio = 0f;

    public DatabaseAttachment(AttachmentId attachmentId, long mmsId,
                              boolean hasData, boolean hasThumbnail,
                              String contentType, int transferProgress, long size, long duration,
                              String fileName, String location, String key, String relay,
                              byte[] digest, String fastPreflightId, String url, boolean voiceNote) {
        super(contentType, transferProgress, size, duration, fileName, location, key, relay, digest, fastPreflightId, url, voiceNote);
        this.attachmentId = attachmentId;
        this.hasData = hasData;
        this.hasThumbnail = hasThumbnail;
        this.mmsId = mmsId;
    }

    public void setHasData(boolean hasData) {
        this.hasData = hasData;
    }

    public void setHasThumbnail(boolean hasThumbnail) {
        this.hasThumbnail = hasThumbnail;
    }

    @Override
    @Nullable
    public Uri getDataUri() {
        if (hasData) {
            return PartAuthority.getAttachmentDataUri(attachmentId);
        } else {
            return null;
        }
    }

    @Override
    public void setDataUri(Uri uri) {

    }

    @Override
    @Nullable
    public Uri getThumbnailUri() {
        if (hasThumbnail) {
            return PartAuthority.getAttachmentThumbnailUri(attachmentId);
        } else {
            return null;
        }
    }

    public AttachmentId getAttachmentId() {
        return attachmentId;
    }

    @Override
    public boolean equals(Object other) {
        return other != null &&
                other instanceof DatabaseAttachment &&
                ((DatabaseAttachment) other).attachmentId.equals(this.attachmentId);
    }

    @Override
    public int hashCode() {
        return attachmentId.hashCode();
    }

    public long getMmsId() {
        return mmsId;
    }

    public boolean hasData() {
        return hasData;
    }

    public boolean hasThumbnail() {
        return hasThumbnail;
    }
}
