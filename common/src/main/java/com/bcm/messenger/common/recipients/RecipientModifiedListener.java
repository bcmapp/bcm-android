package com.bcm.messenger.common.recipients;


import androidx.annotation.NonNull;

/**
 * 
 */
public interface RecipientModifiedListener {
    /**
     * 
     *
     * @param recipient
     */
    void onModified(@NonNull Recipient recipient);
}
