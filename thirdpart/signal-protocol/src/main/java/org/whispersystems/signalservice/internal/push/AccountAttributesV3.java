package org.whispersystems.signalservice.internal.push;
import org.whispersystems.libsignal.logging.Log;

public class AccountAttributesV3 {
    private String signalingKey;
    private int registrationId;
    private boolean voice;
    private boolean video;
    private boolean fetchesMessages;
    private String privateKey;
    private String pubKey;
    private String openId;


    public AccountAttributesV3(String openId, String privateKey, String pubKey) {
        this.voice = false;
        this.video = false;
        this.fetchesMessages = true;
        this.openId = openId;
        this.privateKey = privateKey;
        this.pubKey = pubKey;

        Log.i("bindOpenId public V3:", pubKey);
        Log.i("bindOpenId private V3:", privateKey);
    }

    public AccountAttributesV3() {
    }

    public boolean isVoice() {
        return voice;
    }

    public boolean isVideo() {
        return video;
    }

    public boolean isFetchesMessages() {
        return fetchesMessages;
    }

    public String getOpenId() {
        return openId;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public String getPubKey() {
        return pubKey;
    }

    public String getSignalingKey() {
        return signalingKey;
    }

    public int getRegistrationId() {
        return registrationId;
    }
}
