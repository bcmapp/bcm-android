/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 * <p>
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.push;

public class AccountAttributes {

    private String signalingKey;
    private int registrationId;

    private boolean voice;

    private boolean video;
    private boolean fetchesMessages;

    private String openId;

    private String privateKey;

    public AccountAttributes(String signalingKey, int registrationId, boolean fetchesMessages) {
        this.signalingKey = signalingKey;
        this.registrationId = registrationId;
        this.voice = true;
        this.video = true;
        this.fetchesMessages = fetchesMessages;
    }

    public AccountAttributes(String signalingKey, int registrationId, boolean fetchesMessages, String openId, String privateKey) {
        this.signalingKey = signalingKey;
        this.registrationId = registrationId;
        this.voice = true;
        this.video = true;
        this.fetchesMessages = fetchesMessages;
        this.openId = openId;
        this.privateKey = privateKey;
    }

    public AccountAttributes() {
    }

    public String getSignalingKey() {
        return signalingKey;
    }

    public int getRegistrationId() {
        return registrationId;
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
}
