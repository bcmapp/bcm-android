package com.bcm.messenger.common.provider

import android.content.Context
import com.bcm.messenger.common.core.corebean.AmeGroupInfo
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.route.api.IRouteProvider


/**
 * 群提供的服务
 * Created by zjl on 2018/3/14.
 */
interface IGroupModule : IAmeModule {
    /**
     * 处理群的分享二维码并执行相关跳转(里面会判断是否需要群主审核，如果已经在群，则直接跳转到群窗口)
     */
    fun doGroupJoin(context: Context, gid: Long, name: String?, icon: String?, code: String, signature: String, timestamp: Long, eKey:ByteArray?, callback: ((success: Boolean) -> Unit)? = null)

    /**
     * 获取我的群列表
     */
    fun getJoinedList(): List<AmeGroupInfo>

    /**
     * 获取已经排序好的我的群列表（可能等待一定耗时，请在子线程操作）
     */
    fun getJoinedListBySort(): List<AmeGroupInfo>

    /**
     * 获取群资料
     * @param groupId 群ID
     */
    fun getGroupInfo(groupId: Long): AmeGroupInfo?

    /**
     * 从服务器查询群资料
     * @param groupId 群ID
     * @param result 查询回调
     */
    fun queryGroupInfo(groupId: Long, result: (groupInfo: AmeGroupInfo?) -> Unit)

    /**
     * 获取群成员
     * @param groupId 群ID
     * @param uid 群成员UID
     */
    fun getMember(groupId: Long, uid:String) :AmeGroupMemberInfo?

    /**
     * 获取群成员
     * @param groupId 群ID
     * @param uidList 群成员UID列表
     */
    fun getMembers(groupId: Long, uidList:List<String>, result:(List<AmeGroupMemberInfo>)->Unit)

    /**
     * 获取群成员
     * @param groupId 群ID
     * @param uidList 群成员UID列表
     */
    fun getMembersFromCache(groupId: Long):List<String>

    /**
     * 从服务器查成员资料
     * @param groupId 群ID
     * @param uid 群成员ID
     * @param result 查询回调
     */
    fun queryMember(groupId: Long, uid:String, result: (memberInfo: AmeGroupMemberInfo?) -> Unit)

    /**
     * 帐号登录成功
     */
    fun doOnLogin()

    /**
     * 取出前N个群成员
     * @param groupId 群ID
     * @return 前N个群成员信息
     */
    fun getAvatarMemberList(groupId: Long, result:(gid:Long, list:List<AmeGroupMemberInfo>)->Unit)

    fun refreshGroupNameAndAvatar(gid: Long)
}