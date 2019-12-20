package com.bcm.messenger.common.attachments;

import android.annotation.SuppressLint;
import android.net.Uri;
import androidx.annotation.Nullable;

import com.bcm.messenger.utility.Util;

import java.util.List;
import java.util.Locale;

import com.bcm.messenger.utility.logger.ALog;

public class AttachmentId {
    private final long rowId;
    private final long uniqueId;

    public AttachmentId(long rowId, long uniqueId) {
        this.rowId = rowId;
        this.uniqueId = uniqueId;
    }

    public long getRowId() {
        return rowId;
    }

    public long getUniqueId() {
        return uniqueId;
    }

    public String[] toStrings() {
        return new String[]{String.valueOf(rowId), String.valueOf(uniqueId)};
    }

    public String toString() {
        return "(row id: " + rowId + ", unique ID: " + uniqueId + ")";
    }

    public boolean isValid() {
        return rowId >= 0 && uniqueId >= 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AttachmentId attachmentId = (AttachmentId) o;

        if (rowId != attachmentId.rowId) return false;
        return uniqueId == attachmentId.uniqueId;
    }

    @Override
    public int hashCode() {
        return Util.hashCode(rowId, uniqueId);
    }


    static public Uri toUri(AttachmentId id) {
        @SuppressLint("DefaultLocale")
        String url = String.format(Locale.US, "chatsession://preview/%d/%d", id.rowId, id.uniqueId);
        return Uri.parse(url);
    }

    static public @Nullable AttachmentId fromUri(Uri uri) {
        try {
            if (null != uri && uri.toString().startsWith("chatsession://")) {
                List<String> segments = uri.getPathSegments();
                if (segments.size() == 2) {
                    AttachmentId id = new AttachmentId(Long.parseLong(segments.get(0)), Long.parseLong(segments.get(1)));
                    return id;
                }
            }
        } catch (Exception e) {
            ALog.e("AttachmentId", e);
        }
        return null;
    }
}
