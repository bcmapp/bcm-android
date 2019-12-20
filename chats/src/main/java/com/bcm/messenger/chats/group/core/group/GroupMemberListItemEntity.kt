package com.bcm.messenger.chats.group.core.group

import com.bcm.messenger.common.grouprepository.room.entity.GroupInfo
import com.bcm.messenger.common.grouprepository.room.entity.GroupMember

class GroupMemberListItemEntity:GroupMemberEntity() {
    var groupNickname:String = ""
    var profileKeys:String = ""
    var createTime:String = ""

    override fun toDbMember(gid: Long, profileKey: String?, groupInfo:GroupInfo): GroupMember? {
        create_time = createTime
        group_nickname = groupNickname
        profile_keys = profileKeys
        return super.toDbMember(gid, profileKey, groupInfo)
    }
}