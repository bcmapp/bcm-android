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
        ALog.d(context.getTag() + tag, log, null);
    }

    public static void d(AccountContext context, String tag, String log, Throwable e) {
        ALog.d(context.getTag() + tag, log, e);
    }

    public static void i(AccountContext context, String tag, String log) {
        ALog.i(context.getTag() + tag, log);
    }

    public static void w(AccountContext context, String tag, String log) {
        ALog.w(context.getTag() + tag, log);
    }

    public static void e(AccountContext context, String tag, String log){
        ALog.e(context.getTag() + tag, log, null);
    }

    public static void e(AccountContext context, String tag, String log, @Nullable Throwable throwable){
        ALog.e(context.getTag() + tag, log, throwable);
    }

    public static void e(AccountContext context, String tag, @Nullable Throwable throwable){
        ALog.e(context.getTag() + tag, "", throwable);
    }
}
