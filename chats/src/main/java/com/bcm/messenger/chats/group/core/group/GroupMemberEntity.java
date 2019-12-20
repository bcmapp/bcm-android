package com.bcm.messenger.chats.group.core.group;

import android.text.TextUtils;

import com.bcm.messenger.chats.group.logic.secure.GroupProof;
import com.bcm.messenger.common.grouprepository.room.entity.GroupInfo;
import com.bcm.messenger.common.grouprepository.room.entity.GroupMember;
import com.bcm.messenger.utility.EncryptUtils;
import com.bcm.messenger.utility.logger.ALog;
import com.bcm.messenger.utility.proguard.NotGuard;

/**
 * ling created in 2018/6/4
 **/
public class GroupMemberEntity implements NotGuard {
    public String uid;
    public String nick;
    public String nickname;       //channel key encrypted nick name
    public String group_nickname; //channel key encrypted group extra name
    public String profile_keys;   //channel key encrypted member avatar key
    /**
     * 1 group owner，2 manager，3 member，4 subscriber
     */
    public int role;
    public String create_time;
    public long status;
    public String proof = "";

    public GroupMember toDbMember(Long gid, String profileKey, GroupInfo groupInfo) {
        GroupMember member = new GroupMember();
        member.setUid(uid);
        member.setGid(gid);
        member.setRole(role);

        if (groupInfo.isNewGroup()) {
            GroupProof.GroupMemberProof memberProof = GroupProof.INSTANCE.decodeMemberProof(proof);
            if (null == memberProof) {
                ALog.e("GroupMember", "proof decode failed");
                return null;
            }
            if(!GroupProof.INSTANCE.checkMember(groupInfo, uid, memberProof)) {
                ALog.e("GroupMember", "proof failed");
                return null;
            }
        }

        try {
            member.setJoinTime(0L);
            if (null != create_time) {
                member.setJoinTime(Long.parseLong(create_time));
            }

            if (!TextUtils.isEmpty(profileKey)) {
                if (!TextUtils.isEmpty(nickname)) {
                    member.setNickname(EncryptUtils.aes256DecryptAndBase64(nickname, profileKey.getBytes()));
                }

                if (!TextUtils.isEmpty(group_nickname)) {
                    member.setCustomNickname(EncryptUtils.aes256DecryptAndBase64(group_nickname, profileKey.getBytes()));
                }

                if (!TextUtils.isEmpty(profile_keys)) {
                    member.setProfileKeyConfig(EncryptUtils.aes256DecryptAndBase64(profile_keys, profileKey.getBytes()));
                }
            }
        } catch (Throwable e) {
            ALog.e("GroupMemberSyncManager", "syncGroupMember $gid cast error", e);
        }

        return member;
    }

    public long joinTime() {
        try {
            if (null != create_time) {
                return Long.parseLong(create_time);
            }
        } catch (Throwable e) {
            ALog.e("GroupMemberSyncManager", "joinTime $gid cast error", e);
        }
        return 0;
    }

}
