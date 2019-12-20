package com.bcm.messenger.common.video.exo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.TransferListener;

import com.bcm.messenger.common.crypto.MasterSecret;

public class AttachmentDataSourceFactory implements DataSource.Factory {

  private final MasterSecret masterSecret;
  private final DefaultDataSourceFactory             defaultDataSourceFactory;
  private final TransferListener<? super DataSource> listener;

  public AttachmentDataSourceFactory(@NonNull MasterSecret masterSecret,
                                     @NonNull DefaultDataSourceFactory defaultDataSourceFactory,
                                     @Nullable TransferListener<? super DataSource> listener)
  {
    this.masterSecret             = masterSecret;
    this.defaultDataSourceFactory = defaultDataSourceFactory;
    this.listener                 = listener;
  }

  @Override
  public AttachmentDataSource createDataSource() {
    return new AttachmentDataSource(defaultDataSourceFactory.createDataSource(),
                                    new PartDataSource(masterSecret, listener));
  }
}
