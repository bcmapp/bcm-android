package com.bcm.messenger.common.crypto;


import android.content.Context;
import androidx.annotation.NonNull;
import com.bcm.messenger.utility.Base64;
import com.bcm.messenger.common.preferences.TextSecurePreferences;
import com.bcm.messenger.utility.EncryptUtils;
import java.io.IOException;


public class ProfileKeyUtil {

    public static synchronized boolean hasProfileKey(@NonNull Context context) {
        return TextSecurePreferences.getProfileKey(context) != null;
    }

    public static synchronized @NonNull
    byte[] getProfileKey(@NonNull Context context) {
        try {
            String encodedProfileKey = TextSecurePreferences.getProfileKey(context);

            if (encodedProfileKey == null) {
                encodedProfileKey = EncryptUtils.getSecret(32);
                TextSecurePreferences.setProfileKey(context, encodedProfileKey);
            }

            return Base64.decode(encodedProfileKey);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    public static synchronized void setProfileKey(@NonNull Context context, @NonNull String profileKey) {
        TextSecurePreferences.setProfileKey(context, profileKey);
    }
}
