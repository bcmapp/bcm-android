package com.bcm.messenger.common.database.converters;

import android.net.Uri;

import androidx.room.TypeConverter;

public class UriConverter {
    @TypeConverter
    public static final String storeToDatabase(Uri uri) {
        if (uri == null) return null;
        return uri.toString();
    }

    @TypeConverter
    public static final Uri restoreToOrigin(String str) {
        if (str == null) return null;
        return Uri.parse(str);
    }
}
