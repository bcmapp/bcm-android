package com.bcm.messenger.common.utils;

import org.whispersystems.signalservice.internal.util.Base64;
import org.whispersystems.signalservice.internal.util.Util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class BcmHash {
    private static final long FNV1_32_INIT = 0x811c9dc5;

    private static final long FNV1_PRIME_32 = 16777619;

    public static long hash(byte[] data) {
        long hash = FNV1_32_INIT;

        for (byte aData : data) {
            hash ^= (aData & 0xff);
            hash *= FNV1_PRIME_32;
        }
        return hash & 0x00000000ffffffffL;
    }


    public static String hashPhone(String phone, Boolean urlSafe) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA1");
            byte[] token = Util.trim(digest.digest(phone.getBytes()), 10);
            String encoded = Base64.encodeBytesWithoutPadding(token);

            if (urlSafe) {
                return encoded.replace('+', '-').replace('/', '_');
            } else {
                return encoded;
            }
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
