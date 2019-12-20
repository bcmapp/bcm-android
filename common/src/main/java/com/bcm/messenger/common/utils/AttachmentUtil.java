package com.bcm.messenger.common.utils;


import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bcm.messenger.common.database.records.AttachmentRecord;
import com.bcm.messenger.common.preferences.TextSecurePreferences;
import com.bcm.messenger.utility.foreground.AppForeground;

import java.util.Collections;
import java.util.Set;

public class AttachmentUtil {

  private static final String TAG = AttachmentUtil.class.getSimpleName();

  public static boolean isAutoDownloadPermitted(@NonNull Context context, @Nullable AttachmentRecord attachment) {
    if (attachment == null) {
      Log.w(TAG, "attachment was null, returning vacuous true");
      return true;
    }

    Set<String> allowedTypes = getAllowedAutoDownloadTypes(context);
    String      contentType  = attachment.getContentType();

    if (attachment.isVoiceNote() || attachment.isAudio() && TextUtils.isEmpty(attachment.getName())) {
      return true;
    } else if (isNonDocumentType(contentType)) {
      return allowedTypes.contains(MediaUtil.getDiscreteMimeType(contentType));
    } else {
      return allowedTypes.contains("documents");
    }
  }

  private static boolean isNonDocumentType(String contentType) {
    return
        MediaUtil.isImageType(contentType) ||
        MediaUtil.isVideoType(contentType) ||
        MediaUtil.isAudioType(contentType);
  }

  private static @NonNull Set<String> getAllowedAutoDownloadTypes(@NonNull Context context) {
    if(!AppForeground.INSTANCE.foreground()) return Collections.emptySet();//在后台的时候，不开启自动下载
    else if (isConnectedWifi(context))    return TextSecurePreferences.getWifiMediaDownloadAllowed(context);
    else if (isConnectedRoaming(context)) return TextSecurePreferences.getRoamingMediaDownloadAllowed(context);
    else if (isConnectedMobile(context))  return TextSecurePreferences.getMobileMediaDownloadAllowed(context);
    else                                  return Collections.emptySet();
  }

  private static NetworkInfo getNetworkInfo(@NonNull Context context) {
    return ((ConnectivityManager)context.getSystemService(Activity.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
  }

  private static boolean isConnectedWifi(@NonNull Context context) {
    final NetworkInfo info = getNetworkInfo(context);
    return info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI;
  }

  private static boolean isConnectedMobile(@NonNull Context context) {
    final NetworkInfo info = getNetworkInfo(context);
    return info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_MOBILE;
  }

  private static boolean isConnectedRoaming(@NonNull Context context) {
    final NetworkInfo info = getNetworkInfo(context);
    return info != null && info.isConnected() && info.isRoaming() && info.getType() == ConnectivityManager.TYPE_MOBILE;
  }


}
