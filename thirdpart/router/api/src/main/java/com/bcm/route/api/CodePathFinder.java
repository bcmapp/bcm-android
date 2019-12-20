package com.bcm.route.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

public class CodePathFinder {
    private static final String TAG = CodePathFinder.class.getSimpleName();

    private static final String ROUTE_PACKAGE = "com.bcm.route.path";
    private static final String PREF_NAME = "BCM_ROUTE_PREF";
    private static final String LAST_VERSION_NAME = "last_version_name";
    private static final String LAST_VERSION_CODE = "last_version_code";
    private static final String LAST_VERSION_FLAVOUR = "last_version_flavour";
    private static final String ROUTE_MAP = "route_map";

    public synchronized static void init(Context context, String flavour) throws RuntimeException {
        try {
            Set<String> routerMap;
            if (BcmInnerRouter.isDebuggable() || isNewVersion(context, flavour)) {
                // Debug mode or is new version, rebuild route map
                Log.i(TAG, "Debug enabled or is new version");
                routerMap = ClassUtils.getFileNameByPackageName(context, ROUTE_PACKAGE);
                if (!routerMap.isEmpty()) {
                    context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putStringSet(ROUTE_MAP, routerMap).apply();
                } else {
                    Log.i(TAG, "Route map is empty!");
                }
            } else {
                // Load route map from cache
                Log.i(TAG, "Old version");
                routerMap = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getStringSet(ROUTE_MAP, new HashSet<String>());
            }

            for (String className : routerMap) {
                if (className.startsWith(ROUTE_PACKAGE + ".Route$$Module$$")) {
                    try {
                        ((IRoutePaths) Class.forName(className).getConstructor().newInstance()).loadInto(RouteMap.getMap());
                    } catch (Throwable tr) {
                        Log.e(TAG, "Class " + className + " not found");
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Init route map error, reason = " + e.getMessage());
        }
    }

    private static boolean isNewVersion(Context context, String flavour) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_CONFIGURATIONS);
            if (packageInfo != null) {
                String versionName = packageInfo.versionName;
                int versionCode = packageInfo.versionCode;
                String newFlavour = flavour == null ? "" : flavour;

                SharedPreferences preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                // VersionName or VersionCode is not equals to the old one.
                if (preferences.getString(LAST_VERSION_NAME, "").equals(versionName)
                        && preferences.getInt(LAST_VERSION_CODE, -1) == versionCode
                        && preferences.getString(LAST_VERSION_FLAVOUR, "").equals(newFlavour)) {
                    return false;
                } else {
                    preferences.edit().putString(LAST_VERSION_NAME, versionName).putInt(LAST_VERSION_CODE, versionCode).putString(LAST_VERSION_FLAVOUR, newFlavour).apply();
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Get package info error!", e);
        }
        return true;
    }
}