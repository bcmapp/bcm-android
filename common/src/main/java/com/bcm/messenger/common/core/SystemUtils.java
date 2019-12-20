package com.bcm.messenger.common.core;

import android.os.Build;
import android.text.TextUtils;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SystemUtils {
    private final static int STATIC_VERSION_CODE = 333;
    private final static String STATIC_VERSION_NAME = "1-1-2";

    public static void initAPPInfo(String versionName, int versionCode) {
        VERSION_CODE = versionCode;
        VERSION_NAME = versionName;
    }

    private static int VERSION_CODE = -1;
    private static String VERSION_NAME = "";

    private static String USE_LANGUAGE = "";

    public static void setUseLanguage(String language) {
        USE_LANGUAGE = language;
    }
    public static String getUseLanguage() {
        return USE_LANGUAGE;
    }

    public static String getSystemInfo() {
        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String time = dateFormat.format(date);
        StringBuilder sb = new StringBuilder();
        sb.append("_______  systemInfo  ").append(time).append(" ______________");
        sb.append("\nID                 :").append(Build.ID);
        sb.append("\nBRAND              :").append(Build.BRAND);
        sb.append("\nMODEL              :").append(Build.MODEL);
        sb.append("\nRELEASE            :").append(Build.VERSION.RELEASE);
        sb.append("\nSDK                :").append(Build.VERSION.SDK);

        sb.append("\n_______ OTHER _______");
        sb.append("\nBOARD              :").append(Build.BOARD);
        sb.append("\nPRODUCT            :").append(Build.PRODUCT);
        sb.append("\nDEVICE             :").append(Build.DEVICE);
        sb.append("\nFINGERPRINT        :").append(Build.FINGERPRINT);
        sb.append("\nHOST               :").append(Build.HOST);
        sb.append("\nTAGS               :").append(Build.TAGS);
        sb.append("\nTYPE               :").append(Build.TYPE);
        sb.append("\nTIME               :").append(Build.TIME);
        sb.append("\nINCREMENTAL        :").append(Build.VERSION.INCREMENTAL);

        sb.append("\n_______ CUPCAKE-3 _______");
        sb.append("\nDISPLAY            :").append(Build.DISPLAY);

        sb.append("\n_______ DONUT-4 _______");
        sb.append("\nSDK_INT            :").append(Build.VERSION.SDK_INT);
        sb.append("\nMANUFACTURER       :").append(Build.MANUFACTURER);
        sb.append("\nBOOTLOADER         :").append(Build.BOOTLOADER);
        sb.append("\nCPU_ABI            :").append(Build.CPU_ABI);
        sb.append("\nCPU_ABI2           :").append(Build.CPU_ABI2);
        sb.append("\nHARDWARE           :").append(Build.HARDWARE);
        sb.append("\nUNKNOWN            :").append(Build.UNKNOWN);
        sb.append("\nCODENAME           :").append(Build.VERSION.CODENAME);

        sb.append("\n_______ GINGERBREAD-9 _______");
        sb.append("\nSERIAL             :").append(Build.SERIAL);
        return sb.toString();
    }

    public static String getSimpleSystemInfo() {
        String info =  Build.BRAND +"_"+Build.MODEL;
        return info.replace(" ", "_");
    }

    public static String getSimpleSystemVersion() {
        return Build.VERSION.RELEASE;
    }

    public static int getVersionCode() {
        if (VERSION_CODE != -1)
            return VERSION_CODE;
        return STATIC_VERSION_CODE;
    }

    public static String getVersionName() {
        if (!TextUtils.isEmpty(VERSION_NAME))
            return VERSION_NAME;
        return STATIC_VERSION_NAME;
    }

}
