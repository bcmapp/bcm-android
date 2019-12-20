package com.bcm.messenger.common.sms;

import android.text.TextUtils;

import com.bcm.messenger.common.core.AmeGroupMessage;
import com.bcm.messenger.utility.AmeURLUtil;

import com.bcm.messenger.common.recipients.Recipient;

public class OutgoingTextMessage {

    private final Recipient recipient;
    private final String message;
    private final int subscriptionId;
    private final long expiresIn;
    protected int payloadType;

    public OutgoingTextMessage(Recipient recipient, String message, int subscriptionId) {
        this(recipient, message, 0, subscriptionId);
    }

    public OutgoingTextMessage(Recipient recipient, String message, long expiresIn, int subscriptionId) {
        this.recipient = recipient;
        this.message = message;
        this.payloadType = parseBodyPayloadType(message);
        this.expiresIn = expiresIn;
        this.subscriptionId = subscriptionId;
    }

    protected OutgoingTextMessage(OutgoingTextMessage base, String body) {
        this.recipient = base.getRecipient();
        this.payloadType = base.payloadType;
        this.subscriptionId = base.getSubscriptionId();
        this.expiresIn = base.getExpiresIn();
        this.message = body;
    }


    protected int parseBodyPayloadType(String encodedBody) {
        if (!TextUtils.isEmpty(encodedBody) && AmeURLUtil.INSTANCE.isLegitimateUrl(encodedBody))
            return (int) AmeGroupMessage.LINK;
        return 0;
    }


    public long getExpiresIn() {
        return expiresIn;
    }

    public int getSubscriptionId() {
        return subscriptionId;
    }

    public String getMessageBody() {
        return message;
    }

    public Recipient getRecipient() {
        return recipient;
    }

    public boolean isKeyExchange() {
        return false;
    }

    public boolean isLocation() {
        return false;
    }

    public int getPayloadType() {
        return payloadType;
    }

    public boolean isSecureMessage() {
        return false;
    }

    public boolean isEndSession() {
        return false;
    }

    public boolean isPreKeyBundle() {
        return false;
    }

    public boolean isIdentityVerified() {
        return false;
    }

    public boolean isIdentityDefault() {
        return false;
    }

    public OutgoingTextMessage withBody(String body) {
        return new OutgoingTextMessage(this, body);
    }
}
