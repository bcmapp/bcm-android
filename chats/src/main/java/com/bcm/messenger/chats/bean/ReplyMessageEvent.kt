package com.bcm.messenger.chats.bean

import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail

/**
 * Created by wjh on 2018/11/27
 */
class ReplyMessageEvent(val messageDetail: AmeGroupMessageDetail, val action: Int) {
    companion object {
        const val ACTION_REPLY = 1
        const val ACTION_LOCATE = 2
    }
}