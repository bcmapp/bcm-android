package com.bcm.route.api;

import android.app.Activity;
import android.app.Application;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.ActivityCompat;

import java.util.concurrent.ConcurrentHashMap;

/**
 * BCM Inner Router, the real router.
 *
 * @author Kin
 */
final class BcmInnerRouter {
    private static final String TAG = BcmInnerRouter.class.getSimpleName();

    private static BcmInnerRouter instance = null;

    private static boolean debuggable = false;
    private static Context context;

    private ConcurrentHashMap<String, IRouteProvider> providerMap = new ConcurrentHashMap<>();

    private BcmInnerRouter() {}

    static boolean isDebuggable() {
        return debuggable;
    }

    static synchronized void setDebuggable() {
        BcmInnerRouter.debuggable = true;
    }

    static Context getContext() {
        return context;
    }

    protected static BcmInnerRouter getInstance() {
        if (instance == null) {
            synchronized (BcmInnerRouter.class) {
                if (instance == null) {
                    instance = new BcmInnerRouter();
                }
            }
        }
        return instance;
    }

    protected static synchronized boolean init(Application application, String flavour) {
        context = application;
        CodePathFinder.init(application, flavour);
        return true;
    }

    protected static synchronized void destroy() {
        RouteMap.clear();
    }

    protected BcmRouterIntent get(String path) {
        return new BcmRouterIntent(path);
    }

    /**
     * Real navigation.
     */
    protected <T> T navigation(Context context, BcmRouterIntent routerIntent, int requestCode) {
        final Context innerContext = context == null ? BcmInnerRouter.context : context;

        switch (routerIntent.getType()) {
            case ACTIVITY:
                // Destination is an Activity
                try {
                    final Intent intent = new Intent(innerContext, routerIntent.getTarget());
                    intent.putExtras(routerIntent.getBundle());

                    if (routerIntent.getFlags() != 0) {
                        intent.addFlags(routerIntent.getFlags());
                    }

                    if (routerIntent.getUri() != null) {
                        intent.setData(routerIntent.getUri());
                    }

                    if (!(innerContext instanceof Activity)) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    }

                    if (Looper.myLooper() != Looper.getMainLooper()) {
                        // Navigate to activity must be invoked in main thread,
                        throw new RuntimeException("Activity must start in main thread!!");
                    }

                    if (requestCode > 0) {
                        if (!(innerContext instanceof Activity)) {
                            // If start intent context is not an Activity, cannot invoke startActivityForResult
                            throw new RuntimeException("Must pass an Activity if has request code!!");
                        }
                        ActivityCompat.startActivityForResult((Activity) innerContext, intent, requestCode, routerIntent.getOptionsCompat());
                    } else {
                        ActivityCompat.startActivity(innerContext, intent, routerIntent.getOptionsCompat());
                    }

                    if (routerIntent.getEnterAnimation() != 0 || routerIntent.getExitAnimation() != 0 && innerContext instanceof Activity) {
                        ((Activity) innerContext).overridePendingTransition(routerIntent.getEnterAnimation(), routerIntent.getExitAnimation());
                    }
                } catch (Throwable tr) {
                    Log.e(TAG, "Start activity error", tr);
                }

                break;
            case FRAGMENT:
            case SERVICE:
                // Other types, generate new instance
                try {
                    Class fragment = routerIntent.getTarget();

                    T instance = (T) fragment.getConstructor().newInstance();

                    // If destination is a Fragment, put extras
                    if (instance instanceof Fragment) {
                        ((Fragment) instance).setArguments(routerIntent.getBundle());
                    } else if (instance instanceof androidx.fragment.app.Fragment) {
                        ((androidx.fragment.app.Fragment) instance).setArguments(routerIntent.getBundle());
                    }

                    return instance;
                } catch (Throwable e) {
                    Log.e(TAG, "Init fragment instance error", e);
                }
                break;
            case PROVIDER:
                try {
                    Class providerClass = routerIntent.getTarget();

                    IRouteProvider provider = providerMap.get(routerIntent.getPath());
                    if (provider != null) {
                        return (T) provider;
                    }

                    T instance = (T) providerClass.getConstructor().newInstance();
                    providerMap.put(routerIntent.getPath(), (IRouteProvider) instance);
                    return instance;

                } catch (Throwable tr) {
                    Log.e(TAG, "Init provider error", tr);
                }

                break;
            default:
                break;
        }
        return null;
    }
}
