package com.bcm.route.api;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * BCM Router API, for developer to invoke
 *
 * @author Kin
 */
public final class BcmRouter {
    private static final String TAG = BcmRouter.class.getSimpleName();

    private volatile static BcmRouter instance = null;
    private static boolean isInited = false;

    private BcmRouter() {}

    /**
     * Init Router.
     *
     * @param application Default context.
     */
    public static void init(@NonNull Application application) {
        if (!isInited) {
            Log.i(TAG, "BCM router start init.");
            BcmInnerRouter.init(application, null);
            isInited = true;
            Log.i(TAG, "BCM router init end");
        }
    }

    /**
     * Init Router.
     *
     * @param application Default context.
     * @param flavour Current build flavour, if exists.
     */
    public static void init(@NonNull Application application, @Nullable String flavour) {
        if (!isInited) {
            Log.i(TAG, "BCM router start init.");
            BcmInnerRouter.init(application, flavour);
            isInited = true;
            Log.i(TAG, "BCM router init end");
        }
    }

    /**
     * Get router instance.
     *
     * @return Router instance.
     */
    public static BcmRouter getInstance() {
        if (!isInited) {
            throw new RuntimeException("BcmRouter has not init!!");
        }
        if (instance == null) {
            synchronized (BcmRouter.class) {
                if (instance == null) {
                    instance = new BcmRouter();
                }
            }
        }
        return instance;
    }

    /**
     * Set router debug mode, MUST invoke before init.
     */
    public static synchronized void openDebug() {
        BcmInnerRouter.setDebuggable();
    }

    /**
     * Check if router is in debug mode.
     *
     * @return Return is debug mode.
     */
    public static boolean isDebuggable() {
        return BcmInnerRouter.isDebuggable();
    }

    /**
     * Stop router running.
     */
    public static synchronized void stop() {
        BcmInnerRouter.destroy();
        isInited = false;
    }

    /**
     * Build a BcmRouteIntent
     *
     * @param path Path want to route to.
     * @return Return a BcmRouteIntent.
     */
    public BcmRouterIntent get(String path) {
        return BcmInnerRouter.getInstance().get(path);
    }

    /**
     * Start navigation to destination or return a specific class instance.
     *
     * @param context Context use to navigate.
     * @param routerIntent BcmRouterIntent.
     * @param requestCode RequestCode for invoking startActivityForResult, Context is an Activity at this time.
     * @param <T> Class type want to be casted to return.
     * @return Return an Object or a Specific class instance when destination is a(n) Fragment/IRouteProvider/Service, or return null
     *          when destination is an Activity.
     */
    public <T> T navigation(Context context, BcmRouterIntent routerIntent, int requestCode) {
        try {
            return BcmInnerRouter.getInstance().navigation(context, routerIntent, requestCode);
        } catch (Throwable tr) {
            Log.e(TAG, "Navigation failed, message = " + tr.getMessage());
            return null;
        }
    }
}
