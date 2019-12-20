package com.bcm.messenger.common.mms;

import android.content.Context;
import android.util.Log;

import com.bcm.messenger.common.bcmhttp.FileHttp;
import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.cache.DiskCache;
import com.bumptech.glide.load.engine.cache.DiskCacheAdapter;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;
import com.bcm.messenger.common.contacts.avatars.ContactPhoto;
import com.bcm.messenger.common.glide.ContactPhotoLoader;
import com.bcm.messenger.common.glide.OkHttpUrlLoader;
import com.bcm.messenger.common.mms.AttachmentStreamUriLoader.AttachmentModel;
import com.bcm.messenger.common.mms.DecryptableStreamUriLoader.DecryptableUri;
import java.io.InputStream;


@GlideModule
public class SignalGlideModule extends AppGlideModule {

  @Override
  public boolean isManifestParsingEnabled() {
    return false;
  }

  @Override
  public void applyOptions(Context context, GlideBuilder builder) {
    builder.setLogLevel(Log.ERROR);
  }

  @Override
  public void registerComponents(Context context, Glide glide, Registry registry) {
    registry.append(ContactPhoto.class, InputStream.class, new ContactPhotoLoader.Factory(context));
    registry.append(DecryptableUri.class, InputStream.class, new DecryptableStreamUriLoader.Factory(context));
    registry.append(AttachmentModel.class, InputStream.class, new AttachmentStreamUriLoader.Factory());
    registry.replace(GlideUrl.class, InputStream.class, FileHttp.INSTANCE.getOkHttpFactory());
  }

  public static class NoopDiskCacheFactory implements DiskCache.Factory {
    @Override
    public DiskCache build() {
      return new DiskCacheAdapter();
    }
  }
}
