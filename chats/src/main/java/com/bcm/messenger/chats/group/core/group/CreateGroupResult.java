package com.bcm.messenger.chats.group.core.group;


import com.bcm.messenger.utility.proguard.NotGuard;

import java.util.List;

/**
 * ling created in 2018/5/31
 **/
public class CreateGroupResult implements NotGuard {
    public long gid;
    public List<String> failed_members;
}
