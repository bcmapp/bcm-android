package com.bcm.messenger.common.api

import com.bcm.messenger.common.recipients.Recipient

/**
 * 
 * Created by wjh on 2018/4/13
 */
interface IContactsCallback {
    /**
     * 
     */
    fun onSelect(recipient: Recipient)
    /**
     * 
     */
    fun onDeselect(recipient: Recipient)

    /**
     * 
     */
    fun onModeChanged(multiSelect: Boolean) {}

}