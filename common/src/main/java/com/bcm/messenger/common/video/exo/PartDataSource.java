package com.bcm.messenger.common.video.exo;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.database.records.AttachmentRecord;
import com.bcm.messenger.common.database.repositories.AttachmentRepo;
import com.bcm.messenger.common.database.repositories.Repository;
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager;
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail;
import com.bcm.messenger.common.mms.GroupUriParser;
import com.bcm.messenger.common.mms.PartAuthority;
import com.bcm.messenger.common.mms.PartUriParser;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class PartDataSource implements DataSource {

  private final @NonNull  MasterSecret masterSecret;
  private final @Nullable TransferListener<? super PartDataSource> listener;

  private Uri         uri;
  private InputStream inputSteam;

  public PartDataSource(@NonNull MasterSecret masterSecret,
                        @Nullable TransferListener<? super PartDataSource> listener)
  {
    this.masterSecret = masterSecret;
    this.listener     = listener;
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    this.uri = dataSpec.uri;
    long dataSize;

    if (PartAuthority.isGroupUri(uri)) {
      GroupUriParser groupUriParser = new GroupUriParser(uri);

      AmeGroupMessageDetail messageDetail = MessageDataManager.INSTANCE.fetchOneMessageByGidAndIndexId(masterSecret.getAccountContext(), groupUriParser.getGid(), groupUriParser.getIndexId());

      if (messageDetail == null) throw new IOException("GroupMessage not found");

      this.inputSteam = MessageDataManager.INSTANCE.getFileStream(masterSecret.getAccountContext(), masterSecret, groupUriParser.getGid(), groupUriParser.getIndexId(), dataSpec.position);
      dataSize = messageDetail.getAttachmentSize();
    } else {
      AttachmentRepo     attachmentRepo     = Repository.getAttachmentRepo(masterSecret.getAccountContext());
      if (attachmentRepo == null) {
        throw new IOException("Attachment repo is null");
      }

      PartUriParser      partUri            = new PartUriParser(uri);
      AttachmentRecord   attachment         = attachmentRepo.getAttachment(partUri.getPartId().getRowId(), partUri.getPartId().getUniqueId());

      if (attachment == null) throw new IOException("Attachment not found");

      try {
        this.inputSteam = attachmentRepo.getAttachmentStream(masterSecret, partUri.getPartId().getRowId(), partUri.getPartId().getUniqueId(), dataSpec.position);
      } catch (AssertionError e) {
        throw new IOException(e);
      }
      dataSize = attachment.getDataSize();
    }

    if (inputSteam == null) throw new IOException("InputStream not found");

    if (listener != null) {
      listener.onTransferStart(this, dataSpec);
    }

    if (dataSize - dataSpec.position <= 0) throw new EOFException("No more data");

    return dataSize - dataSpec.position;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws IOException {
    int read = inputSteam.read(buffer, offset, readLength);

    if (read > 0 && listener != null) {
      listener.onBytesTransferred(this, read);
    }

    return read;
  }

  @Override
  public Uri getUri() {
    return uri;
  }

  @Override
  public void close() throws IOException {
    inputSteam.close();
  }
}
