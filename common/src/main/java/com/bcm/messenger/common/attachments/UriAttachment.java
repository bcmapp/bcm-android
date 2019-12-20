package com.bcm.messenger.common.attachments;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class UriAttachment extends Attachment {

    private Uri dataUri;
  private final @Nullable Uri thumbnailUri;

  public UriAttachment(@NonNull Uri uri, @NonNull String contentType, int transferState, long size,
                       @Nullable String fileName, boolean voiceNote)
  {
    this(uri, uri, contentType, transferState, size, fileName, null, voiceNote);
  }

    public UriAttachment(@Nullable Uri dataUri, @Nullable Uri thumbnailUri,
                         @NonNull String contentType, int transferState, long size,
                         @Nullable String fileName, @Nullable String fastPreflightId,
                         boolean voiceNote)
  {
    super(contentType, transferState, size, 0, fileName, null, null, null, null, fastPreflightId, null, voiceNote);
    this.dataUri      = dataUri;
    this.thumbnailUri = thumbnailUri;
  }

  @Override
  public Uri getDataUri() {
    return dataUri;
  }

    @Override
    public void setDataUri(@NonNull Uri dataUri) {
        this.dataUri = dataUri;
    }

  @Override
  @Nullable
  public Uri getThumbnailUri() {
    return thumbnailUri;
  }

  @Override
  public boolean equals(Object other) {
    return other != null && other instanceof UriAttachment && ((UriAttachment) other).dataUri.equals(this.dataUri);
  }

  @Override
  public int hashCode() {
    if (dataUri != null) {
      return dataUri.hashCode();
    } else {
      return 0;
    }
  }
}
