package com.bcm.messenger.common.attachments;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.deprecated.AttachmentDatabase;

public abstract class Attachment {

    @NonNull
    private final String contentType;
    private final int transferState;
    private final long size;
    private long duration;//

    @Nullable
    private final String fileName;

    @Nullable
    private String location;

    @Nullable
    private String key;

    @Nullable
    private final String relay;

    @Nullable
    private byte[] digest;

    @Nullable
    private final String fastPreflightId;

    @Nullable
    private final String url;

    private final boolean voiceNote;

    public Attachment(@NonNull String contentType, int transferState, long size, @Nullable String fileName,
                      @Nullable String location, @Nullable String key, @Nullable String relay,
                      @Nullable byte[] digest, @Nullable String fastPreflightId, @Nullable String url, boolean voiceNote) {
        this(contentType, transferState, size, 0, fileName, location, key, relay, digest, fastPreflightId, url, voiceNote);
    }

    public Attachment(@NonNull String contentType, int transferState, long size, long duration, @Nullable String fileName,
                      @Nullable String location, @Nullable String key, @Nullable String relay,
                      @Nullable byte[] digest, @Nullable String fastPreflightId, @Nullable String url, boolean voiceNote) {
        this.contentType = contentType;
        this.transferState = transferState;
        this.size = size;
        this.duration = duration;
        this.fileName = fileName;
        this.location = location;
        this.key = key;
        this.relay = relay;
        this.digest = digest;
        this.fastPreflightId = fastPreflightId;
        this.url = url;
        this.voiceNote = voiceNote;
    }

    @Nullable
    public abstract Uri getDataUri();

    public abstract void setDataUri(Uri uri);

    @Nullable
    public abstract Uri getThumbnailUri();

    public int getTransferState() {
        return transferState;
    }

    public boolean isInProgress() {
        return transferState != AttachmentDatabase.TRANSFER_PROGRESS_DONE &&
                transferState != AttachmentDatabase.TRANSFER_PROGRESS_FAILED;
    }

    public long getSize() {
        return size;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public long getDuration() { return duration; }

    @Nullable
    public String getFileName() {
        return fileName;
    }

    @NonNull
    public String getContentType() {
        return contentType;
    }

    @Nullable
    public String getLocation() {
        return location;
    }

    @Nullable
    public String getKey() {
        return key;
    }

    @Nullable
    public String getRelay() {
        return relay;
    }

    @Nullable
    public byte[] getDigest() {
        return digest;
    }

    @Nullable
    public String getFastPreflightId() {
        return fastPreflightId;
    }

    @Nullable
    public String getUrl() {
        return url;
    }

    public boolean isVoiceNote() {
        return voiceNote;
    }

    public void update(String key, byte[] digest, Long location){
        this.key = key;
        this.location = Long.toString(location);
        this.digest = digest;
    }
}
