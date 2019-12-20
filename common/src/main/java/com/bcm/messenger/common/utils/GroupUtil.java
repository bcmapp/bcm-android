package com.bcm.messenger.common.utils;

import android.util.Log;

import androidx.annotation.NonNull;

import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.utility.ClassHelper;
import com.bcm.messenger.utility.HexUtil;
import com.orhanobut.logger.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;

public class GroupUtil {

    public static final String ENCODED_SIGNAL_GROUP_PREFIX = "__textsecure_group__!";
    public static final String ENCODED_MMS_GROUP_PREFIX = "__signal_mms_group__!";
    public static final String ENCODED_TT_GROUP_PREFIX = "__tt_mms_group__!";
    private static final String TAG = "GroupUtil";


    public static Address addressFromGid(long groupId) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(groupId);
        return Address.fromSerialized(encodeTTGroupId(buffer.array()));
    }

    public static Long gidFromAddress(Address address) {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(8);
            buffer.put(decodeTTGroupId(address.toGroupString()));
            buffer.flip();
            return buffer.getLong();
        } catch (AssertionError | IOException e) {
            Logger.e(e, "groupIdFromRecipient");
        }
        return 0L;
    }

    public static String getEncodedId(byte[] groupId, boolean mms) {
        return (mms ? ENCODED_MMS_GROUP_PREFIX : ENCODED_SIGNAL_GROUP_PREFIX) + HexUtil.toString(groupId);
    }

    private static String encodeTTGroupId(byte[] groupId) {
        return ENCODED_TT_GROUP_PREFIX + HexUtil.toString(groupId);
    }

    public static byte[] getDecodedId(String groupId) throws IOException {
        if (!isEncodedGroup(groupId)) {
            throw new IOException("Invalid encoding");
        }

        return HexUtil.fromString(groupId.split("!", 2)[1]);
    }

    private static byte[] decodeTTGroupId(String groupId) throws IOException {
        if (!isTTGroup(groupId)) {
            throw new IOException("Invalid encoding");
        }
        return HexUtil.fromString(groupId.split("!", 2)[1]);
    }

    public static boolean isEncodedGroup(@NonNull String groupId) {
        return groupId.startsWith(ENCODED_SIGNAL_GROUP_PREFIX) || groupId.startsWith(ENCODED_MMS_GROUP_PREFIX);
    }

    public static boolean isTTGroup(@NonNull String groupId) {
        return groupId.startsWith(ENCODED_TT_GROUP_PREFIX);
    }

    public static boolean isMmsGroup(@NonNull String groupId) {
        return groupId.startsWith(ENCODED_MMS_GROUP_PREFIX);
    }
}
