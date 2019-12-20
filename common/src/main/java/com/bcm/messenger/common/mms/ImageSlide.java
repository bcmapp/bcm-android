/** 
 * Copyright (C) 2011 Whisper Systems
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.bcm.messenger.common.mms;

import android.content.Context;
import android.content.res.Resources.Theme;
import android.net.Uri;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.R;
import com.bcm.messenger.common.attachments.Attachment;
import com.bcm.messenger.common.attachments.UriAttachment;
import com.bcm.messenger.common.database.model.AttachmentDbModel;
import com.bcm.messenger.common.utils.MediaUtil;

import org.whispersystems.libsignal.util.guava.Optional;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class ImageSlide extends Slide {

  private static final String TAG = ImageSlide.class.getSimpleName();
    public static final int STARTED = 1;
    public static final int PENDING = 2;
    public static final int DONE = 3;

  public ImageSlide(@NonNull Context context, @NonNull Attachment attachment) {
    super(context, attachment);
  }

  public ImageSlide(Context context, Uri uri, long size) {
    super(context, constructAttachmentFromUri(context, uri, MediaUtil.IMAGE_JPEG, size, true, null, false));
  }

    public ImageSlide(Context context, Uri uri, long size, int status) {
        super(context, doneAttachmentFromUri(context, uri, MediaUtil.IMAGE_JPEG, size, true, null, false, status));
    }

    public ImageSlide(Context context, Uri uri, String mimeType, long size, int status) {
        super(context, doneAttachmentFromUri(context, uri, mimeType != null ? mimeType : MediaUtil.IMAGE_JPEG, size, true, null, false, status));
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
            String fastPreflightId = String.valueOf(SecureRandom.getInstance("SHA1PRNG").nextLong());
            UriAttachment attachment;
            if (status == DONE) {
                attachment = new UriAttachment(uri, hasThumbnail ? uri : null, resolvedType.or(defaultMime), AttachmentDbModel.TransferState.DONE.getState(), size, fileName, fastPreflightId, voiceNote);
            } else if (status == PENDING) {
                attachment = new UriAttachment(uri, hasThumbnail ? uri : null, resolvedType.or(defaultMime), AttachmentDbModel.TransferState.PENDING.getState(), size, fileName, fastPreflightId, voiceNote);
            } else {
                attachment = (UriAttachment) constructAttachmentFromUri(context, uri, resolvedType.or(defaultMime), size, true, fileName, voiceNote);
            }
            return attachment;
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

  @Override
  public @DrawableRes int getPlaceholderRes(Theme theme) {
    return R.drawable.common_image_place_img;
  }

  @Override
  public boolean hasImage() {
    return true;
  }

    @Override
    public boolean hasPlaceholder() {
        return true;
    }

    @NonNull
  @Override
  public String getContentDescription() {
    return context.getString(R.string.common_slide_image);
  }
}
