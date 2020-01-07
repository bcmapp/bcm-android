package com.bcm.messenger.chats.group.core


import com.bcm.messenger.chats.group.core.group.*
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.BcmHttpApiHelper
import com.bcm.messenger.common.core.ServerResult
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.bcmhttp.facade.AmeEmpty
import com.bcm.messenger.common.bcmhttp.RxIMHttp
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.proguard.NotGuard
import com.google.gson.reflect.TypeToken
import io.reactivex.Observable
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * ling created in 2018/5/23
 */
object GroupMemberCore {

    fun inviteMemberJoinGroup(accountContext: AccountContext, gid: Long
                              , members: List<String>
                              , memberKeys: List<String>?
                              , proofList: List<String>?
                              , memberSecrets: List<String>?
                              , signatures: List<String>?): Observable<ControlMemberResult> {
        val obj = JSONObject()
        try {
            obj.put("gid", gid)
            obj.put("members", JSONArray(members))

            if (proofList?.isNotEmpty() == true) {
                obj.put("member_proofs", JSONArray(proofList))
            }

            if (memberKeys?.isNotEmpty() == true) {
                obj.put("member_keys", JSONArray(memberKeys))
            }


            if (memberSecrets?.isNotEmpty() == true) {
                obj.put("group_info_secrets", JSONArray(memberSecrets))
            }

            if (signatures?.isNotEmpty() == true) {
                obj.put("signature", JSONArray(signatures))
            }

        } catch (e: JSONException) {
            e.printStackTrace()
        }

        val uri = if (proofList != null) {
            GroupCoreConstants.INVITE_MEMBER_TO_GROUP_URL_V3
        } else {
            GroupCoreConstants.INVITE_MEMBER_TO_GROUP_URL
        }
        return RxIMHttp.get(accountContext).put(BcmHttpApiHelper.getApi(uri), null, obj.toString(), object : TypeToken<ControlMemberResult>() {

        }.type)
    }

    private data class GroupMembersReq(val gid:Long, val uids:List<String>): NotGuard
    private data class GroupMembersRes(val members:List<GroupMemberListItemEntity>):NotGuard
    fun getGroupMembers(accountContext: AccountContext, gid: Long, uidList:List<String>): Observable<List<GroupMemberListItemEntity>> {
        val req = GroupMembersReq(gid, uidList)
        return RxIMHttp.get(accountContext).post<GroupMembersRes>(BcmHttpApiHelper.getApi(GroupCoreConstants.GET_GROUP_MEMBERS_URL), GsonUtils.toJson(req), GroupMembersRes::class.java)
                .subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AmeDispatcher.ioScheduler)
                .map {
                    it.members
                }
    }

    fun getGroupMemberByPage(accountContext: AccountContext, gid: Long, roles: List<Long>, fromUid: String?, createTime: Long, count: Long): Observable<ServerResult<GetGroupMemberListEntity>> {
        val obj = JSONObject()
        try {
            obj.put("gid", gid)
            obj.put("role", JSONArray(roles))
            obj.put("startUid", fromUid?:"")
            obj.put("createTime", createTime)
            obj.put("count", count)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return RxIMHttp.get(accountContext).post(BcmHttpApiHelper.getApi(GroupCoreConstants.QUERY_GROUP_MEMBER_PAGE),
                obj.toString(), object : TypeToken<ServerResult<GetGroupMemberListEntity>>() {

        }.type)
    }

    fun getGroupMemberInfo(accountContext: AccountContext, gid: Long, uid: String): Observable<GroupMemberEntity> {
        val obj = JSONObject()
        try {
            obj.put("gid", gid)
            obj.put("uid", uid)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return RxIMHttp.get(accountContext).put<ServerResult<GroupMemberEntity>>(BcmHttpApiHelper.getApi(GroupCoreConstants.GET_GROUP_MEMBER_URL), null, obj.toString(), object : TypeToken<ServerResult<GroupMemberEntity>>() {

        }.type).subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AmeDispatcher.ioScheduler)
                .map {
                    if (it.isSuccess) {
                        it.data
                    } else {
                        throw Exception(it.msg)
                    }
                }
    }

    fun kickGroupMember(accountContext: AccountContext, gid: Long, isNewGroup: Boolean?, members: List<String>): Observable<AmeEmpty> {
        val obj = JSONObject()
        try {
            obj.put("gid", gid)
            obj.put("members", JSONArray(members))
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        val uri: String
        if (isNewGroup!!) {
            uri = GroupCoreConstants.KICK_GROUP_MEMBER_URL_V3
        } else {
            uri = GroupCoreConstants.KICK_GROUP_MEMBER_URL
        }
        return RxIMHttp.get(accountContext).put(BcmHttpApiHelper.getApi(uri), null, obj.toString(), object : TypeToken<AmeEmpty>() {

        }.type)
    }


    fun getPreKeyBundles(accountContext: AccountContext, uidList: List<String>): Observable<PreKeyBundleListEntity> {
        val obj = JSONObject()
        try {
            obj.put("uids", JSONArray(uidList))
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return RxIMHttp.get(accountContext).post(BcmHttpApiHelper.getApi(GroupCoreConstants.GROUP_GET_PREKEY),
                obj.toString(), PreKeyBundleListEntity::class.java)
    }
}
