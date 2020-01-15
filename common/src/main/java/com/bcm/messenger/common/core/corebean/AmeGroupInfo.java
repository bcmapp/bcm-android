package com.bcm.messenger.common.core.corebean;

import android.text.TextUtils;

import com.bcm.messenger.common.R;
import com.bcm.messenger.common.core.AmeLanguageUtilsKt;
import com.bcm.messenger.common.utils.AppUtilKotlinKt;
import com.bcm.messenger.utility.AppContextHolder;

import java.util.Locale;
import java.util.Objects;

public class AmeGroupInfo {

    private Long gid;
    private String name;
    private String owner;
    private String key;
    private String infoSecret;
    private String channelKey;
    private int permission;
    private String iconUrl;
    private long createTime;
    private int memberCount;
    private String shareLink; //
    private String shareContent;//
    private Boolean stickOnTop;
    private Boolean mute;
    private int status;//
    private Long role;// AmeGroupMemberInfo role
    private LegitimateState legitimateState;
    private long pinMid;
    private Boolean hasPin;
    private Boolean shareEnable;
    private Boolean needConfirm;

    //
    private String noticeContent;
    private long  noticeUpdateTime;

    private boolean isShowNotice;

    private String spliceName;
    private String chnSpliceName;
    private String spliceAvatarPath;
    private Boolean isNewGroup;
    private boolean profileEncrypted;

    private GroupMemberSyncState memberSyncState = GroupMemberSyncState.DIRTY;

    public AmeGroupInfo() {
        isShowNotice = true;
        mute = true;
    }

    public AmeGroupInfo(Long gid) {
        isShowNotice = true;
        mute = true;
        this.gid = gid;
    }

    public String getShareContent() {
        return shareContent;
    }

    public void setShareContent(String shareContent) {
        this.shareContent = shareContent;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getChannelKey() {
        return this.channelKey;
    }

    public void setChannelKey(String channelKey) {
        this.channelKey = channelKey;
    }

    public Long getRole() {
        if (null == role) {
            return AmeGroupMemberInfo.VISITOR;
        }
        return role;
    }

    public void setRole(Long role) {
        this.role = role;
    }

    public GroupMemberSyncState getMemberSyncState() {
        return memberSyncState;
    }

    public void setMemberSyncState(GroupMemberSyncState memberSyncState) {
        this.memberSyncState = memberSyncState;
    }

    public Boolean getShareEnable() {
        return shareEnable;
    }

    public void setShareEnable(Boolean shareEnable) {
        this.shareEnable = shareEnable;
    }

    public Boolean getNeedConfirm() {
        return needConfirm;
    }

    public void setNeedConfirm(Boolean needConfirm) {
        this.needConfirm = needConfirm;
    }

    public String getInfoSecret() {
        return infoSecret;
    }

    public void setInfoSecret(String infoSecret) {
        this.infoSecret = infoSecret;
    }

    public Boolean getNewGroup() {
        return isNewGroup;
    }

    public void setNewGroup(Boolean newGroup) {
        isNewGroup = newGroup;
    }

    public enum LegitimateState{
        LEGITIMATE,
        ILLEGAL
    }

    public enum Field {
        NAME(1),
        AVATAR(1 << 1),
        PERMISSION(1 << 2),
        SHARE_URL(1 << 3),
        SUBSCRIBER_ENABLE(1 << 4),
        SHARE_CONTENT(1 << 5),;

        private final long fieldValue;

        Field(long value) {
            this.fieldValue = value;
        }

        public long getFieldValue() {
            return fieldValue;
        }
    }


    public Long getGid() {
        return gid;
    }

    public void setGid(Long gid) {
        this.gid = gid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public int getPermission() {
        return permission;
    }

    public void setPermission(int permission) {
        this.permission = permission;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }


    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getShareLink() {
        return shareLink;
    }

    public void setShareLink(String shareLink) {
        this.shareLink = shareLink;
    }

    public Boolean getStickOnTop() {
        return stickOnTop;
    }

    public void setStickOnTop(Boolean stickOnTop) {
        this.stickOnTop = stickOnTop;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
    }

    public Boolean getMute() {
        return mute;
    }

    public void setMute(Boolean mute) {
        this.mute = mute;
    }

    public LegitimateState getLegitimateState() {
        return legitimateState;
    }

    public void setLegitimateState(LegitimateState legitimateState) {
        this.legitimateState = legitimateState;
    }

    public String getNoticeContent() {
        if (TextUtils.isEmpty(noticeContent)) {
            return "";
        }
        return noticeContent;
    }

    public void setNoticeContent(String noticeContent) {
        this.noticeContent = noticeContent;
    }

    public long getNoticeUpdateTime() {
        return noticeUpdateTime;
    }

    public void setNoticeUpdateTime(long noticeUpdateTime) {
        this.noticeUpdateTime = noticeUpdateTime;
    }

    public boolean isShowNotice() {
        return isShowNotice;
    }

    public void setShowNotice(boolean showNotice) {
        isShowNotice = showNotice;
    }

    public long getPinMid() {
        return pinMid;
    }

    public void setPinMid(long pinMid) {
        this.pinMid = pinMid;
    }

    public Boolean getHasPin() {
        return hasPin;
    }

    public void setHasPin(Boolean hasPin) {
        this.hasPin = hasPin;
    }

    public String getSpliceName() {
        return spliceName;
    }

    public void setSpliceName(String spliceName) {
        this.spliceName = spliceName;
    }

    public String getSpliceAvatarPath() {
        return spliceAvatarPath;
    }

    public void setSpliceAvatarPath(String spliceAvatarPath) {
        this.spliceAvatarPath = spliceAvatarPath;
    }

    public String getChnSpliceName() {
        return chnSpliceName;
    }

    public void setChnSpliceName(String chnSpliceName) {
        this.chnSpliceName = chnSpliceName;
    }

    public String getDisplayName() {
        if (name != null && !name.isEmpty()) {
            return name;
        }
        if (AmeLanguageUtilsKt.getSelectedLocale(AppContextHolder.APP_CONTEXT).getLanguage().equals(Locale.CHINESE.getLanguage())) {
            if (chnSpliceName != null && !chnSpliceName.isEmpty()) {
                return chnSpliceName;
            }
        }

        if (spliceName != null && !spliceName.isEmpty()) {
            return spliceName;
        }
        return AppUtilKotlinKt.getString(R.string.common_chats_group_default_display_name);
    }

    public boolean isProfileEncrypted() {
        return profileEncrypted;
    }

    public void setProfileEncrypted(boolean profileEncrypted) {
        this.profileEncrypted = profileEncrypted;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AmeGroupInfo that = (AmeGroupInfo) o;
        return Objects.equals(gid, that.gid) &&
                Objects.equals(name, that.name) &&
                Objects.equals(owner, that.owner) &&
                Objects.equals(key, that.key) &&
                Objects.equals(role, that.role);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gid, name, owner, key, role);
    }
}
