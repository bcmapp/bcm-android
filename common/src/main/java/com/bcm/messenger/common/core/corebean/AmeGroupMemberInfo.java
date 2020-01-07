package com.bcm.messenger.common.core.corebean;

import android.text.TextUtils;

import com.bcm.messenger.common.core.Address;
import com.bcm.messenger.utility.GsonUtils;
import com.bcm.messenger.utility.logger.ALog;
import com.bcm.messenger.utility.proguard.NotGuard;

public class AmeGroupMemberInfo {
    private long gid = 0;
    private String uid = "";
    private long role = AmeGroupMemberInfo.VISITOR;
    private long createTime = 0;
    private String nickname;
    private String  customNickname = "";
    private KeyConfig keyConfig;

    public static final AmeGroupMemberInfo MEMBER_ADD_MEMBER = new AmeGroupMemberInfo();
    public static final AmeGroupMemberInfo MEMBER_REMOVE = new AmeGroupMemberInfo();
    public static final AmeGroupMemberInfo MEMBER_SEARCH = new AmeGroupMemberInfo();

    //AmeGroupMemberInfo.role
    public final static long VISITOR = 0L;
    public final static long OWNER = 1L;
    public final static long ADMIN = 2L;
    public final static long MEMBER = 3L;



    public long getGid() {
        return gid;
    }

    public void setGid(long gid) {
        this.gid = gid;
    }

    public Long getRole() {
        return role;
    }

    public void setRole(long role) {
        this.role = role;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getCustomNickname() {
        return customNickname;
    }

    public void setCustomNickname(String customNickname) {
        this.customNickname = customNickname;
    }

    public KeyConfig getKeyConfig() {
        return keyConfig;
    }

    public void setKeyConfig(KeyConfig keyConfig) {
        this.keyConfig = keyConfig;
    }

    public static class KeyConfig implements NotGuard {
        public String avatarKey = "";
        public int    version = 0;

        public static KeyConfig fromJson(String json) {
            try{
                if (!TextUtils.isEmpty(json)) {
                    return GsonUtils.INSTANCE.fromJson(json, KeyConfig.class);
                }
            } catch (Exception e) {
                ALog.e("KeyConfig", e);
            }
            return null;
        }

        @Override
        public String toString() {
            return GsonUtils.INSTANCE.toJson(this);
        }
    }
}
