package com.bcm.messenger.common.mms;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.database.records.AttachmentRecord;
import com.bcm.messenger.common.mms.DecryptableStreamUriLoader.DecryptableUri;
import com.bcm.messenger.common.utils.BitmapDecodingException;
import com.bcm.messenger.common.utils.MediaUtil;
import com.bcm.messenger.utility.BitmapUtils;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;


public abstract class MediaConstraints {

    private static final String TAG = MediaConstraints.class.getSimpleName();

    private static final int MAX_COMPRESSION_QUALITY = 90;
    private static final int MIN_COMPRESSION_QUALITY = 45;
    private static final int MAX_COMPRESSION_ATTEMPTS = 5;
    private static final int MIN_COMPRESSION_QUALITY_DECREASE = 5;

    public static MediaConstraints getPushMediaConstraints() {
        return new PushMediaConstraints();
    }

    public abstract int getImageMaxWidth(Context context);

    public abstract int getImageMaxHeight(Context context);

    public abstract int getImageMaxSize(Context context);

    public abstract int getGifMaxSize(Context context);

    public abstract int getVideoMaxSize(Context context);

    public abstract int getAudioMaxSize(Context context);

    public abstract int getDocumentMaxSize(Context context);

    public boolean isSatisfied(@NonNull Context context, @NonNull MasterSecret masterSecret, @NonNull AttachmentRecord attachment) {
        try {
            return (MediaUtil.isGif(attachment.getContentType()) && attachment.getDataSize() <= getGifMaxSize(context) && isWithinBounds(context, masterSecret, attachment.getPartUri())) ||
                    attachment.isImage() && attachment.getDataSize() <= getImageMaxSize(context) && isWithinBounds(context, masterSecret, attachment.getPartUri()) ||
                    (attachment.isAudio() || attachment.isVoiceNote()) && attachment.getDataSize() <= getAudioMaxSize(context) ||
                    attachment.isVideo() && attachment.getDataSize() <= getVideoMaxSize(context) ||
                    attachment.isDocument() && attachment.getDataSize() <= getDocumentMaxSize(context);
        } catch (IOException ioe) {
            Log.w(TAG, "Failed to determine if media's constraints are satisfied.", ioe);
            return false;
        }
    }

    private boolean isWithinBounds(Context context, MasterSecret masterSecret, Uri uri) throws IOException {
        try {
            InputStream is = PartAuthority.getAttachmentStream(context, masterSecret, uri);
            Size size = BitmapUtils.INSTANCE.getImageDimensions(is);
            return size.getWidth() > 0 && size.getWidth() <= getImageMaxWidth(context) &&
                    size.getHeight() > 0 && size.getHeight() <= getImageMaxHeight(context);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public boolean canResize(@Nullable AttachmentRecord attachment) {
        return attachment != null && attachment.isImage() && !MediaUtil.isGif(attachment.getContentType());
    }

    public MediaStream getResizedMedia(@NonNull Context context,
                                       @NonNull MasterSecret masterSecret,
                                       @NonNull AttachmentRecord attachment)
            throws IOException {
        if (!canResize(attachment)) {
            throw new UnsupportedOperationException("Cannot resize this content type");
        }

        try {
            // XXX - This is loading everything into memory! We want the send path to be stream-like.
            return new MediaStream(new ByteArrayInputStream(createScaledBytes(context, new DecryptableUri(masterSecret, attachment.getPartUri()), this)),
                    MediaUtil.IMAGE_JPEG);
        } catch (BitmapDecodingException e) {
            throw new IOException(e);
        }
    }

    @WorkerThread
    private <T> byte[] createScaledBytes(Context context, T model, MediaConstraints constraints)
            throws BitmapDecodingException {
        try {
            int quality = MAX_COMPRESSION_QUALITY;
            int attempts = 0;
            byte[] bytes;

            Bitmap scaledBitmap = GlideApp.with(context.getApplicationContext())
                    .asBitmap()
                    .load(model)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .downsample(DownsampleStrategy.AT_MOST)
                    .submit(constraints.getImageMaxWidth(context),
                            constraints.getImageMaxWidth(context))
                    .get();

            if (scaledBitmap == null) {
                throw new BitmapDecodingException("Unable to decode image");
            }

            try {
                do {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
                    bytes = baos.toByteArray();

                    Log.w(TAG, "iteration with quality " + quality + " size " + (bytes.length / 1024) + "kb");
                    if (quality == MIN_COMPRESSION_QUALITY) break;

                    int nextQuality = (int) Math.floor(quality * Math.sqrt((double) constraints.getImageMaxSize(context) / bytes.length));
                    if (quality - nextQuality < MIN_COMPRESSION_QUALITY_DECREASE) {
                        nextQuality = quality - MIN_COMPRESSION_QUALITY_DECREASE;
                    }
                    quality = Math.max(nextQuality, MIN_COMPRESSION_QUALITY);
                }
                while (bytes.length > constraints.getImageMaxSize(context) && attempts++ < MAX_COMPRESSION_ATTEMPTS);
                if (bytes.length > constraints.getImageMaxSize(context)) {
                    throw new BitmapDecodingException("Unable to scale image below: " + bytes.length);
                }
                Log.w(TAG, "createScaledBytes(" + model.toString() + ") -> quality " + Math.min(quality, MAX_COMPRESSION_QUALITY) + ", " + attempts + " attempt(s)");
                return bytes;
            } finally {
                scaledBitmap.recycle();
            }

        } catch (InterruptedException | ExecutionException e) {
            throw new BitmapDecodingException(e);
        }
    }
}
