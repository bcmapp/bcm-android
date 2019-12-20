package com.bcm.messenger.common.crypto.encrypt;


import com.bcm.messenger.utility.proguard.NotGuard;

public class EncryptKeySpec implements NotGuard {

    /**
     * version : 1
     * inviter_public_key :
     * invitee_public_key :
     * encrypt_key :
     */

    public static final int ENCRYPT_VERSION = 1;
    private int version;
    private String inviter_public_key;
    private String invitee_public_key;
    private String encrypt_key;

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getInviter_public_key() {
        return inviter_public_key;
    }

    public void setInviter_public_key(String inviter_public_key) {
        this.inviter_public_key = inviter_public_key;
    }

    public String getInvitee_public_key() {
        return invitee_public_key;
    }

    public void setInvitee_public_key(String invitee_public_key) {
        this.invitee_public_key = invitee_public_key;
    }

    public String getEncrypt_key() {
        return encrypt_key;
    }

    public void setEncrypt_key(String encrypt_key) {
        this.encrypt_key = encrypt_key;
    }
}
