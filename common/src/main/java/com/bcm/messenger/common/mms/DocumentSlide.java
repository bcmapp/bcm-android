package com.bcm.messenger.common.mms;


import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.attachments.Attachment;
import com.bcm.messenger.common.attachments.UriAttachment;
import com.bcm.messenger.common.database.model.AttachmentDbModel;
import com.bcm.messenger.common.utils.MediaUtil;

import org.whispersystems.libsignal.util.guava.Optional;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class DocumentSlide extends Slide {
    public final static int STATED = 1;
    public final static int PENDING = 2;
    public final static int DONE = 0;

  public DocumentSlide(@NonNull Context context, @NonNull Attachment attachment) {
    super(context, attachment);
  }

    public DocumentSlide(@NonNull Context context, Uri uri,
                         @NonNull String contentType, long size,
                         @Nullable String fileName)
  {
    super(context, constructAttachmentFromUri(context, uri, contentType, size, true, fileName, false));
  }

    public DocumentSlide(@NonNull Context context, Uri uri,
                         @NonNull String contentType, long size,
                         @Nullable String fileName, int status) {
        super(context, doneAttachmentFromUri(context, uri, contentType, size, true, fileName, false, status));
    }

    protected static Attachment doneAttachmentFromUri(@NonNull Context context,
                                                      Uri uri,
                                                      @NonNull String defaultMime,
                                                      long size,
                                                      boolean hasThumbnail,
                                                      @Nullable String fileName,
                                                      boolean voiceNote,
                                                      int status) {
        try {
            Optional<String> resolvedType = Optional.fromNullable(MediaUtil.getMimeType(context, uri));
            if (!resolvedType.isPresent() && fileName != null && !fileName.isEmpty()) {
                int index = fileName.lastIndexOf(".");
                if (index >= 0) {
                    String suffix = fileName.substring(++index);
                    resolvedType = Optional.fromNullable(MimeTypeMap.getSingleton().getMimeTypeFromExtension(suffix));
                }
            }
            String fastPreflightId = String.valueOf(SecureRandom.getInstance("SHA1PRNG").nextLong());
            UriAttachment attachment;
            if (status == STATED) {
                attachment = new UriAttachment(uri, hasThumbnail ? uri : null, resolvedType.or(defaultMime), AttachmentDbModel.TransferState.STARTED.getState(), size, fileName, fastPreflightId, voiceNote);
            } else if (status == PENDING) {
                attachment = new UriAttachment(uri, hasThumbnail ? uri : null, resolvedType.or(defaultMime), AttachmentDbModel.TransferState.PENDING.getState(), size, fileName, fastPreflightId, voiceNote);
            } else {
                attachment = new UriAttachment(uri, hasThumbnail ? uri : null, resolvedType.or(defaultMime), AttachmentDbModel.TransferState.DONE.getState(), size, fileName, fastPreflightId, voiceNote);
            }
            return attachment;
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

  @Override
  public boolean hasDocument() {
    return true;
  }

}
