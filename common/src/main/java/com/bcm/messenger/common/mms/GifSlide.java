package com.bcm.messenger.common.mms;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.bcm.messenger.common.attachments.Attachment;
import com.bcm.messenger.common.utils.BcmFileUtils;

public class GifSlide extends ImageSlide {

  public GifSlide(Context context, Attachment attachment) {
    super(context, attachment);
  }

  public GifSlide(Context context, Uri uri, long size) {
      super(context, constructAttachmentFromUri(context, uri, BcmFileUtils.IMAGE_GIF, size, true, null, false));
  }

  @Override
  @Nullable
  public Uri getThumbnailUri() {
    return getUri();
  }
}
