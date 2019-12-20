package com.bcm.messenger.common.sms;

import com.bcm.messenger.common.core.AmeGroupMessage;

import com.bcm.messenger.common.recipients.Recipient;

public class OutgoingLocationMessage extends OutgoingEncryptedMessage {

    public OutgoingLocationMessage(Recipient recipient, String body, long expiresIn) {
        super(recipient, body, needExpireTime(body)?expiresIn:0L);
    }


    private OutgoingLocationMessage(OutgoingLocationMessage base, String body) {
        super(base, body);
    }

    @Override
    public boolean isLocation() {
        return true;
    }

    @Override
    public OutgoingTextMessage withBody(String body) {
        return new OutgoingLocationMessage(this, body);
    }

    @Override
    protected int parseBodyPayloadType(String encodedBody) {
       AmeGroupMessage ameGroupMessage = AmeGroupMessage.Companion.messageFromJson(encodedBody);
       if (null != ameGroupMessage){
           return (int)ameGroupMessage.getType();
       }
       return 0;
    }

    private static boolean needExpireTime(String body){
        AmeGroupMessage ameGroupMessage = AmeGroupMessage.Companion.messageFromJson(body);
        long payloadType = ameGroupMessage.getType();
        return (payloadType != AmeGroupMessage.SCREEN_SHOT_MESSAGE
            && payloadType != AmeGroupMessage.CONTROL_MESSAGE
            && payloadType != AmeGroupMessage.EXCHANGE_PROFILE
            && payloadType != AmeGroupMessage.NONSUPPORT);
    }
}
