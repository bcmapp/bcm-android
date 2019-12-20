package com.bcm.messenger.common.grouprepository.events;

import com.bcm.messenger.common.core.corebean.AmeGroupInfo;

public class GroupInfoUpdateNotify {
    private AmeGroupInfo groupInfo;

    public AmeGroupInfo getGroupInfo() {
        return groupInfo;
    }

    public void setGroupInfo(AmeGroupInfo groupInfo) {
        this.groupInfo = groupInfo;
    }
}
