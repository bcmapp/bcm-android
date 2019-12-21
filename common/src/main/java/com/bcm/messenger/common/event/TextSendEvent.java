package com.bcm.messenger.common.event;

/**
 * 
 */
public class TextSendEvent {
    public final long messageId;
    public final boolean success;
    public final String details;

    public TextSendEvent(long messageId, boolean success, String details) {
        this.messageId = messageId;
        this.success = success;
        this.details = details;
    }
}
