package com.bcm.messenger.common.recipients;


import androidx.annotation.NonNull;

/**
 * 联系人信息变更回调类
 */
public interface RecipientModifiedListener {
    /**
     * 联系人信息变更回调
     *
     * @param recipient
     */
    void onModified(@NonNull Recipient recipient);
}
