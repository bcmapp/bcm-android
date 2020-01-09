package com.bcm.messenger.common.ui.subsampling;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.mms.PartAuthority;
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils;
import com.davemorrissey.labs.subscaleview.decoder.ImageDecoder;
import com.davemorrissey.labs.subscaleview.decoder.SkiaImageDecoder;

import java.io.InputStream;

public class AttachmentBitmapDecoder implements ImageDecoder {
  private AccountContext accountContext;

  public AttachmentBitmapDecoder(@NonNull AccountContext accountContext) {
    this.accountContext = accountContext;
  }

  @Override
  public Bitmap decode(Context context, Uri uri) throws Exception {
    if (!PartAuthority.isLocalUri(uri)) {
      return new SkiaImageDecoder().decode(context, uri);
    }

    MasterSecret masterSecret = accountContext.getMasterSecret();

    if (masterSecret == null) {
      throw new IllegalStateException("Can't decode without secret");
    }

    InputStream inputStream = PartAuthority.getAttachmentStream(context, masterSecret, uri);

    try {
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inPreferredConfig = Bitmap.Config.ARGB_8888;

      Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);

      if (bitmap == null) {
        throw new RuntimeException("Skia image region decoder returned null bitmap - image format may not be supported");
      }

      return bitmap;
    } finally {
      if (inputStream != null) inputStream.close();
    }
  }
}
