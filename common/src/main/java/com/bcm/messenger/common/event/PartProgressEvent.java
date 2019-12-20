package com.bcm.messenger.common.event;


import androidx.annotation.NonNull;

import com.bcm.messenger.common.database.records.AttachmentRecord;

public class PartProgressEvent {

  public final AttachmentRecord attachment;
  public final long       total;
  public final long       progress;

  public PartProgressEvent(@NonNull AttachmentRecord attachment, long total, long progress) {
    this.attachment = attachment;
    this.total      = total;
    this.progress   = progress;
  }
}
