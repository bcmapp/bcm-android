package com.bcm.messenger.common.mms;

import com.bcm.messenger.common.attachments.Attachment;
import com.bcm.messenger.common.recipients.Recipient;

import java.util.LinkedList;
import java.util.List;

/**
 * 复杂的媒体消息（用于小群）
 */
public class OutgoingComplexMediaMessage extends OutgoingMediaMessage {

    private boolean isLocation;
    private boolean isSecure;

    public OutgoingComplexMediaMessage(Recipient recipient, String body,
                                       List<Attachment> attachments,
                                       long sentTimeMillis,
                                       int subscriptionId,
                                       long expiresIn,
                                       int distributionType,
                                       boolean isLocation,
                                       boolean isSecure) {
        super(recipient, body, attachments, sentTimeMillis, subscriptionId, expiresIn, distributionType);
        this.isLocation = isLocation;
        this.isSecure = isSecure;
    }

    public OutgoingComplexMediaMessage(Recipient recipient, String body, long sentTimeMillis,
                                       int subscriptionId, long expiresIn, int distributionType,
                                       boolean isLocation,
                                       boolean isSecure) {
        this(recipient, body, new LinkedList<Attachment>(), sentTimeMillis, subscriptionId, expiresIn, distributionType, isLocation, isSecure);
    }

    public OutgoingComplexMediaMessage(OutgoingMediaMessage base) {
        super(base);
    }

    @Override
    public boolean isSecure() {
        return isSecure;
    }

    @Override
    public boolean isLocation() {
        return isLocation;
    }
}
