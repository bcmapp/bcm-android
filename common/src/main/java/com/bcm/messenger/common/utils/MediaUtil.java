package com.bcm.messenger.common.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.R;
import com.bcm.messenger.common.attachments.Attachment;
import com.bcm.messenger.common.crypto.MasterSecret;
import com.bcm.messenger.common.mms.AudioSlide;
import com.bcm.messenger.common.mms.DecryptableStreamUriLoader.DecryptableUri;
import com.bcm.messenger.common.mms.DocumentSlide;
import com.bcm.messenger.common.mms.GifSlide;
import com.bcm.messenger.common.mms.GlideApp;
import com.bcm.messenger.common.mms.ImageSlide;
import com.bcm.messenger.common.mms.MmsSlide;
import com.bcm.messenger.common.mms.PartAuthority;
import com.bcm.messenger.common.mms.Slide;
import com.bcm.messenger.common.mms.VideoSlide;
import com.bcm.messenger.common.providers.PersistentBlobProvider;
import com.bcm.messenger.utility.BitmapUtils;
import com.bumptech.glide.request.target.Target;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;

public class MediaUtil {

    private static final String TAG = MediaUtil.class.getSimpleName();
    public static final String IMAGE_JPEG = "image/jpeg";

    public static final String AUDIO_AAC = "audio/aac";
    public static final String AUDIO_UNSPECIFIED = "audio/*";
    public static final String VIDEO_UNSPECIFIED = "video/*";


    public static @Nullable
    ThumbnailData generateThumbnail(Context context, MasterSecret masterSecret, String contentType, Uri uri)
            throws BitmapDecodingException {
        long startMillis = System.currentTimeMillis();
        ThumbnailData data = null;

        if (isImageType(contentType)) {
            data = new ThumbnailData(generateImageThumbnail(context, masterSecret, uri));
        }

        if (data != null) {
            Log.w(TAG, String.format("generated thumbnail for part, %dx%d (%.3f:1) in %dms",
                    data.getBitmap().getWidth(), data.getBitmap().getHeight(),
                    data.getAspectRatio(), System.currentTimeMillis() - startMillis));
        }

        return data;
    }

    public static @Nullable
    ThumbnailData generateThumbnail(Context context, String contentType, Uri uri)
            throws BitmapDecodingException {
        long startMillis = System.currentTimeMillis();
        ThumbnailData data = null;

        if (isImageType(contentType)) {
            data = new ThumbnailData(generateImageThumbnail(context, uri));
        }

        if (data != null) {
            Log.w(TAG, String.format("generated thumbnail for part, %dx%d (%.3f:1) in %dms",
                    data.getBitmap().getWidth(), data.getBitmap().getHeight(),
                    data.getAspectRatio(), System.currentTimeMillis() - startMillis));
        }

        return data;
    }

    private static Bitmap generateImageThumbnail(Context context, MasterSecret masterSecret, Uri uri)
            throws BitmapDecodingException {
        try {
            int maxSize = context.getResources().getDimensionPixelSize(R.dimen.media_bubble_height);
            return GlideApp.with(context.getApplicationContext())
                    .asBitmap()
                    .load(new DecryptableUri(masterSecret, uri))
                    .fitCenter()
                    .into(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
//                    .centerCrop()
//                    .into(maxSize, maxSize)
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            Log.w(TAG, e);
            throw new BitmapDecodingException(e);
        }
    }

    private static Bitmap generateImageThumbnail(Context context, Uri uri)
            throws BitmapDecodingException {
        try {
            int maxSize = context.getResources().getDimensionPixelSize(R.dimen.media_bubble_height);
            return GlideApp.with(context.getApplicationContext())
                    .asBitmap()
                    .load(uri)
                    .fitCenter()
                    .into(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
//                    .centerCrop()
//                    .into(maxSize, maxSize)
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            Log.w(TAG, e);
            throw new BitmapDecodingException(e);
        }
    }

    public static Slide getSlideForAttachment(Context context, Attachment attachment) {
        Slide slide = null;
        if (isGif(attachment.getContentType())) {
            slide = new GifSlide(context, attachment);
        } else if (isImageType(attachment.getContentType())) {
            slide = new ImageSlide(context, attachment);
        } else if (isVideoType(attachment.getContentType())) {
            slide = new VideoSlide(context, attachment);
        } else if (isAudioType(attachment.getContentType())) {
            slide = new AudioSlide(context, attachment, 0);
        } else if (isMms(attachment.getContentType())) {
            slide = new MmsSlide(context, attachment);
        } else if (attachment.getContentType() != null) {
            slide = new DocumentSlide(context, attachment);
        }

        return slide;
    }

    public static @Nullable
    String getMimeType(Context context, Uri uri) {
        if (uri == null) {
            return null;
        }

        if (PersistentBlobProvider.isAuthority(context, uri)) {
            return PersistentBlobProvider.getMimeType(context, uri);
        }

        String type = context.getContentResolver().getType(uri);
        if (type == null) {
            final String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
        }

        String mimeType = getCorrectedMimeType(type);

        if(mimeType != null && mimeType.endsWith("android.package-archive")) {
            return "application/octet-stream";
        } else {
            return mimeType;
        }
    }

    public static @Nullable
    String getCorrectedMimeType(@Nullable String mimeType) {
        if (mimeType == null) {
            return null;
        }

        switch (mimeType) {
            case "image/jpg":
                return MimeTypeMap.getSingleton().hasMimeType(IMAGE_JPEG)
                        ? IMAGE_JPEG
                        : mimeType;
            default:
                return mimeType;
        }
    }

    public static long getMediaSize(Context context, MasterSecret masterSecret, Uri uri) throws IOException {
        InputStream in = PartAuthority.getAttachmentStream(context, masterSecret, uri);
        if (in == null) {
            throw new IOException("Couldn't obtain input stream.");
        }

        long size = 0;
        byte[] buffer = new byte[4096];
        int read;

        while ((read = in.read(buffer)) != -1) {
            size += read;
        }
        in.close();

        return size;
    }


    public static long getMediaDuration(Context context, MasterSecret masterSecret, Uri uri) throws IOException {
        InputStream in = PartAuthority.getAttachmentStream(context, masterSecret, uri);
        if (in == null) {
            throw new IOException("Couldn't obtain input stream.");
        }

        long size = 0;
        byte[] buffer = new byte[4096];
        int read;

        while ((read = in.read(buffer)) != -1) {
            size += read;
        }
        in.close();

        return size;
    }


    public static boolean isMms(String contentType) {
        return !TextUtils.isEmpty(contentType) && contentType.trim().equals("application/mms");
    }

    public static boolean isGif(Attachment attachment) {
        return isGif(attachment.getContentType());
    }

    public static boolean isImage(Attachment attachment) {
        return isImageType(attachment.getContentType());
    }

    public static boolean isAudio(Attachment attachment) {
        return isAudioType(attachment.getContentType());
    }

    public static boolean isVideo(Attachment attachment) {
        return isVideoType(attachment.getContentType());
    }

    public static boolean isVideo(String contentType) {
        return !TextUtils.isEmpty(contentType) && contentType.trim().startsWith("video/");
    }

    public static boolean isGif(String contentType) {
        return !TextUtils.isEmpty(contentType) && contentType.trim().equals("image/gif");
    }

    public static boolean isFile(Attachment attachment) {
        return !isGif(attachment) && !isImage(attachment) && !isAudio(attachment) && !isVideo(attachment);
    }

    public static boolean isTextType(String contentType) {
        return (null != contentType) && contentType.startsWith("text/");
    }

    public static boolean isImageType(String contentType) {
        return (null != contentType) && BcmFileUtils.INSTANCE.isImageType(contentType);
    }

    public static boolean isAudioType(String contentType) {
        return (null != contentType) && contentType.startsWith(AUDIO_AAC);
    }

    public static boolean isVideoType(String contentType) {
        return (null != contentType) && contentType.startsWith("video/");
    }

    public static boolean hasVideoThumbnail(Uri uri) {
        Log.w(TAG, "Checking: " + uri);

        if (uri == null) {
            return false;
        }

        if ("file".equals(uri.getScheme())) {
            File file = new File(uri.getPath());
            String name = file.getName();
            if (TextUtils.isEmpty(name)) {
                return false;
            }

            String[] stringArray = name.split("\\.");
            return isVideoType(MimeTypeMap.getSingleton().getMimeTypeFromExtension(stringArray[stringArray.length -1]));
        }

        if (!ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            return false;
        }

        if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
            return uri.getLastPathSegment().contains("video");
        }



        return false;
    }

    public static @Nullable
    Bitmap getVideoThumbnail(Context context, Uri uri) {
        if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
            long videoId = Long.parseLong(uri.getLastPathSegment().split(":")[1]);

            return MediaStore.Video.Thumbnails.getThumbnail(context.getContentResolver(),
                    videoId,
                    MediaStore.Images.Thumbnails.MINI_KIND,
                    null);
        }

        return null;
    }

    public static @Nullable
    String getDiscreteMimeType(@NonNull String mimeType) {
        final String[] sections = mimeType.split("/", 2);
        return sections.length > 1 ? sections[0] : null;
    }

    public static class ThumbnailData {
        Bitmap bitmap;
        float aspectRatio;

        public ThumbnailData(Bitmap bitmap) {
            this.bitmap = bitmap;
            this.aspectRatio = (float) bitmap.getWidth() / (float) bitmap.getHeight();
        }

        public Bitmap getBitmap() {
            return bitmap;
        }

        public float getAspectRatio() {
            return aspectRatio;
        }

        public InputStream toDataStream() {
            return new ByteArrayInputStream(BitmapUtils.INSTANCE.toByteArray(bitmap, 100));
        }
    }
}
