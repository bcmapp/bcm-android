package com.bcm.messenger.common.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.ArrayRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.R;
import com.bcm.messenger.common.utils.AppUtil;
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

    private static final String THREAD_TRIM_ENABLED = "pref_trim_threads";

    public static final String REGISTERED_GCM_PREF = "pref_gcm_registered";
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
    private static final String ALWAYS_RELAY_CALLS_PREF = "pref_turn_only";
    private static final String PROFILE_KEY_PREF = "pref_profile_key";
    public static final String READ_RECEIPTS_PREF = "pref_read_receipts";

    public static final String PROFILE_SECRET_PREF = "pref_profile_secret";

    public static final String QR_DISCERN_NOTICE = "pref_qr_discern";

    public static final String SYS_NOTIFICATION_NOTICE = "pref_sys_notification";

    public static final String SYS_PUSH_MESSAGE = "pref_sys_message_";

    public static final String HAS_DATABASE_MIGRATED = "pref_has_database_migrated";

    private static final String MIGRATE_FAILED_COUNT = "pref_migrate_failed_count";

    public static final String ACCOUNT_DATA_VERSION = "pref_account_version";

    public static final String CONTACT_SYNC_VERSION = "pref_contact_sync_version";

    public static void clear(Context context) {
        SharedPreferences prefs = getCurrentSharedPreferences(context);
        if (null != prefs){
            prefs.edit().clear().apply();
        }
    }

    public static boolean isProfileSecret(Context context) {
        return getBooleanPreference(context, PROFILE_SECRET_PREF, false);
    }

    public static boolean isReadReceiptsEnabled(Context context) {
        return getBooleanPreference(context, READ_RECEIPTS_PREF, false);
    }

    public static void setReadReceiptsEnabled(Context context, boolean enabled) {
        setBooleanPreference(context, READ_RECEIPTS_PREF, enabled);
    }

    public static @Nullable
    String getProfileKey(Context context) {
        return getStringPreference(context, PROFILE_KEY_PREF, null);
    }

    public static void setProfileKey(Context context, String key) {
        setStringPreference(context, PROFILE_KEY_PREF, key);
    }

    public static boolean isTurnOnly(Context context) {
        return getBooleanPreference(context, ALWAYS_RELAY_CALLS_PREF, false);
    }

    public static void setTurnOnly(Context context, Boolean turnOnly) {
        setBooleanPreference(context, ALWAYS_RELAY_CALLS_PREF, turnOnly);
    }

    public static boolean isGcmDisabled(Context context) {
        return getBooleanPreference(context, GCM_DISABLED_PREF, false);
    }


    public static void setMultiDevice(Context context, boolean value) {
        setBooleanPreference(context, MULTI_DEVICE_PROVISIONED_PREF, value);
    }

    public static boolean isMultiDevice(Context context) {
        return getBooleanPreference(context, MULTI_DEVICE_PROVISIONED_PREF, false);
    }


    public static int getSignedPreKeyFailureCount(Context context) {
        return getIntegerPreference(context, SIGNED_PREKEY_FAILURE_COUNT_PREF, 0);
    }


    public static boolean isSignedPreKeyRegistered(Context context) {
        return getBooleanPreference(context, SIGNED_PREKEY_REGISTERED_PREF, false);
    }

    @Nullable
    public static String getGcmRegistrationId(Context context) {
        int storedRegistrationIdVersion = getIntegerPreference(context, GCM_REGISTRATION_ID_VERSION_PREF, 0);

        if (storedRegistrationIdVersion != AppUtil.INSTANCE.getVersionCode(context)) {
            return null;
        } else {
            return getStringPreference(context, GCM_REGISTRATION_ID_PREF, null);
        }
    }

    public static long getGcmRegistrationIdLastSetTime(Context context) {
        return getLongPreference(context, GCM_REGISTRATION_ID_TIME_PREF, 0);
    }


    public static int getLocalRegistrationId(Context context) {
        return getIntegerPreference(context, LOCAL_REGISTRATION_ID_PREF, 0);
    }

    public static long getSignedPreKeyRotationTime(Context context) {
        return getLongPreference(context, SIGNED_PREKEY_ROTATION_TIME_PREF, 0L);
    }

    @Nullable
    public static String getPushServerPassword(Context context) {
        return getStringPreference(context, GCM_PASSWORD_PREF, null);
    }

    @Nullable
    public static String getSignalingKey(Context context) {
        return getStringPreference(context, SIGNALING_KEY_PREF, null);
    }

    public static void setScreenSecurityEnabled(Context context, boolean value) {
        setBooleanPreference(context, SCREEN_SECURITY_PREF, value);
    }

    public static boolean isScreenSecurityEnabled(Context context) {
        return getBooleanPreference(context, SCREEN_SECURITY_PREF, false);
    }

    public static boolean isPushRegistered(Context context) {
        return getBooleanPreference(context, REGISTERED_GCM_PREF, false);
    }


    public static boolean isNotificationsEnabled(Context context) {
        return getBooleanPreference(context, NOTIFICATION_PREF, true);
    }

    public static void setNotificationsEnabled(Context context, boolean value) {
        setBooleanPreference(context, NOTIFICATION_PREF, value);
    }

    public static String getNotificationRingtone(Context context) {
        return getStringPreference(context, RINGTONE_PREF, Settings.System.DEFAULT_NOTIFICATION_URI.toString());
    }

    public static void setNotificationRingtone(Context context, String ringtoneUri) {
        setStringPreference(context, RINGTONE_PREF, ringtoneUri);
    }

    public static boolean isNotificationVibrateEnabled(Context context) {
        return getBooleanPreference(context, VIBRATE_PREF, true);
    }

    public static void setNotificationVibrateEnabled(Context context, boolean value) {
        setBooleanPreference(context, VIBRATE_PREF, value);
    }


    public static boolean isThreadLengthTrimmingEnabled(Context context) {
        return getBooleanPreference(context, THREAD_TRIM_ENABLED, false);
    }

    public static int getThreadTrimLength(Context context) {
        return Integer.parseInt(getStringPreference(context, THREAD_TRIM_LENGTH, "500"));
    }

    public static boolean isSystemEmojiPreferred(Context context) {
        return getBooleanPreference(context, SYSTEM_EMOJI_PREF, false);
    }

    public static @NonNull
    Set<String> getMobileMediaDownloadAllowed(Context context) {
        return getMediaDownloadAllowed(context, MEDIA_DOWNLOAD_MOBILE_PREF, R.array.pref_media_download_mobile_data_default);
    }

    public static @NonNull
    Set<String> getWifiMediaDownloadAllowed(Context context) {
        return getMediaDownloadAllowed(context, MEDIA_DOWNLOAD_WIFI_PREF, R.array.pref_media_download_wifi_default);
    }

    public static @NonNull
    Set<String> getRoamingMediaDownloadAllowed(Context context) {
        return getMediaDownloadAllowed(context, MEDIA_DOWNLOAD_ROAMING_PREF, R.array.pref_media_download_roaming_default);
    }

    private static @NonNull
    Set<String> getMediaDownloadAllowed(Context context, String key, @ArrayRes int defaultValuesRes) {
        return getStringSetPreference(context,
                key,
                new HashSet<>(Arrays.asList(context.getResources().getStringArray(defaultValuesRes))));
    }

    public static void setHasDatabaseMigrated(Context context) {
        setBooleanPreference(context, HAS_DATABASE_MIGRATED, true);
    }

    public static boolean isDatabaseMigrated(Context context) {
        return getBooleanPreference(context, HAS_DATABASE_MIGRATED, false);
    }

    public static void setMigrateFailedCount(Context context, int count) {
        setIntegerPrefrence(context, MIGRATE_FAILED_COUNT, count);
    }

    public static int getMigrateFailedCount(Context context) {
        return getIntegerPreference(context, MIGRATE_FAILED_COUNT, 0);
    }

    public static void setBooleanPreference(Context context, String key, boolean value) {
        SharedPreferences pref = getCurrentSharedPreferences(context);
        if (null != pref){
            pref.edit().putBoolean(key, value).apply();
        }

    }

    public static boolean getBooleanPreference(Context context, String key, boolean defaultValue) {
        SharedPreferences pref = getCurrentSharedPreferences(context);
        if (null != pref) {
            return pref.getBoolean(key, defaultValue);
        }
        return defaultValue;
    }

    public static void setStringPreference(Context context, String key, String value) {
        SharedPreferences pref = getCurrentSharedPreferences(context);
        if(null != pref){
            pref.edit().putString(key, value).apply();
        }
    }

    public static String getStringPreference(Context context, String key, String defaultValue) {
        SharedPreferences pref = getCurrentSharedPreferences(context);
        if (null != pref) {
            return pref.getString(key, defaultValue);
        }
        return  defaultValue;
    }

    public static int getIntegerPreference(Context context, String key, int defaultValue) {
        SharedPreferences pref = getCurrentSharedPreferences(context);
        if (null != pref) {
            return pref.getInt(key, defaultValue);
        }
        return  defaultValue;
    }

    public static void setIntegerPrefrence(Context context, String key, int value) {
        SharedPreferences pref = getCurrentSharedPreferences(context);
        if (null != pref) {
            pref.edit().putInt(key, value).apply();
        }
    }

    public static long getLongPreference(Context context, String key, long defaultValue) {
        SharedPreferences pref = getCurrentSharedPreferences(context);
        if (null != pref) {
            return pref.getLong(key, defaultValue);
        }
        return  defaultValue;
    }

    public static void setLongPreference(Context context, String key, long value) {
        SharedPreferences pref = getCurrentSharedPreferences(context);
        if (null != pref) {
            pref.edit().putLong(key, value).apply();
        }
    }

    private static Set<String> getStringSetPreference(Context context, String key, Set<String> defaultValues) {
        final SharedPreferences pref =  getCurrentSharedPreferences(context);
        if (null != pref && pref.contains(key)) {
            return pref.getStringSet(key, Collections.<String>emptySet());
        } else {
            return defaultValues;
        }
    }

    private static SharedPreferences getCurrentSharedPreferences(Context context) {
        String curLogin = SuperPreferences.getStringPreference(context, SuperPreferences.AME_CURRENT_LOGIN);
        if (!TextUtils.isEmpty(curLogin)) {
            return context.getSharedPreferences(TABLE_NAME + curLogin, Context.MODE_PRIVATE);
        }
        return null;
    }

    public static void setStringSetPreference(Context context, String key, Set<String> values) {
        final SharedPreferences prefs = getCurrentSharedPreferences(context);
        if (null != prefs){
            prefs.edit().putStringSet(key, values).apply();
        }
        else {
            ALog.e(TAG, "performance is null");
        }
    }

    public static @Nullable Set<String> getStringSetPreference(Context context, String key) {
        final SharedPreferences prefs = getCurrentSharedPreferences(context);
        if (null != prefs && prefs.contains(key)) {
            return prefs.getStringSet(key, Collections.<String>emptySet());
        } else {
            ALog.e(TAG, "performance is null");
            return null;
        }
    }

    public static void delPreference(Context context, String key) {
        final SharedPreferences prefs = getCurrentSharedPreferences(context);
        if (null != prefs) {
            prefs.edit().remove(key).apply();
        } else {
            ALog.e(TAG, "performance is null");
        }
    }
}
