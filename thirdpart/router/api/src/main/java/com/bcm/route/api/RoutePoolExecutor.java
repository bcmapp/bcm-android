package com.bcm.route.api;

import android.util.Log;

import java.util.concurrent.*;

public class RoutePoolExecutor extends ThreadPoolExecutor {
    private static final String TAG = RoutePoolExecutor.class.getSimpleName();

    private static RoutePoolExecutor instance;

    public static RoutePoolExecutor getInstance() {
        if (instance == null) {
            synchronized (RoutePoolExecutor.class) {
                if (instance == null) {
                    instance = new RoutePoolExecutor(5, 5, 30, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>(32), Executors.defaultThreadFactory());
                }
            }
        }
        return instance;
    }

    private RoutePoolExecutor(int corePoolSize, int maxPoolSize, int keepAliveTime, TimeUnit unit, BlockingDeque<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maxPoolSize, keepAliveTime, unit, workQueue, threadFactory, new RejectedExecutionHandler() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                Log.e(TAG, "Task rejected!");
            }
        });
    }
}
