package com.bcm.messenger.common.sms;

import com.bcm.messenger.common.core.AmeGroupMessage;

public class IncomingLocationMessage extends IncomingEncryptedMessage {
    public IncomingLocationMessage(IncomingTextMessage base, String newBody) {
        super(base, newBody);
    }

    @Override
    public IncomingTextMessage withMessageBody(String body) {
        return new IncomingLocationMessage(this, body);
    }

    @Override
    public boolean isLocation() {
        return true;
    }

    @Override
    public boolean isSecureMessage() {
        return true;
    }

    @Override
    protected int parseBodyPayloadType(String body) {
        AmeGroupMessage ameGroupMessage = AmeGroupMessage.Companion.messageFromJson(body);
        if (null != ameGroupMessage){
            return (int)ameGroupMessage.getType();
        }
        return 0;
    }
}
