package com.bcm.messenger.common.finder

/**
 * Created by bcm.social.01 on 2019/4/8.
 * 检索器类型
 */
enum class BcmFinderType {
    ADDRESS_BOOK, //通讯录检索器
    CHAT,         //私聊聊天记录检索器
    THREAD,       //会话检索器
    GROUP,        //群检索器
    GROUP_MEMBER, //群成员检索器
    GROUP_CHAT,   //群聊天记录检索器
    AIR_CHAT //无网通信检索器
}