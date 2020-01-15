package com.bcm.messenger.chats.group.logic

import com.bcm.messenger.common.core.corebean.AmeGroupInfo
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo

interface IGroupListener {
    fun onGroupInfoChanged(newGroupInfo: AmeGroupInfo) {}
    fun onGroupNameChanged(gid:Long, newName: String) {}
    fun onGroupAvatarChanged(gid:Long, newAvatar: String) {}
    fun onMuteChanged(gid:Long, mute: Boolean) {}
    fun onMemberLeave(gid:Long, memberList:List<AmeGroupMemberInfo>){}
    fun onMemberUpdate(gid:Long, memberList:List<AmeGroupMemberInfo>){}
    fun onMemberJoin(gid:Long, memberList:List<AmeGroupMemberInfo>){}
    fun onGroupShareSettingChanged(gid: Long, shareEnable:Boolean, needConfirm:Boolean){}
    fun onGroupShareLinkChanged(gid: Long, link:String){}
}