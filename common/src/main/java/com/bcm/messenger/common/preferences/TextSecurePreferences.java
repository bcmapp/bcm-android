package com.bcm.messenger.common.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import androidx.annotation.ArrayRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.AccountContext;
import com.bcm.messenger.common.R;
import com.bcm.messenger.common.utils.AppUtil;
import com.bcm.messenger.utility.AppContextHolder;
import com.bcm.messenger.utility.logger.ALog;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class TextSecurePreferences {

    public static final String TABLE_NAME = "text_secure_preferences";
    private static final String TAG = TextSecurePreferences.class.getSimpleName();

    public static final String THREAD_TRIM_LENGTH = "pref_trim_length";

    public static final String RINGTONE_PREF = "pref_key_ringtone";
    private static final String VIBRATE_PREF = "pref_key_vibrate";
    private static final String NOTIFICATION_PREF = "pref_key_enable_notifications";
    public static final String SCREEN_SECURITY_PREF = "pref_screen_security";
    private static final String ALWAYS_RELAY_CALLS_PREF = "pref_turn_only";

    private static final String THREAD_TRIM_ENABLED = "pref_trim_threads";

    private static final String GCM_PASSWORD_PREF = "pref_gcm_password";

    private static final String SIGNALING_KEY_PREF = "pref_signaling_key";

    private static final String SIGNED_PREKEY_ROTATION_TIME_PREF = "pref_signed_pre_key_rotation_time";


    private static final String LOCAL_REGISTRATION_ID_PREF = "pref_local_registration_id";
    private static final String SIGNED_PREKEY_REGISTERED_PREF = "pref_signed_prekey_registered";

    private static final String GCM_DISABLED_PREF = "pref_gcm_disabled";
    private static final String GCM_REGISTRATION_ID_PREF = "pref_gcm_registration_id";
    private static final String GCM_REGISTRATION_ID_VERSION_PREF = "pref_gcm_registration_id_version";
    private static final String GCM_REGISTRATION_ID_TIME_PREF = "pref_gcm_registration_id_last_set_time";
    private static final String SIGNED_PREKEY_FAILURE_COUNT_PREF = "pref_signed_prekey_failure_count";

    public static final String MEDIA_DOWNLOAD_MOBILE_PREF = "pref_media_download_mobile";
    public static final String MEDIA_DOWNLOAD_WIFI_PREF = "pref_media_download_wifi";
    public static final String MEDIA_DOWNLOAD_ROAMING_PREF = "pref_media_download_roaming";

    public static final String SYSTEM_EMOJI_PREF = "pref_system_emoji";
    private static final String MULTI_DEVICE_PROVISIONED_PREF = "pref_multi_device";
    private static final String PROFILE_KEY_PREF = "pref_profile_key";
    public static final String READ_RECEIPTS_PREF = "pref_read_receipts";

    public static final String PROFILE_SECRET_PREF = "pref_profile_secret";

    public static final String QR_DISCERN_NOTICE = "pref_qr_discern";

    public static final String SYS_NOTIFICATION_NOTICE = "pref_sys_notification";

    public static final String SYS_PUSH_MESSAGE = "pref_sys_message_";

    public static final String HAS_DATABASE_MIGRATED = "pref_has_database_migrated";

    private static final String MIGRATE_FAILED_COUNT = "pref_migrate_failed_count";

    public static final String CONTACT_SYNC_VERSION = "pref_contact_sync_version";

    public static void clear(AccountContext accountContext) {
        SharedPreferences prefs = getCurrentSharedPreferences(accountContext);
        if (null != prefs){
            prefs.edit().clear().apply();
        }
    }

    public static boolean isProfileSecret(AccountContext accountContext) {
        return getBooleanPreference(accountContext, PROFILE_SECRET_PREF, false);
    }

    public static boolean isReadReceiptsEnabled(AccountContext accountContext) {
        return getBooleanPreference(accountContext, READ_RECEIPTS_PREF, false);
    }

    public static void setReadReceiptsEnabled(AccountContext accountContext, boolean enabled) {
        setBooleanPreference(accountContext, READ_RECEIPTS_PREF, enabled);
    }

    public static @Nullable
    String getProfileKey(AccountContext accountContext) {
        return getStringPreference(accountContext, PROFILE_KEY_PREF, null);
    }

    public static void setProfileKey(AccountContext accountContext, String key) {
        setStringPreference(accountContext, PROFILE_KEY_PREF, key);
    }

    public static boolean isGcmDisabled(AccountContext accountContext) {
        return getBooleanPreference(accountContext, GCM_DISABLED_PREF, false);
    }


    public static void setMultiDevice(AccountContext accountContext, boolean value) {
        setBooleanPreference(accountContext, MULTI_DEVICE_PROVISIONED_PREF, value);
    }

    public static boolean isMultiDevice(AccountContext accountContext) {
        return getBooleanPreference(accountContext, MULTI_DEVICE_PROVISIONED_PREF, false);
    }


    public static int getSignedPreKeyFailureCount(AccountContext accountContext) {
        return getIntegerPreference(accountContext, SIGNED_PREKEY_FAILURE_COUNT_PREF, 0);
    }


    public static boolean isSignedPreKeyRegistered(AccountContext accountContext) {
        return getBooleanPreference(accountContext, SIGNED_PREKEY_REGISTERED_PREF, false);
    }

    @Nullable
    public static String getGcmRegistrationId(AccountContext accountContext) {
        int storedRegistrationIdVersion = getIntegerPreference(accountContext, GCM_REGISTRATION_ID_VERSION_PREF, 0);

        if (storedRegistrationIdVersion != AppUtil.INSTANCE.getVersionCode(AppContextHolder.APP_CONTEXT)) {
            return null;
        } else {
            return getStringPreference(accountContext, GCM_REGISTRATION_ID_PREF, null);
        }
    }

    public static long getGcmRegistrationIdLastSetTime(AccountContext accountContext) {
        return getLongPreference(accountContext, GCM_REGISTRATION_ID_TIME_PREF, 0);
    }


    public static int getLocalRegistrationId(AccountContext accountContext) {
        return getIntegerPreference(accountContext, LOCAL_REGISTRATION_ID_PREF, 0);
    }

    public static long getSignedPreKeyRotationTime(AccountContext accountContext) {
        return getLongPreference(accountContext, SIGNED_PREKEY_ROTATION_TIME_PREF, 0L);
    }

    public static String getPushServerPassword(AccountContext accountContext) {
        return getStringPreference(accountContext, GCM_PASSWORD_PREF, "");
    }

    public static String getSignalingKey(AccountContext accountContext) {
        return getStringPreference(accountContext, SIGNALING_KEY_PREF, "");
    }


    public static boolean isThreadLengthTrimmingEnabled(AccountContext accountContext) {
        return getBooleanPreference(accountContext, THREAD_TRIM_ENABLED, false);
    }

    public static int getThreadTrimLength(AccountContext accountContext) {
        return Integer.parseInt(getStringPreference(accountContext, THREAD_TRIM_LENGTH, "500"));
    }

    public static boolean isSystemEmojiPreferred(AccountContext accountContext) {
        return getBooleanPreference(accountContext, SYSTEM_EMOJI_PREF, false);
    }

    public static @NonNull
    Set<String> getMobileMediaDownloadAllowed(AccountContext accountContext) {
        return getMediaDownloadAllowed(accountContext, MEDIA_DOWNLOAD_MOBILE_PREF, R.array.pref_media_download_mobile_data_default);
    }

    public static @NonNull
    Set<String> getWifiMediaDownloadAllowed(AccountContext accountContext) {
        return getMediaDownloadAllowed(accountContext, MEDIA_DOWNLOAD_WIFI_PREF, R.array.pref_media_download_wifi_default);
    }

    public static @NonNull
    Set<String> getRoamingMediaDownloadAllowed(AccountContext accountContext) {
        return getMediaDownloadAllowed(accountContext, MEDIA_DOWNLOAD_ROAMING_PREF, R.array.pref_media_download_roaming_default);
    }

    private static @NonNull
    Set<String> getMediaDownloadAllowed(AccountContext accountContext, String key, @ArrayRes int defaultValuesRes) {
        return getStringSetPreference(accountContext,
                key,
                new HashSet<>(Arrays.asList(AppContextHolder.APP_CONTEXT.getResources().getStringArray(defaultValuesRes))));
    }

    public static void setHasDatabaseMigrated(AccountContext accountContext) {
        setBooleanPreference(accountContext, HAS_DATABASE_MIGRATED, true);
    }

    public static boolean isDatabaseMigrated(AccountContext accountContext) {
        return getBooleanPreference(accountContext, HAS_DATABASE_MIGRATED, false);
    }

    public static void setMigrateFailedCount(AccountContext accountContext, int count) {
        setIntegerPrefrence(accountContext, MIGRATE_FAILED_COUNT, count);
    }

    public static int getMigrateFailedCount(AccountContext accountContext) {
        return getIntegerPreference(accountContext, MIGRATE_FAILED_COUNT, 0);
    }

    public static boolean isScreenSecurityEnabled(AccountContext accountContext) {
        return getBooleanPreference(accountContext, SCREEN_SECURITY_PREF, false);
    }

    public static boolean isNotificationsEnabled(AccountContext accountContext) {
        return getBooleanPreference(accountContext, NOTIFICATION_PREF, true);
    }

    public static String getNotificationRingtone(AccountContext accountContext) {
        return getStringPreference(accountContext, RINGTONE_PREF, Settings.System.DEFAULT_NOTIFICATION_URI.toString());
    }

    public static boolean isNotificationVibrateEnabled(AccountContext accountContext) {
        return getBooleanPreference(accountContext, VIBRATE_PREF, true);
    }

    public static boolean isTurnOnly(AccountContext accountContext) {
        return getBooleanPreference(accountContext, ALWAYS_RELAY_CALLS_PREF, false);
    }

    public static void setBooleanPreference(AccountContext accountContext, String key, boolean value) {
        SharedPreferences pref = getCurrentSharedPreferences(accountContext);
        if (null != pref){
            pref.edit().putBoolean(key, value).apply();
        }
    }

    public static boolean getBooleanPreference(AccountContext accountContext, String key, boolean defaultValue) {
        SharedPreferences pref = getCurrentSharedPreferences(accountContext);
        if (null != pref) {
            return pref.getBoolean(key, defaultValue);
        }
        return defaultValue;
    }

    public static void setStringPreference(AccountContext accountContext, String key, String value) {
        SharedPreferences pref = getCurrentSharedPreferences(accountContext);
        if(null != pref){
            pref.edit().putString(key, value).apply();
        }
    }

    public static String getStringPreference(AccountContext accountContext, String key, String defaultValue) {
        SharedPreferences pref = getCurrentSharedPreferences(accountContext);
        if (null != pref) {
            return pref.getString(key, defaultValue);
        }
        return  defaultValue;
    }

    public static int getIntegerPreference(AccountContext accountContext, String key, int defaultValue) {
        SharedPreferences pref = getCurrentSharedPreferences(accountContext);
        if (null != pref) {
            return pref.getInt(key, defaultValue);
        }
        return  defaultValue;
    }

    public static void setIntegerPrefrence(AccountContext accountContext, String key, int value) {
        SharedPreferences pref = getCurrentSharedPreferences(accountContext);
        if (null != pref) {
            pref.edit().putInt(key, value).apply();
        }
    }

    public static long getLongPreference(AccountContext accountContext, String key, long defaultValue) {
        SharedPreferences pref = getCurrentSharedPreferences(accountContext);
        if (null != pref) {
            return pref.getLong(key, defaultValue);
        }
        return  defaultValue;
    }

    public static void setLongPreference(AccountContext accountContext, String key, long value) {
        SharedPreferences pref = getCurrentSharedPreferences(accountContext);
        if (null != pref) {
            pref.edit().putLong(key, value).apply();
        }
    }

    private static Set<String> getStringSetPreference(AccountContext accountContext, String key, Set<String> defaultValues) {
        final SharedPreferences pref =  getCurrentSharedPreferences(accountContext);
        if (null != pref && pref.contains(key)) {
            return pref.getStringSet(key, Collections.<String>emptySet());
        } else {
            return defaultValues;
        }
    }

    private static SharedPreferences getCurrentSharedPreferences(AccountContext accountContext) {
        Context context = AppContextHolder.APP_CONTEXT;

        return context.getSharedPreferences(TABLE_NAME + accountContext.getUid(), Context.MODE_PRIVATE);
    }

    public static void setStringSetPreference(AccountContext accountContext, String key, Set<String> values) {
        final SharedPreferences prefs = getCurrentSharedPreferences(accountContext);
        if (null != prefs){
            prefs.edit().putStringSet(key, values).apply();
        }
        else {
            ALog.e(TAG, "performance is null");
        }
    }

    public static @Nullable Set<String> getStringSetPreference(AccountContext accountContext, String key) {
        final SharedPreferences prefs = getCurrentSharedPreferences(accountContext);
        if (null != prefs && prefs.contains(key)) {
            return prefs.getStringSet(key, Collections.<String>emptySet());
        } else {
            ALog.e(TAG, "performance is null");
            return null;
        }
    }

    public static void delPreference(AccountContext accountContext, String key) {
        final SharedPreferences prefs = getCurrentSharedPreferences(accountContext);
        if (null != prefs) {
            prefs.edit().remove(key).apply();
        } else {
            ALog.e(TAG, "performance is null");
        }
    }
}
