package com.bcm.messenger.chats.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;

import androidx.core.app.ActivityCompat;

import com.bcm.messenger.utility.logger.ALog;

public class ScreenshotManager {

    private Context context;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private MediaContentObserver mediaContentObserver;
    private ScreenshotListener listener;
    private static final String EXTERNAL_CONTENT_URI_MATCHER =
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString();
    private long preScreenShotTime;


    private static final String[] MEDIA_PROJECTIONS = {
            MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED
    };

    private static final String[] KEYWORDS = {
            "screenshot", "screen_shot", "screen-shot", "screen shot",
            "screencapture", "screen_capture", "screen-capture", "screen capture",
            "screencap", "screen_cap", "screen-cap", "screen cap"
    };

    public ScreenshotManager(Context context) {
        this.context = context;
        mHandlerThread = new HandlerThread("Screenshot_Observer");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }


    public void setScreenshotListener(ScreenshotListener listener) {
        this.listener = listener;
        mediaContentObserver = new MediaContentObserver(context, mHandler);
        mediaContentObserver.setChangeListener(new MediaContentObserver.MediaContentChangeListener() {
            @Override
            public void onMediaContentChange(Uri uri) {
                verifyStoragePermissions(context);
                handleMediaContentChange(uri);
            }
        });
    }

    public void removeScreenshotListener() {
        if (mediaContentObserver != null) {
            this.mediaContentObserver.removeChangeListener();
            this.mediaContentObserver = null;
        }

        try {
            if (mHandlerThread != null && mHandlerThread.isAlive()) {
                mHandlerThread.quit();
            }
            this.mHandlerThread = null;
        } catch (Throwable e) {
            ALog.e("ScreenManager", e);
        }


        this.context = null;
        this.mHandler = null;
        this.listener = null;
    }

    private void handleMediaContentChange(Uri contentUri) {
        Cursor cursor = null;
        try {

            if (contentUri.toString().startsWith(EXTERNAL_CONTENT_URI_MATCHER)) {
                cursor = context.getContentResolver().query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        MEDIA_PROJECTIONS,
                        null,
                        null,
                        MediaStore.Images.ImageColumns.DATE_ADDED + " desc limit 1"
                );
            } else {
                cursor = context.getContentResolver().query(
                        MediaStore.Images.Media.INTERNAL_CONTENT_URI,
                        MEDIA_PROJECTIONS,
                        null,
                        null,
                        MediaStore.Images.ImageColumns.DATE_ADDED + " desc limit 1"
                );
            }

            if (cursor == null) {
                return;
            }

            if (!cursor.moveToFirst()) {
                cursor.close();
                return;
            }

            int dataIndex = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            int dateTakenIndex = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_ADDED);

            String data = cursor.getString(dataIndex);
            long dateTaken = cursor.getLong(dateTakenIndex);
            handleMediaRowData(data, dateTaken);

        } catch (Exception e) {
            e.printStackTrace();

        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
    }

    private void handleMediaRowData(String data, long dateTaken) {
        if (checkScreenShot(data, dateTaken)) {
            if (listener != null) {
                listener.onScreenshot();
            }
        } else {
            // DoNothing
        }
    }

    private boolean checkScreenShot(String data, long dateTaken) {
        if (System.currentTimeMillis() / 1000 - dateTaken > 2 || dateTaken - preScreenShotTime < 1 ) {
            return false;
        }
        preScreenShotTime = dateTaken;
        data = data.toLowerCase();
        for (String keyWork : KEYWORDS) {
            if (data.contains(keyWork)) {
                return true;
            }
        }
        return false;
    }


    public interface ScreenshotListener {
        void onScreenshot();
    }

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private void verifyStoragePermissions(Context context) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    (Activity) context,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }


}
