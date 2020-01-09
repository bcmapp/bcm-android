package com.bcm.messenger.common.ui.subsampling;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.mms.PartAuthority;
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils;
import com.bcm.messenger.utility.logger.ALog;
import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder;
import com.davemorrissey.labs.subscaleview.decoder.SkiaImageRegionDecoder;

import java.io.InputStream;

public class AttachmentRegionDecoder implements ImageRegionDecoder {

  private static final String TAG = AttachmentRegionDecoder.class.getName();

  private SkiaImageRegionDecoder passthrough;

  private BitmapRegionDecoder bitmapRegionDecoder;
  private AccountContext accountContext;

  public AttachmentRegionDecoder(@NonNull AccountContext accountContext) {
    this.accountContext = accountContext;
  }

  @Override
  public Point init(Context context, Uri uri) throws Exception {
    ALog.w(TAG, "Init!");
    if (!PartAuthority.isLocalUri(uri)) {
      passthrough = new SkiaImageRegionDecoder();
      return passthrough.init(context, uri);
    }

    MasterSecret masterSecret = accountContext.getMasterSecret();

    if (masterSecret == null) {
      throw new IllegalStateException("No master secret available...");
    }

    InputStream inputStream = PartAuthority.getAttachmentStream(context, masterSecret, uri);

    this.bitmapRegionDecoder = BitmapRegionDecoder.newInstance(inputStream, false);
    inputStream.close();

    return new Point(bitmapRegionDecoder.getWidth(), bitmapRegionDecoder.getHeight());
  }

  @Override
  public Bitmap decodeRegion(Rect rect, int sampleSize) {
    ALog.w(TAG, "Decode region: " + rect);

    if (passthrough != null) {
      return passthrough.decodeRegion(rect, sampleSize);
    }

    synchronized(this) {
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inSampleSize      = sampleSize;
      options.inPreferredConfig = Bitmap.Config.RGB_565;

      Bitmap bitmap = bitmapRegionDecoder.decodeRegion(rect, options);

      if (bitmap == null) {
        throw new RuntimeException("Skia image decoder returned null bitmap - image format may not be supported");
      }

      return bitmap;
    }
  }

  public boolean isReady() {
    ALog.w(TAG, "isReady");
    return (passthrough != null && passthrough.isReady()) ||
           (bitmapRegionDecoder != null && !bitmapRegionDecoder.isRecycled());
  }

  public void recycle() {
    if (passthrough != null) {
      passthrough.recycle();
      passthrough = null;
    } else {
      bitmapRegionDecoder.recycle();
    }
  }
}
