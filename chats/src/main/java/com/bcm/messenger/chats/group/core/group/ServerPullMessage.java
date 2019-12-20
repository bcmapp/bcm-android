package com.bcm.messenger.chats.group.core.group;


import com.bcm.messenger.utility.proguard.NotGuard;

/**
 * ling created in 2018/6/8
 **/
public class ServerPullMessage implements NotGuard {
    public long mid;

    public int type;
    public Object data;
    public String originText;
    public String fromUid;

    public long createTime;
}
