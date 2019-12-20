package com.bcm.messenger.common.database.converters;


import androidx.room.TypeConverter;

import com.bcm.messenger.common.database.records.PrivacyProfile;
import com.bcm.messenger.utility.GsonUtils;

public class PrivacyProfileConverter {
    @TypeConverter
    public static final String storeToDatabase(PrivacyProfile privacyProfile) {
        if (privacyProfile == null) return "";
        try {
            return GsonUtils.INSTANCE.toJson(privacyProfile);
        } catch (Throwable tr) {
            tr.printStackTrace();
        }
        return "";
    }

    @TypeConverter
    public static final PrivacyProfile restoreToOrigin(String json) {
        if (json == null || json.isEmpty()) return new PrivacyProfile();
        try {
            return GsonUtils.INSTANCE.fromJson(json, PrivacyProfile.class);
        } catch (Throwable tr) {
            tr.printStackTrace();
        }
        return new PrivacyProfile();
    }
}
