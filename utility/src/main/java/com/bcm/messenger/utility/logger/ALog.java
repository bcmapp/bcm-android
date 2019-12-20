package com.bcm.messenger.utility.logger;

import com.bcm.messenger.utility.BuildConfig;
import com.orhanobut.logger.Logger;

import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by bcm.social.01 on 2018/6/29.
 */

public class ALog {

    public static void d(String tag, String log) {
        Logger.log(Logger.DEBUG, tag, log, null);
    }

    public static void d(String tag, String log, Throwable e) {
        Logger.log(Logger.DEBUG, tag, log, e);
    }

    public static void i(String tag, String log) {
        Logger.log(Logger.INFO, tag, log, null);
    }

    public static void w(String tag, String log) {
        Logger.log(Logger.WARN, tag, log, null);
    }

    public static void e(String tag, String log){
        Logger.log(Logger.ERROR, tag, log, null);
    }

    public static void e(String tag, String log, @Nullable Throwable throwable){
        Logger.log(Logger.ERROR, tag, log, throwable);
    }

    public static void e(String tag, @Nullable Throwable throwable){
        Logger.log(Logger.ERROR, tag, "", throwable);
    }

    public static void logForSecret(String tag, String log) {
        logForSecret(tag, log, null);
    }

    public static void logForSecret(String tag, String log, @Nullable Throwable t) {
        if (BuildConfig.DEBUG) {
            Logger.log(t == null ? Logger.DEBUG : Logger.ERROR, tag, log, t);
        }else {
            Logger.log(t == null ? Logger.INFO : Logger.ERROR, tag, filterPrivate(log), t);
        }
    }

    private static String filterPrivate(String log) {
        return filterForUID(filterForIP(log));
    }

    private static String filterForIP(String log) {
        String REGEX_IP = "((25[0-5]|2[0-4]\\d|((1\\d{2})|([1-9]?\\d)))\\.){3}(25[0-5]|2[0-4]\\d|((1\\d{2})|([1-9]?\\d)))";
        Pattern p = Pattern.compile(REGEX_IP);
        Matcher matcher = p.matcher(log);
        StringBuffer sb = new StringBuffer();
        while(matcher.find()) {
            matcher.appendReplacement(sb, replaceIP(matcher.group()));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String filterForUID(String log) {

        String REGEX_UID = "[a-km-zA-HJ-NP-Z1-9]{32,}";
        Pattern p = Pattern.compile(REGEX_UID);
        Matcher matcher = p.matcher(log);
        StringBuffer sb = new StringBuffer();
        while(matcher.find()) {
            matcher.appendReplacement(sb, replaceUID(matcher.group()));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String replaceIP(String ipString) {
        try {
            int index = ipString.indexOf(".");
            String first = "***";
            if (index != -1) {
                first = ipString.substring(0, index);
            }
            index = ipString.lastIndexOf(".");
            String last = ".***";
            if (index != -1) {
                last = ipString.substring(index);
            }
            return first + ".***.***" + last;

        }catch (Exception ex) {
            ex.printStackTrace();
        }
        return "unknown";
    }

    private static String replaceUID(String uid) {
        return "*****";
    }
}
