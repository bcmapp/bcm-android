package com.bcm.messenger.chats.group.core.group;

import com.bcm.messenger.utility.proguard.NotGuard;

public class GroupMessagePayload implements NotGuard {
    int version;
    String header;
    String body;
}
