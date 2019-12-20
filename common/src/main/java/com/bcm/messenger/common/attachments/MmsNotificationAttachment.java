package com.bcm.messenger.common.attachments;


import android.net.Uri;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.database.AttachmentDatabase;
import com.bcm.messenger.common.database.MmsDatabase;

public class MmsNotificationAttachment extends Attachment {

  public MmsNotificationAttachment(int status, long size) {
    super("application/mms", getTransferStateFromStatus(status), size, null, null, null, null, null, null, null, false);
  }

  @Nullable
  @Override
  public Uri getDataUri() {
    return null;
  }

    @Override
    public void setDataUri(Uri uri) {

    }

  @Nullable
  @Override
  public Uri getThumbnailUri() {
    return null;
  }

  private static int getTransferStateFromStatus(int status) {
    if (status == MmsDatabase.Status.DOWNLOAD_INITIALIZED ||
        status == MmsDatabase.Status.DOWNLOAD_NO_CONNECTIVITY)
    {
      return AttachmentDatabase.TRANSFER_PROGRESS_PENDING;
    } else if (status == MmsDatabase.Status.DOWNLOAD_CONNECTING) {
      return AttachmentDatabase.TRANSFER_PROGRESS_STARTED;
    } else {
      return AttachmentDatabase.TRANSFER_PROGRESS_FAILED;
    }
  }
}
