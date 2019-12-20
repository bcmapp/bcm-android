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
import com.bcm.messenger.common.utils.AppUtil;
import com.bcm.messenger.common.utils.MediaUtil;


public class AudioSlide extends Slide {
    public long duration = 0;

    public AudioSlide(Context context, Uri uri, long dataSize, long duration, boolean voiceNote) {
        this(context, constructAttachmentFromUri(context, uri, MediaUtil.AUDIO_UNSPECIFIED, dataSize, false, null, voiceNote), duration);
  }

    public AudioSlide(Context context, Uri uri, long dataSize, long duration, String contentType, boolean voiceNote) {
        this(context, new UriAttachment(uri, null, contentType, AttachmentDbModel.TransferState.STARTED.getState(), dataSize, null, null, voiceNote), duration);
  }

    public AudioSlide(Context context, Uri uri, long dataSize, long duration, boolean voiceNote, boolean done) {
        this(context, new UriAttachment(uri, null, MediaUtil.AUDIO_UNSPECIFIED, AttachmentDbModel.TransferState.DONE.getState(), dataSize, null, null, voiceNote), duration);

    }

    public AudioSlide(Context context, Attachment attachment, long duration) {
    super(context, attachment);
        if (duration == 0) {
            this.duration = asAttachment().getDuration();
        } else {
            this.duration = duration;
            attachment.setDuration(this.duration);
        }
  }

  @Override
  @Nullable
  public Uri getThumbnailUri() {
    return null;
  }

  @Override
  public boolean hasPlaceholder() {
    return true;
  }

  @Override
  public boolean hasImage() {
    return true;
  }

  @Override
  public boolean hasAudio() {
    return true;
  }

  @NonNull
  @Override
  public String getContentDescription() {
    return context.getString(R.string.common_slide_audio);
  }

  @Override
  public @DrawableRes int getPlaceholderRes(Theme theme) {
    return AppUtil.INSTANCE.getDrawableRes(theme, R.attr.conversation_icon_attach_audio);
  }
}
