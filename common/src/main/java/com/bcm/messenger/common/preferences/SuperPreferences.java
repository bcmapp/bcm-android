package com.bcm.messenger.common.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import androidx.annotation.NonNull;

import com.bcm.messenger.utility.GsonUtils;
import com.google.gson.reflect.TypeToken;

import java.util.LinkedList;
import java.util.List;


public class SuperPreferences {

    public static final String LOGIN_PROFILE_PREFERENCES = "login_profile_preferences";

    private static final String LOGIN_PROFILE_BACKUP_MODE_PREFERENCES = "login_profile_backup_mode_preferences";
    private static final String LOGIN_PROFILE_SET = "login_profile_set";
    private static final String LOGIN_PROFILE_SET_V2 = "login_profile_set_new";
    private static final String LANGUAGE_SELECT = "language_select";
    private static final String COUNTRY = "country";
    private static final String SETTINGS = "settings";
    private static final String ACCOUNT_TIP = "account_tip";
    private static final String ACCOUNT_REDDOT = "account_reddot";
    private static final String FLOATING_WINDOW_MUTE = "floating_window_mute";
    private static final String HEIGHT_KEYBOARD_PORTRAIT = "height_keyboard_portrait";
    private static final String HEIGHT_KEYBOARD_LANDSCAPE = "height_keyboard_landscape";
    private static final String TABLESS_INTRODUCTION_FLAG = "tabless_introduction_flag";
    private static final String TABLESS_ACCOUNT_SWITCH_FLAG = "tabless_account_switch_flag";

    public static final String RINGTONE_PREF = "pref_key_ringtone";
    private static final String VIBRATE_PREF = "pref_key_vibrate";
    private static final String NOTIFICATION_PREF = "pref_key_enable_notifications";
    public static final String SCREEN_SECURITY_PREF = "pref_screen_security";
    private static final String ALWAYS_RELAY_CALLS_PREF = "pref_turn_only";


    public static final String METRICS = "metrics";

    
    private static final String ACCOUNT_BACKUP_PREF = "pref_account_backup";


    public static SharedPreferences getSuperPreferences(Context context, String table) {
        return context.getSharedPreferences(table, Context.MODE_PRIVATE);
    }

    
    public static List<String> getAccountsProfileIntoSet(Context context) {
        String listString = context.getSharedPreferences(LOGIN_PROFILE_BACKUP_MODE_PREFERENCES, Context.MODE_PRIVATE).getString(LOGIN_PROFILE_SET_V2, "");
        if (!listString.isEmpty()) {
            return GsonUtils.INSTANCE.<LinkedList<String>>fromJson(listString, new TypeToken<LinkedList<String>>() {
            }.getType());
        }
        return new LinkedList<String>();
    }

   
    public static List<String> getAccountsProfileIntoSetV1(Context context) {
        String listString = context.getSharedPreferences(LOGIN_PROFILE_BACKUP_MODE_PREFERENCES, Context.MODE_PRIVATE).getString(LOGIN_PROFILE_SET, "");
        if (!listString.isEmpty()) {
            return GsonUtils.INSTANCE.<LinkedList<String>>fromJson(listString, new TypeToken<LinkedList<String>>() {
            }.getType());
        }
        return new LinkedList<String>();
    }

   
    public static void clearAccountsV1Profile(Context context) {
        context.getSharedPreferences(LOGIN_PROFILE_BACKUP_MODE_PREFERENCES, Context.MODE_PRIVATE).edit().remove(LOGIN_PROFILE_SET).apply();
    }

    
    public static void clearAccountsV2Profile(Context context) {
        context.getSharedPreferences(LOGIN_PROFILE_BACKUP_MODE_PREFERENCES, Context.MODE_PRIVATE).edit().remove(LOGIN_PROFILE_SET_V2).apply();

    }

    
    public static void setAccountBackupWithPublicKey(Context context, String publicKey, boolean done) {
        setBooleanPreference(context, ACCOUNT_BACKUP_PREF + publicKey, done);
    }

    public static boolean isAccountBackupWithPublicKey(Context context, String publicKey) {
        return getBooleanPreference(context, ACCOUNT_BACKUP_PREF + publicKey, false);
    }


    public static void setAccountBackupWithPublicKey2(Context context, String publicKey, String backup) {
        setAccountBackupWithPublicKey(context, publicKey, true);
        context.getSharedPreferences(LOGIN_PROFILE_PREFERENCES, 0).edit().putString(ACCOUNT_BACKUP_PREF + publicKey+"2", backup).apply();
    }
    
    public static String getAccountBackupWithPublicKey2(Context context, String publicKey) {
        return context.getSharedPreferences(LOGIN_PROFILE_PREFERENCES, 0).getString(ACCOUNT_BACKUP_PREF + publicKey+"2", "");
    }

    public static void setAccountBackupRedPoint(Context context, String publicKey, boolean done) {
        setBooleanPreference(context, ACCOUNT_BACKUP_PREF + "_red_point" + publicKey, done);
    }

    public static boolean isAccountBackupWithRedPoint(Context context, String publicKey) {
        return getBooleanPreference(context, ACCOUNT_BACKUP_PREF + "_red_point" + publicKey, false);
    }


    public static void setFlowtingWindowMuted(Context context, boolean isMuted) {
        setBooleanPreference(context, FLOATING_WINDOW_MUTE, isMuted);
    }

    public static void setBooleanPreference(Context context, String key, boolean value) {
        context.getSharedPreferences(LOGIN_PROFILE_PREFERENCES, 0).edit().putBoolean(key, value).apply();
    }

    public static boolean getBooleanPreference(Context context, String key, boolean defaultValue) {
        return context.getSharedPreferences(LOGIN_PROFILE_PREFERENCES, 0).getBoolean(key, defaultValue);
    }

    public static void setStringPreference(Context context, String key, String value) {
        context.getSharedPreferences(LOGIN_PROFILE_PREFERENCES, 0).edit().putString(key, value).apply();
    }

    public static void setStringPreferenceNow(Context context, String key, String value) {
        context.getSharedPreferences(LOGIN_PROFILE_PREFERENCES, 0).edit().putString(key, value).commit();
    }

    @NonNull
    public static String getStringPreference(Context context, String key) {
        return context.getSharedPreferences(LOGIN_PROFILE_PREFERENCES, 0).getString(key, "");
    }

    public static String getLanguageString(Context context, String defaultValue) {
        return context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).getString(LANGUAGE_SELECT, defaultValue);
    }

    public static void setLanguageString(Context context, String value) {
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit().putString(LANGUAGE_SELECT, value).apply();
    }

    public static String getCountryString(Context context, String defaultValue) {
        return context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).getString(COUNTRY, defaultValue);
    }

    public static void setCountryString(Context context, String value) {
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit().putString(COUNTRY, value).apply();
    }

    public static Boolean getAccountTipVisible(Context context) {
        return context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).getBoolean(ACCOUNT_TIP, true);
    }

    public static void setAccountTipVisible(Context context, Boolean value) {
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit().putBoolean(ACCOUNT_TIP, value).apply();
    }

    public static Boolean getAccountRedDot(Context context) {
        return context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).getBoolean(ACCOUNT_REDDOT, true);
    }

    public static void setAccountRedDot(Context context, Boolean value) {
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit().putBoolean(ACCOUNT_REDDOT, value).apply();
    }

    public static int getPortraitKeyboardHeight(Context context, int defaultHeight) {
        return context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).getInt(HEIGHT_KEYBOARD_PORTRAIT, defaultHeight);
    }

    public static void setPortraitKeyboardHeight(Context context, int height) {
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit().putInt(HEIGHT_KEYBOARD_PORTRAIT, height).apply();
    }

    public static int getLandscapeKeyboardHeight(Context context, int defaultHeight) {
        return context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).getInt(HEIGHT_KEYBOARD_LANDSCAPE, defaultHeight);
    }

    public static void setLandscapeKeyboardHeight(Context context, int height) {
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit().putInt(HEIGHT_KEYBOARD_LANDSCAPE, height).apply();
    }

    public static boolean getTablessIntroductionFlag(Context context) {
        return context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).getBoolean(TABLESS_ACCOUNT_SWITCH_FLAG, false);
    }

    public static void setTablessIntroductionFlag(Context context) {
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit().putBoolean(TABLESS_ACCOUNT_SWITCH_FLAG, true).apply();
    }

    public static void setScreenSecurityEnabled(Context context, boolean value) {
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit().putBoolean(SCREEN_SECURITY_PREF, value).apply();
    }

    public static boolean isScreenSecurityEnabled(Context context) {
        return context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).getBoolean(SCREEN_SECURITY_PREF, false);
    }

    public static boolean isNotificationsEnabled(Context context) {
        return context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).getBoolean(NOTIFICATION_PREF, true);
    }

    public static void setNotificationsEnabled(Context context, boolean value) {
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit().putBoolean(NOTIFICATION_PREF, value).apply();
    }

    public static String getNotificationRingtone(Context context) {
        return context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).getString(RINGTONE_PREF, Settings.System.DEFAULT_NOTIFICATION_URI.toString());
    }

    public static void setNotificationRingtone(Context context, String ringtoneUri) {
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit().putString(RINGTONE_PREF, ringtoneUri).apply();
    }

    public static boolean isNotificationVibrateEnabled(Context context) {
        return context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).getBoolean(VIBRATE_PREF, true);
    }

    public static void setNotificationVibrateEnabled(Context context, boolean value) {
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit().putBoolean(VIBRATE_PREF, value).apply();
    }

    public static boolean isTurnOnly(Context context) {
        return context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).getBoolean(ALWAYS_RELAY_CALLS_PREF, false);
    }

    public static void setTurnOnly(Context context, Boolean turnOnly) {
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit().putBoolean(ALWAYS_RELAY_CALLS_PREF, turnOnly).apply();
    }
}
