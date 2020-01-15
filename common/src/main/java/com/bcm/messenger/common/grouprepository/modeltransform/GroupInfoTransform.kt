package com.bcm.messenger.common.grouprepository.modeltransform

import com.bcm.messenger.common.core.corebean.AmeGroupInfo
import com.bcm.messenger.common.core.corebean.GroupMemberSyncState
import com.bcm.messenger.common.grouprepository.room.entity.GroupInfo


object GroupInfoTransform {
    fun transformToModel(entity: GroupInfo): AmeGroupInfo {
        val ameGroupInfo = AmeGroupInfo()
        ameGroupInfo.gid = entity.gid
        ameGroupInfo.shareContent = entity.share_content
        ameGroupInfo.iconUrl = entity.iconUrl
        ameGroupInfo.name = entity.name
        ameGroupInfo.mute = (entity.notification_enable != GroupInfo.NOTIFICATION_ENABLE)
        ameGroupInfo.memberCount = entity.member_count
        ameGroupInfo.createTime = entity.createTime
        ameGroupInfo.owner = entity.owner
        ameGroupInfo.permission = entity.permission
        ameGroupInfo.shareLink = entity.shareLink
        ameGroupInfo.channelKey = entity.channel_key
        ameGroupInfo.key = entity.currentKey
        ameGroupInfo.role = entity.role
        ameGroupInfo.noticeContent = entity.notice_content
        ameGroupInfo.noticeUpdateTime = entity.notice_update_time
        ameGroupInfo.pinMid = entity.pinMid
        ameGroupInfo.hasPin = (entity.hasPin != 0)
        ameGroupInfo.infoSecret = entity.infoSecret
        ameGroupInfo.newGroup = entity.isNewGroup

        ameGroupInfo.isShowNotice = when (entity.is_show_notice) {
            GroupInfo.SHOW_NOTICE -> true
            else -> false
        }

        ameGroupInfo.shareEnable = entity.shareEnabled == 1
        ameGroupInfo.needConfirm = entity.needOwnerConfirm == 1

        ameGroupInfo.memberSyncState = GroupMemberSyncState.valueOf(entity.member_sync_state)
        ameGroupInfo.spliceName = entity.spliceName
        ameGroupInfo.spliceAvatarPath = entity.spliceAvatar
        ameGroupInfo.chnSpliceName = entity.chnSpliceName
        when (entity.illegal) {
            GroupInfo.ILLEGAL_GROUP -> ameGroupInfo.legitimateState = AmeGroupInfo.LegitimateState.ILLEGAL
            GroupInfo.LEGITIMATE_GROUP -> ameGroupInfo.legitimateState = AmeGroupInfo.LegitimateState.LEGITIMATE
            else -> ameGroupInfo.legitimateState = AmeGroupInfo.LegitimateState.LEGITIMATE
        }

        ameGroupInfo.isProfileEncrypted = entity.isProfileEncrypted

        return ameGroupInfo
    }

}
