package com.bcm.messenger.utility;

import android.annotation.SuppressLint;
import android.app.Application;

import org.jetbrains.annotations.NotNull;

/**
 * bcm.social.01 2018/9/4.
 */

public class AppContextHolder {

    @SuppressLint("StaticFieldLeak")
    public static Application APP_CONTEXT;
    public static void init(@NotNull Application application) {
        APP_CONTEXT = application;
    }

}
