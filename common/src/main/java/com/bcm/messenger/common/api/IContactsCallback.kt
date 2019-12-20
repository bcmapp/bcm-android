package com.bcm.messenger.common.api

import com.bcm.messenger.common.recipients.Recipient

/**
 * 联系人操作回调
 * Created by wjh on 2018/4/13
 */
interface IContactsCallback {
    /**
     * 选中某个联系人
     */
    fun onSelect(recipient: Recipient)
    /**
     * 取消选中某个联系人
     */
    fun onDeselect(recipient: Recipient)

    /**
     * 选择模式变更
     */
    fun onModeChanged(multiSelect: Boolean) {}

}