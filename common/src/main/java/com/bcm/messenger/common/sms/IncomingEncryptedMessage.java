package com.bcm.messenger.common.sms;

public class IncomingEncryptedMessage extends IncomingTextMessage {

    public IncomingEncryptedMessage(IncomingTextMessage base, String newBody) {
        super(base, newBody);
        this.payloadType = parseBodyPayloadType(newBody);
    }

    @Override
    public IncomingTextMessage withMessageBody(String body) {
        return new IncomingEncryptedMessage(this, body);
    }

    @Override
    public boolean isSecureMessage() {
        return true;
    }


}
