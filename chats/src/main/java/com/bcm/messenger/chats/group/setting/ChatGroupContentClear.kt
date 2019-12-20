package com.bcm.messenger.chats.group.setting

/**
 * 群消息清理事件
 */
class ChatGroupContentClear(val groupId: Long) {

    companion object {
        const val GROUP_CONTENT_CLEAR = "group_content_clear"
    }
}