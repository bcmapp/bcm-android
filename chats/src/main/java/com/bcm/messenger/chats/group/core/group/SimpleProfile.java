package com.bcm.messenger.chats.group.core.group;

import com.bcm.messenger.utility.proguard.NotGuard;
import com.google.gson.annotations.SerializedName;

public class SimpleProfile implements NotGuard {

    /**
     * identityKey : BXLn+EnrFz/aDkTfPb0vQgnA/TsrZN2HcbNvJIdS8Yw0
     * openId : +8610000000518-235
     * openIdUpdateCounts : 0
     * profileKey : w+tQk6/Gs3vRNtEbu5A5rE/5J4+ddOq0LxZOwbWyIZo=
     * avatar : 6968841198953052361
     * namePlaintext : 5bCP5Z2a
     * avatarNamePlaintext : c5364086541c4295820e1fd01f7d769f
     */

    @SerializedName("identityKey")
    private String identityKey;

    private String uid;

    public String getIdentityKey() {
        return identityKey;
    }

    public void setIdentityKey(String identityKey) {
        this.identityKey = identityKey;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }
}
