package com.bcm.messenger.common.core.corebean;

import com.bcm.messenger.utility.proguard.NotGuard;
import com.google.gson.JsonObject;


/**
 * idkey
 */
public class ProfilesResult implements NotGuard {

    private JsonObject profiles;

    public JsonObject getProfiles() {
        return profiles;
    }

    public void setProfiles(JsonObject profiles) {
        this.profiles = profiles;
    }
}
