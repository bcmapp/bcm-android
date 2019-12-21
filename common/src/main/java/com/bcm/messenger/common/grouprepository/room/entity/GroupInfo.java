package com.bcm.messenger.common.grouprepository.room.entity;

import android.text.TextUtils;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import com.bcm.messenger.common.core.corebean.GroupMemberSyncState;
import com.bcm.messenger.common.utils.BCMPrivateKeyUtils;
import com.bcm.messenger.common.crypto.encrypt.GroupMessageEncryptUtils;
import com.bcm.messenger.utility.Base64;
import com.bcm.messenger.utility.EncryptUtils;
import com.bcm.messenger.utility.logger.ALog;


import org.whispersystems.libsignal.kdf.HKDF;

import static com.bcm.messenger.common.grouprepository.room.entity.GroupInfo.TABLE_NAME;

@Entity(tableName = TABLE_NAME)
public class GroupInfo {
    public static final String TABLE_NAME = "group_info";
    /**
     * gid
     */
    @PrimaryKey
    @ColumnInfo(index = true)
    private long gid;
    private String owner;
    /**
     * group_name
     */
    private String name;
    //
    @ColumnInfo(name = "key")
    private String currentKey;
    @ColumnInfo(name = "key_version")
    private long currentKeyVersion;

    //channel 
    @ColumnInfo(name = "channel_key")
    private String channel_key;
    private int permission;
    private String iconUrl;
    private int broadcast;//
    private long createTime;
    private int status;//
    private long thread_id;
    private String share_url;
    private String share_content;
    private int member_count;
    private int subscriber_count;
    @ColumnInfo(name = "role")
    private long role;//
    @ColumnInfo(name = "illegal")
    private int illegal;
    private int notification_enable;
    //,
    private String notice_content;
    private long notice_update_time;

    private int is_show_notice;
    private String member_sync_state;

    @ColumnInfo(name = "share_qr_code_setting")
    private String shareCodeSetting;
    @ColumnInfo(name = "owner_confirm")
    private Integer needOwnerConfirm;
    @ColumnInfo(name = "share_sig")
    private String shareCodeSettingSign;
    @ColumnInfo(name = "share_and_owner_confirm_sig")
    private String shareSettingAndConfirmSign;
    @ColumnInfo (name = "group_info_secret")
    private String infoSecret;//share_qr_code_setting
    @ColumnInfo(name = "share_enabled")
    private Integer shareEnabled;//1 ，0 
    @ColumnInfo(name = "share_code")
    private String shareCode;//
    @ColumnInfo(name = "share_epoch")
    private Integer shareEpoch; //
    @ColumnInfo(name = "group_splice_name")
    private String spliceName; // 
    @ColumnInfo(name = "chn_splice_name")
    private String chnSpliceName; // 
    @ColumnInfo(name = "splice_avatar")
    private String spliceAvatar; // 
    @ColumnInfo(name = "ephemeral_key")
    private String ephemeralKey;
    private int version;//0， 3
    @ColumnInfo(name = "profile_encrypted")
    private boolean profileEncrypted = false;

    @Ignore
    private byte[] groupPrivateKey;
    @Ignore
    private byte[] groupPublicKey;
    @Ignore
    private byte [] channelPrivateKey;
    @Ignore
    private byte [] channelPublicKey;

    @ColumnInfo(name = "share_link")
    private String shareLink;

    public static final int SHOW_NOTICE = 0;
    public static final int NOT_SHOW_NOTICE = 1;

    //mid
    private long pinMid;
    private int hasPin;

    public static final int NOTIFICATION_ENABLE = 1;
    public static final int NOTIFICATION_DISABLE = 0;

    public static final int ILLEGAL_GROUP = -1000;
    public static final int LEGITIMATE_GROUP = 1000;

    public GroupInfo() {
        notification_enable = NOTIFICATION_ENABLE;
    }

    public long getGid() {
        return gid;
    }

    public void setGid(long gid) {
        this.gid = gid;
    }

    public String getName() {
        if (null == name) {
            return "";
        }
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCurrentKey() {
        return currentKey;
    }

    public void setCurrentKey(String key) {
        this.currentKey = key;
    }

    public int getPermission() {
        return permission;
    }

    public void setPermission(int permission) {
        this.permission = permission;
    }

    public String getIconUrl() {
        if (null == iconUrl) {
            return "";
        }
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public int getBroadcast() {
        return broadcast;
    }

    public void setBroadcast(int broadcast) {
        this.broadcast = broadcast;
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

    public long getThread_id() {
        return thread_id;
    }

    public void setThread_id(long thread_id) {
        this.thread_id = thread_id;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }


    public String getShare_content() {
        return share_content;
    }

    public void setShare_content(String share_content) {
        this.share_content = share_content;
    }

    public int getMember_count() {
        return member_count;
    }

    public void setMember_count(int member_count) {
        this.member_count = member_count;
    }

    public int getSubscriber_count() {
        return subscriber_count;
    }

    public void setSubscriber_count(int subscriber_count) {
        this.subscriber_count = subscriber_count;
    }

    public String getShare_url() {
        return share_url;
    }

    public void setShare_url(String share_url) {
        this.share_url = share_url;
    }

    public int getNotification_enable() {
        return notification_enable;
    }

    public void setNotification_enable(int notification_enable) {
        this.notification_enable = notification_enable;
    }

    public String getChannel_key() {
        String keyParam = currentKey;
        if (isNewGroup()) {
            keyParam = infoSecret;

        }

        if (TextUtils.isEmpty(this.channel_key)) {
            this.channel_key = GroupMessageEncryptUtils.generateChannelKey(keyParam);
        }
        return channel_key;
    }

    public void setChannel_key(String channel_key) {
        this.channel_key = channel_key;
    }

    public long getRole() {
        return role;
    }

    public void setRole(long role) {
        this.role = role;
    }

    public int getIllegal() {
        return illegal;
    }

    public void setIllegal(int illegal) {
        this.illegal = illegal;
    }

    public String getNotice_content() {
        return notice_content;
    }

    public void setNotice_content(String notice_content) {
        this.notice_content = notice_content;
    }

    public long getNotice_update_time() {
        return notice_update_time;
    }

    public void setNotice_update_time(long notice_update_time) {
        this.notice_update_time = notice_update_time;
    }

    public long getPinMid() {
        return pinMid;
    }

    public void setPinMid(long pinMid) {
        this.pinMid = pinMid;
    }


    public int getIs_show_notice() {
        return is_show_notice;
    }

    public void setIs_show_notice(int is_show_notice) {
        this.is_show_notice = is_show_notice;
    }

    public int getHasPin() {
        return hasPin;
    }

    public void setHasPin(int hasPin) {
        this.hasPin = hasPin;
    }

    public String getMember_sync_state() {
        if (member_sync_state == null) {
            member_sync_state = GroupMemberSyncState.DIRTY.toString();
        }
        return member_sync_state;
    }

    public void setMember_sync_state(String member_sync_state) {
        this.member_sync_state = member_sync_state;
    }

    public String getShareLink() {
        return shareLink;
    }

    public void setShareLink(String shareLink) {
        this.shareLink = shareLink;
    }

    public String getShareCodeSetting() {
        if (shareCodeSetting == null){
            return "";
        }
        return shareCodeSetting;
    }

    public void setShareCodeSetting(String shareCodeSetting) {
        this.shareCodeSetting = shareCodeSetting;
    }

    public Integer getNeedOwnerConfirm() {
        if (null == needOwnerConfirm) {
            return 0;
        }
        return needOwnerConfirm;
    }

    public void setNeedOwnerConfirm(Integer needOwnerConfirm) {
        this.needOwnerConfirm = needOwnerConfirm;
    }

    public String getShareCodeSettingSign() {
        if (shareCodeSettingSign == null){
            return "";
        }
        return shareCodeSettingSign;
    }

    public void setShareCodeSettingSign(String shareCodeSettingSign) {
        this.shareCodeSettingSign = shareCodeSettingSign;
    }

    public String getShareSettingAndConfirmSign() {
        if (shareSettingAndConfirmSign == null){
            return "";
        }
        return shareSettingAndConfirmSign;
    }

    public void setShareSettingAndConfirmSign(String shareSettingAndConfirmSign) {
        this.shareSettingAndConfirmSign = shareSettingAndConfirmSign;
    }

    public String getInfoSecret() {
        return infoSecret;
    }

    public void setInfoSecret(String infoSecret) {
        this.infoSecret = infoSecret;
    }

    public Integer getShareEnabled() {
        if (null == shareEnabled) {
            return 0;
        }
        return shareEnabled;
    }

    public void setShareEnabled(Integer shareEnabled) {
        this.shareEnabled = shareEnabled;
    }

    public String getShareCode() {
        return shareCode;
    }

    public void setShareCode(String shareCode) {
        this.shareCode = shareCode;
    }

    public Integer getShareEpoch() {
        if (null == shareEpoch) {
            return 1;
        }
        return shareEpoch;
    }

    public void setShareEpoch(Integer shareEpoch) {
        this.shareEpoch = shareEpoch;
    }

    public String getSpliceName() {
        return spliceName;
    }

    public void setSpliceName(String spliceName) {
        this.spliceName = spliceName;
    }

    public String getSpliceAvatar() {
        return spliceAvatar;
    }

    public void setSpliceAvatar(String spliceAvatar) {
        this.spliceAvatar = spliceAvatar;
    }

    public String getChnSpliceName() {
        return chnSpliceName;
    }

    public void setChnSpliceName(String chnSpliceName) {
        this.chnSpliceName = chnSpliceName;
    }

    public byte[] getGroupPrivateKey() {
        if (null == groupPrivateKey) {
            genGroupKeyPair();
        }
        return groupPrivateKey;
    }

    public byte[] getGroupPublicKey() {
        if (null == groupPublicKey) {
            genGroupKeyPair();
        }
        return groupPublicKey;
    }

    private void genGroupKeyPair() {
        try {
            byte[] groupInfoSecret = EncryptUtils.base64Decode(infoSecret.getBytes());
            byte[] groupInfoSecretKDF = HKDF.createFor(3).deriveSecrets(groupInfoSecret, "GROUP_INFO_SECRET".getBytes(), 32);

            groupPrivateKey = BCMPrivateKeyUtils.INSTANCE.generatePrivateKey(groupInfoSecretKDF);
            if (null != groupPrivateKey) {
                groupPublicKey = BCMPrivateKeyUtils.INSTANCE.generatePublicKeyWithDJB(this.groupPrivateKey);
            }

        } catch (Exception ex) {
            ALog.e("GroupInfo", "genGroupKeyPair error", ex);
        }
    }

    public String getEphemeralKey() {
        return ephemeralKey;
    }

    public void setEphemeralKey(String ephemeralKey) {
        this.ephemeralKey = ephemeralKey;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Boolean isNewGroup() {
        return version == 3;
    }

    public boolean isProfileEncrypted() {
        return profileEncrypted;
    }

    public void setProfileEncrypted(boolean profileEncrypted) {
        this.profileEncrypted = profileEncrypted;
    }

    public byte[] getChannelPrivateKey() {
        if (isNewGroup()) {
            return getGroupPrivateKey();
        } else  {
            //
            if (null == channelPrivateKey) {
                genChannelKeyPair();
            }
        }

        return channelPrivateKey;
    }


    public byte[] getChannelPublicKey() {
        if (isNewGroup()) {
            return getGroupPublicKey();
        } else {
            //
            if (null == channelPublicKey) {
                genChannelKeyPair();
            }
        }

        return channelPublicKey;
    }

    /**
     * channelkey
     * @return
     */
    private void genChannelKeyPair() {
        try {
            byte[] channelKeyBytes = Base64.decode(getChannel_key());
            byte[] kdfKeyBytes = HKDF.createFor(3).deriveSecrets(channelKeyBytes, "BCM_GROUP".getBytes(), 32);

            channelPrivateKey = BCMPrivateKeyUtils.INSTANCE.generatePrivateKey(kdfKeyBytes);
            if (channelPrivateKey == null) {
                throw new Exception("derivePrivateKey is null");
            }

            this.channelPublicKey = BCMPrivateKeyUtils.INSTANCE.generatePublicKeyWithDJB(channelPrivateKey);

        }catch (Exception ex) {
            ALog.e("GroupInfo", "genChannelKeyPair error", ex);
        }
    }

    public long getCurrentKeyVersion() {
        return currentKeyVersion;
    }

    public void setCurrentKeyVersion(long currentKeyVersion) {
        this.currentKeyVersion = currentKeyVersion;
    }


    public enum GroupStatus {
        BROADCAST_SHARE_DATA_ING(0x100);

        private int status;
        GroupStatus(int status) {
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }
}
