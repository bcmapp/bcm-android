package com.bcm.messenger.chats.util;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;

public class MediaContentObserver {

    private ContentObserver mInternalObserver;
    private ContentObserver mExternalObserver;
    private MediaContentChangeListener listener;
    private Context context;

    public MediaContentObserver(Context context, Handler handler) {
        this.context = context;
        mInternalObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                if (listener != null) {
                    listener.onMediaContentChange(MediaStore.Images.Media.INTERNAL_CONTENT_URI);
                }
            }
        };

        mExternalObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                if (listener != null) {
                    listener.onMediaContentChange(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                }
            }
        };
    }

    public void setChangeListener(MediaContentChangeListener listener) {
        this.listener = listener;
        if (context != null) {
            context.getContentResolver().registerContentObserver(
                    MediaStore.Images.Media.INTERNAL_CONTENT_URI,
                    true,
                    mInternalObserver);

            context.getContentResolver().registerContentObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    true,
                    mExternalObserver);
        }
    }


    public void removeChangeListener() {
        this.listener = null;
        context.getContentResolver().unregisterContentObserver(mInternalObserver);
        context.getContentResolver().unregisterContentObserver(mExternalObserver);
        context = null;
    }


    interface MediaContentChangeListener {
        void onMediaContentChange(Uri uri);
    }
}
