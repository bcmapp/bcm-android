package com.bcm.messenger.common.core.corebean;

import com.google.gson.annotations.SerializedName;

import com.bcm.messenger.utility.proguard.NotGuard;

public class EncryptMessageBean implements NotGuard {

    public static final int ENCRYPT_VERSION = 1;

    public static final int ENCRYPT_CHAT_LEVEL = 0;
    public static final int ENCRYPT_CHANNEL_LEVEL = 1;


    /**
     * version : 1
     * header : {"hash_data":"","encryption_level":0}
     * body :
     */

    private int version;
    @SerializedName("key_version")
    private long keyVersion;
    private HeaderBean header;
    private String body;

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public HeaderBean getHeader() {
        return header;
    }

    public void setHeader(HeaderBean header) {
        this.header = header;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public long getKeyVersion() {
        return keyVersion;
    }

    public void setKeyVersion(long keyVersion) {
        this.keyVersion = keyVersion;
    }

    public static class HeaderBean implements NotGuard{
        /**
         * hash_data :
         * encryption_level : 0
         */

        private String hash_data;
        private int encryption_level;

        public String getHash_data() {
            return hash_data;
        }

        public void setHash_data(String hash_data) {
            this.hash_data = hash_data;
        }

        public int getEncryption_level() {
            return encryption_level;
        }

        public void setEncryption_level(int encryption_level) {
            this.encryption_level = encryption_level;
        }
    }
}
