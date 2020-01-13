package com.bcm.messenger.common.utils.log;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.utility.logger.ALog;
import com.orhanobut.logger.Logger;

import org.jetbrains.annotations.Nullable;

/**
 * account context log
 */
public class ACLog {
    public static void d(AccountContext context, String tag, String log) {
        ALog.d(tag, log, null);
    }

    public static void d(AccountContext context, String tag, String log, Throwable e) {
        ALog.d(tag, log, e);
    }

    public static void i(AccountContext context, String tag, String log) {
        ALog.i(tag, log);
    }

    public static void w(AccountContext context, String tag, String log) {
        ALog.w(tag, log);
    }

    public static void e(AccountContext context, String tag, String log){
        ALog.e(tag, log, null);
    }

    public static void e(AccountContext context, String tag, String log, @Nullable Throwable throwable){
        ALog.e(tag, log, throwable);
    }

    public static void e(AccountContext context, String tag, @Nullable Throwable throwable){
        ALog.e(tag, "", throwable);
    }
}
