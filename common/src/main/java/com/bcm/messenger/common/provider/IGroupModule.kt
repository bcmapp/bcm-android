package com.bcm.messenger.common.provider

import android.content.Context
import com.bcm.messenger.common.core.corebean.AmeGroupInfo
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.route.api.IRouteProvider


/**
 * 
 * Created by zjl on 2018/3/14.
 */
interface IGroupModule : IAmeModule {
    /**
     * (，，)
     */
    fun doGroupJoin(context: Context, gid: Long, name: String?, icon: String?, code: String, signature: String, timestamp: Long, eKey:ByteArray?, callback: ((success: Boolean) -> Unit)? = null)

    /**
     * 
     */
    fun getJoinedList(): List<AmeGroupInfo>

    /**
     * （，）
     */
    fun getJoinedListBySort(): List<AmeGroupInfo>

    /**
     * 
     * @param groupId ID
     */
    fun getGroupInfo(groupId: Long): AmeGroupInfo?

    /**
     * 
     * @param groupId ID
     * @param result 
     */
    fun queryGroupInfo(groupId: Long, result: (groupInfo: AmeGroupInfo?) -> Unit)

    /**
     * 
     * @param groupId ID
     * @param uid UID
     */
    fun getMember(groupId: Long, uid:String) :AmeGroupMemberInfo?

    /**
     * 
     * @param groupId ID
     * @param uidList UID
     */
    fun getMembers(groupId: Long, uidList:List<String>, result:(List<AmeGroupMemberInfo>)->Unit)

    /**
     * 
     * @param groupId ID
     * @param uidList UID
     */
    fun getMembersFromCache(groupId: Long):List<String>

    /**
     * 
     * @param groupId ID
     * @param uid ID
     * @param result 
     */
    fun queryMember(groupId: Long, uid:String, result: (memberInfo: AmeGroupMemberInfo?) -> Unit)

    /**
     * 
     */
    fun doOnLogin()

    /**
     * N
     * @param groupId ID
     * @return N
     */
    fun getAvatarMemberList(groupId: Long, result:(gid:Long, list:List<AmeGroupMemberInfo>)->Unit)

    fun refreshGroupNameAndAvatar(gid: Long)
}