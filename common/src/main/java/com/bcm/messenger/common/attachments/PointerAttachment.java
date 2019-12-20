package com.bcm.messenger.common.attachments;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.crypto.MasterSecretUnion;
import com.bcm.messenger.common.crypto.MediaKey;
import com.bcm.messenger.common.database.AttachmentDatabase;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;

import java.util.LinkedList;
import java.util.List;

public class PointerAttachment extends Attachment {

  public PointerAttachment(@NonNull String contentType, int transferState, long size,
                           @Nullable String fileName,  @NonNull String location,
                           @NonNull String key, @NonNull String relay,
                           @Nullable byte[] digest, @Nullable String url,
                           boolean voiceNote)
  {
    super(contentType, transferState, size, fileName, location, key, relay, digest, null, url, voiceNote);
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


  public static List<Attachment> forPointers(@NonNull MasterSecretUnion masterSecret, Optional<List<SignalServiceAttachment>> pointers) {
    List<Attachment> results = new LinkedList<>();

    if (pointers.isPresent()) {
      for (SignalServiceAttachment pointer : pointers.get()) {
        if (pointer.isPointer()) {
          String encryptedKey = MediaKey.getEncrypted(masterSecret, pointer.asPointer().getKey());
          results.add(new PointerAttachment(pointer.getContentType(),
                                            AttachmentDatabase.TRANSFER_PROGRESS_PENDING,
                                            pointer.asPointer().getSize().or(0),
                                            pointer.asPointer().getFileName().orNull(),
                                            String.valueOf(pointer.asPointer().getId()),
                                            encryptedKey, pointer.asPointer().getRelay().orNull(),
                                            pointer.asPointer().getDigest().orNull(),
                                            pointer.asPointer().getUrl().orNull(),
                                            pointer.asPointer().getVoiceNote()));
        }
      }
    }

    return results;
  }
}
