package com.bcm.messenger.utility.bcmhttp.utils.progress;

import java.util.Map;
import java.util.WeakHashMap;

public class ProgressManager {

    private final Map<Long, ProgressListener> mRequestListener = new WeakHashMap<>();
    private final Map<Long, ProgressListener> mResponseListener = new WeakHashMap<>();

    private static volatile ProgressManager instance;

    public static ProgressManager getInstance() {
        if (instance == null) {
            synchronized (ProgressManager.class) {
                if (instance == null) {
                    instance = new ProgressManager();
                }
            }
        }
        return instance;
    }

    public void registerRequestListener(long id, ProgressListener listener) {
        mRequestListener.put(id, listener);
    }

    public void registerResponseListener(long id, ProgressListener listener) {
        mResponseListener.put(id, listener);
    }

    public void unregisterProgressListener(long id) {
        mRequestListener.remove(id);
        mResponseListener.remove(id);
    }

    public Map<Long, ProgressListener> getRequestListener() {
        return mRequestListener;
    }

    public Map<Long, ProgressListener> getResponseListener() {
        return mResponseListener;
    }


}
