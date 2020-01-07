package com.bcm.messenger.common.grouprepository.modeltransform

import android.text.TextUtils
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.grouprepository.room.entity.GroupMember

import java.util.ArrayList

object GroupMemberTransform {
    fun transToModel(dbUser: GroupMember): AmeGroupMemberInfo {
        val member = AmeGroupMemberInfo()
        member.createTime = dbUser.joinTime
        member.gid = dbUser.gid
        member.role = dbUser.role
        member.uid = dbUser.uid

        member.customNickname = dbUser.customNickname
        member.nickname = dbUser.nickname

        if (!TextUtils.isEmpty(dbUser.profileKeyConfig)) {
            member.keyConfig = AmeGroupMemberInfo.KeyConfig.fromJson(dbUser.profileKeyConfig)
        }

        return member
    }

    fun transToModelList(members: List<GroupMember>): ArrayList<AmeGroupMemberInfo> {
        return ArrayList(members.map { transToModel(it) })
    }

    fun transToDb(member: AmeGroupMemberInfo): GroupMember {
        val dbUser = GroupMember()
        dbUser.joinTime = member.createTime
        dbUser.gid = member.gid
        dbUser.role = member.role
        dbUser.uid = member.uid
        dbUser.customNickname = member.customNickname?:""
        dbUser.nickname = member.nickname?:""
        dbUser.profileKeyConfig = member.keyConfig?.toString()?:""
        return dbUser
    }

    fun transToDbList(memberList:List<AmeGroupMemberInfo>): List<GroupMember> {
        return memberList.map { transToDb(it) }
    }
}
