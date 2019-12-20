package com.bcm.messenger.common.ui.emoji.parsing;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.annotation.NonNull;

import com.bcm.messenger.utility.logger.ALog;
import com.bcm.messenger.common.ui.emoji.EmojiPageModel;
import com.bcm.messenger.common.mms.GlideApp;
import com.bcm.messenger.utility.concurrent.ListenableFutureTask;
import com.bcm.messenger.utility.Util;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class EmojiPageBitmap {

    private static final String TAG = EmojiPageBitmap.class.getName();

    private final Context context;
    private final EmojiPageModel model;
    private final float decodeScale;

    private SoftReference<Bitmap> bitmapReference;
    private ListenableFutureTask<Bitmap> task;

    public EmojiPageBitmap(@NonNull Context context, @NonNull EmojiPageModel model, float decodeScale) {
        this.context = context.getApplicationContext();
        this.model = model;
        this.decodeScale = decodeScale;
    }

    public ListenableFutureTask<Bitmap> get() {
        Util.assertMainThread();

        if (bitmapReference != null && bitmapReference.get() != null) {
            return new ListenableFutureTask<>(bitmapReference.get());
        } else if (task != null) {
            return task;
        } else {
            Callable<Bitmap> callable = () -> {
                try {
                    ALog.w(TAG, "loading page " + model.getSprite());
                    return loadPage();
                } catch (IOException ioe) {
                    ALog.e(TAG, ioe);
                }
                return null;
            };
            task = new ListenableFutureTask<>(callable);
            Observable.create(new ObservableOnSubscribe<Boolean>() {
                @Override
                public void subscribe(ObservableEmitter<Boolean> emitter) throws Exception {
                    task.run();
                    emitter.onNext(true);
                    emitter.onComplete();
                }

            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Consumer<Boolean>() {
                        @Override
                        public void accept(Boolean aBoolean) throws Exception {
                            task = null;
                        }
                    }, new Consumer<Throwable>() {
                        @Override
                        public void accept(Throwable throwable) throws Exception {
                            task = null;
                        }
                    });
        }
        return task;
    }

    private Bitmap loadPage() throws IOException {
        if (bitmapReference != null && bitmapReference.get() != null)
            return bitmapReference.get();

        try {
            Bitmap originalBitmap = GlideApp.with(context.getApplicationContext())
                    .asBitmap()
                    .load("file:///android_asset/" + model.getSprite())
                    .submit()
                    .get();

            Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, (int) (originalBitmap.getWidth() * decodeScale), (int) (originalBitmap.getHeight() * decodeScale), false);

            bitmapReference = new SoftReference<>(scaledBitmap);
            ALog.w(TAG, "onPageLoaded(" + model.getSprite() + ")");
            return scaledBitmap;
        } catch (InterruptedException e) {
            ALog.e(TAG, e);
            throw new IOException(e);
        } catch (ExecutionException e) {
            ALog.e(TAG, e);
            throw new IOException(e);
        }
    }

    @Override
    public String toString() {
        return model.getSprite();
    }
}
