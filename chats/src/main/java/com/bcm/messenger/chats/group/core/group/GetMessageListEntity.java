package com.bcm.messenger.chats.group.core.group;

import com.bcm.messenger.utility.proguard.NotGuard;

import java.util.List;

/**
 * 拉取的群组消息列表实体
 * ling created in 2018/6/4
 **/
public class GetMessageListEntity implements NotGuard {
    public List<GroupMessageEntity> messages;
}
