/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 * <p>
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.push;

import org.whispersystems.libsignal.logging.Log;

public class AccountAttributesV2 {
    private String signalingKey;
    private int registrationId;
    private boolean voice;
    private boolean video;
    private boolean fetchesMessages;
    private String privateKey;
    private String pubKey;

    public AccountAttributesV2(String signalingKey, int registrationId, boolean fetchesMessages) {
        this.signalingKey = signalingKey;
        this.registrationId = registrationId;
        this.voice = true;
        this.video = true;
        this.fetchesMessages = fetchesMessages;

    }

    public AccountAttributesV2(String signalingKey, int registrationId, boolean fetchesMessages, String pubKey, String privateKey) {
        this.signalingKey = signalingKey;
        this.registrationId = registrationId;
        this.voice = true;
        this.video = true;
        this.fetchesMessages = fetchesMessages;
        this.pubKey = pubKey;
        this.privateKey = privateKey;

        Log.i("bindOpenId public V2:", pubKey);
        Log.i("bindOpenId private V2:", privateKey);
    }

    public AccountAttributesV2() {
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

    public String getPrivateKey() {
        return privateKey;
    }

    public String getPubKey() {
        return pubKey;
    }
}
