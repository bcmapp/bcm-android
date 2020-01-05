package com.bcm.messenger.chats.group.core

import android.text.TextUtils
import com.bcm.messenger.chats.group.core.group.*
import com.bcm.messenger.chats.group.logic.bean.BcmGroupReviewAccept
import com.bcm.messenger.chats.group.logic.secure.GroupKeysContent
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.BcmHttpApiHelper
import com.bcm.messenger.common.core.ServerResult
import com.bcm.messenger.common.core.corebean.GroupKeyMode
import com.bcm.messenger.common.core.corebean.IdentityKeyInfo
import com.bcm.messenger.common.core.corebean.ProfilesResult
import com.bcm.messenger.common.grouprepository.room.entity.JoinGroupReqComment
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.bcmhttp.facade.AmeEmpty
import com.bcm.messenger.common.bcmhttp.RxIMHttp
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.proguard.NotGuard
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import io.reactivex.Observable
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.*


/**
 * ling created in 2018/5/23
 */
object GroupManagerCore {


    fun offlineMessageState(accountContext: AccountContext): Observable<ServerResult<GroupLastMidEntity>> {
        return RxIMHttp.getHttp(accountContext).put(BcmHttpApiHelper.getApi(GroupCoreConstants.QUERY_OFFLINE_MSG_STATE),
                null, "{}", object : TypeToken<ServerResult<GroupLastMidEntity>>() {
        }.type)
    }


    fun createGroupV3(accountContext: AccountContext, req: CreateGroupRequest): Observable<CreateGroupResult> {
        req.groupKeyMode = GroupKeyMode.STRONG_MODE.m
        return RxIMHttp.getHttp(accountContext).put(BcmHttpApiHelper.getApi(GroupCoreConstants.CREATE_GROUP_URL_V3),
                null, GsonUtils.toJson(req), CreateGroupResult::class.java)
    }


    fun createGroupV2(accountContext: AccountContext,
                      name: String, icon: String,
                      broadcast: Int, intro: String,
                      members: List<String>,
                      memberKeys: List<String>?,
                      memberSecrets: List<String>?,
                      ownerKey: String,
                      ownerInfoSecret: String,
                      nickname: String,
                      profileKeys: String,
                      shareSetting: String,
                      shareSettingSign: String,
                      shareConfirmSign: String): Observable<ServerResult<CreateGroupResult>> {
        val obj = JSONObject()
        try {
            obj.put("name", name)
            obj.put("icon", icon)
            obj.put("broadcast", broadcast)
            obj.put("members", JSONArray(members))
            obj.put("intro", intro)
            obj.put("share_qr_code_setting", shareSetting)
            obj.put("share_sig", shareSettingSign)
            obj.put("share_and_owner_confirm_sig", shareConfirmSign)
            obj.put("owner_confirm", 0)

            if (!TextUtils.isEmpty(ownerInfoSecret)) {
                obj.put("owner_group_info_secret", ownerInfoSecret)
            }

            if (memberSecrets !=
                    null && memberSecrets.size > 0) {
                obj.put("member_group_info_secrets", JSONArray(memberSecrets))
            }

            if (memberKeys != null && memberKeys.size > 0) {
                obj.put("member_keys", JSONArray(memberKeys))
            }

            if (!TextUtils.isEmpty(ownerKey)) {
                obj.put("owner_key", ownerKey)
            }

            if (!TextUtils.isEmpty(nickname)) {
                obj.put("owner_nickname", nickname)
            }

            if (!TextUtils.isEmpty(profileKeys)) {
                obj.put("owner_profile_keys", profileKeys)
            }

        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return RxIMHttp.getHttp(accountContext).put(BcmHttpApiHelper.getApi(GroupCoreConstants.CREATE_GROUP_URL_V2),
                null, obj.toString(), object : TypeToken<ServerResult<CreateGroupResult>>() {

        }.type)
    }


    data class GroupKeysReq(
            @SerializedName("gid")
            var gid: Long = 0,
            @SerializedName("versions")
            var versions: List<Long>? = null
    ) : NotGuard

    fun getGroupKeys(accountContext: AccountContext, gid: Long, keyVersions: List<Long>): Observable<GroupKeyResEntity> {
        val req = GroupKeysReq()
        req.gid = gid
        req.versions = keyVersions
        val reqJson = GsonUtils.toJson(req)

        return RxIMHttp.getHttp(accountContext).post<GroupKeyResEntity>(BcmHttpApiHelper.getApi(GroupCoreConstants.GET_GROUP_KEYS),
                reqJson, GroupKeyResEntity::class.java)
                .subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AmeDispatcher.ioScheduler)
                .map {
                    it.keys?.forEach { key ->
                        key.gid = gid
                    }
                    it
                }


    }


    data class LatestGroupKeysReq(
            @SerializedName("gids")
            val gids: List<Long>
    ) : NotGuard

    fun getGroupLatestKeys(accountContext: AccountContext, gidList: List<Long>): Observable<LatestGroupKeyResEntity> {
        val req = LatestGroupKeysReq(gidList)
        val reqJson = GsonUtils.toJson(req)
        return RxIMHttp.getHttp(accountContext).post(BcmHttpApiHelper.getApi(GroupCoreConstants.GROUP_LATEST_GROUP_KEY),
                reqJson, LatestGroupKeyResEntity::class.java)
    }


    class RefreshGroupKeysReq : NotGuard {
        @SerializedName("gids")
        var gidList: List<Long>? = null
    }

    fun refreshGroupKeys(accountContext: AccountContext, gidList: List<Long>): Observable<RefreshKeyResEntity> {
        val req = RefreshGroupKeysReq()
        req.gidList = gidList
        return RxIMHttp.getHttp(accountContext).post(BcmHttpApiHelper.getApi(GroupCoreConstants.REFRESH_GROUP_KES),
                GsonUtils.toJson(req), RefreshKeyResEntity::class.java)

    }

    class UploadGroupKeysReq : NotGuard {
        @SerializedName("gid")
        var gid: Long = 0
        @SerializedName("version")
        var version: Long = 0
        @SerializedName("group_keys")
        var keys: GroupKeysContent? = null
        @SerializedName("group_keys_mode")
        var keyMode: Int = 0
    }

    fun uploadGroupKeys(accountContext: AccountContext, gid: Long, version: Long, keys: GroupKeysContent, keyMode: GroupKeyMode): Observable<AmeEmpty> {
        val req = UploadGroupKeysReq()
        req.gid = gid
        req.version = version
        req.keys = keys
        req.keyMode = keyMode.m
        return RxIMHttp.getHttp(accountContext).put(BcmHttpApiHelper.getApi(GroupCoreConstants.UPLOAD_GROUP_KES), null,
                GsonUtils.toJson(req), AmeEmpty::class.java)

    }

    class PrepareUploadGroupKeysReq : NotGuard {
        @SerializedName("gid")
        var gid: Long = 0
        @SerializedName("version")
        var version: Long = 0
        @SerializedName("mode")
        var mode: Int = 0
    }

    fun prepareUploadGroupKeys(accountContext: AccountContext, gid: Long, version: Long, mode: Int): Observable<PreKeyBundleListEntity> {
        val req = PrepareUploadGroupKeysReq()
        req.gid = gid
        req.version = version
        req.mode = mode
        return RxIMHttp.getHttp(accountContext).post(BcmHttpApiHelper.getApi(GroupCoreConstants.PREPARE_UPLOAD_GROUP_KES),
                GsonUtils.toJson(req), PreKeyBundleListEntity::class.java)

    }

    fun queryRecipientsInfo(accountContext: AccountContext, members: List<String>): Observable<List<IdentityKeyInfo>> {
        val obj = JSONObject()
        try {
            obj.put("contacts", JSONArray(members))
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return RxIMHttp.getHttp(accountContext).put<ProfilesResult>(BcmHttpApiHelper.getApi(GroupCoreConstants.GET_GROUP_MEMBER_PROFILE_URL),
                null, obj.toString(), ProfilesResult::class.java)
                .map { inviteList ->
                    val list = ArrayList<IdentityKeyInfo>()
                    for (item in inviteList.profiles.entrySet()) {
                        val v = item.value
                        val uid = item.key
                        val tmp = Gson().fromJson(v, IdentityKeyInfo::class.java)

                        val keyInfo = IdentityKeyInfo(uid, tmp.identityKey, tmp.features)
                        if (keyInfo.isValid()) {
                            list.add(keyInfo)
                        } else {
                            ALog.e("GroupManagerApi", "queryRecipientsInfo failed: $uid")
                        }
                    }
                    list
                }
    }


    fun updateGroup(accountContext: AccountContext, gid: Long, name: String?, icon: String?): Observable<ServerResult<AmeEmpty>> {
        val obj = JSONObject()
        try {
            obj.put("gid", gid)
            if (name != null) {
                obj.put("encrypted_name", name)
            }
            if (icon != null) {
                obj.put("icon", icon)
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return RxIMHttp.getHttp(accountContext).put(BcmHttpApiHelper.getApi(GroupCoreConstants.UPDATE_GROUP_URL_V3),
                null, obj.toString(), object : TypeToken<ServerResult<AmeEmpty>>() {

        }.type)
    }

    fun setEncryptedGroupProfile(accountContext: AccountContext, gid: Long, name: String?, noticeContent: String?): Observable<ServerResult<AmeEmpty>> {
        val obj = JSONObject()
        try {
            obj.put("gid", gid)
            if (name != null) {
                obj.put("encrypted_name", name)
            }
            if (noticeContent != null) {
                obj.put("encrypted_notice", noticeContent)
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return RxIMHttp.getHttp(accountContext).put(BcmHttpApiHelper.getApi(GroupCoreConstants.UPDATE_GROUP_URL_V3),
                null, obj.toString(), object : TypeToken<ServerResult<AmeEmpty>>() {

        }.type)
    }

    fun getGroupInfo(accountContext: AccountContext, gid: Long): Observable<ServerResult<GroupInfoEntity>> {
        val obj = JSONObject()
        try {
            obj.put("gid", gid)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return RxIMHttp.getHttp(accountContext).put(BcmHttpApiHelper.getApi(GroupCoreConstants.GET_GROUP_INFO_URL),
                null, obj.toString(), object : TypeToken<ServerResult<GroupInfoEntity>>() {
        }.type)
    }

    fun getGroupInfoByGids(accountContext: AccountContext, gids: List<Long>): Observable<ServerResult<GroupInfoListEntity>> {
        val req = QueryInfoBatchReq()
        req.gids = gids
        return RxIMHttp.getHttp(accountContext).post(BcmHttpApiHelper.getApi(GroupCoreConstants.QUERY_GROUP_INFO_BATCH),
                Gson().toJson(req), object : TypeToken<ServerResult<GroupInfoListEntity>>() {}.type)
    }


    fun leaveGroup(accountContext: AccountContext, gid: Long, isNewGroup: Boolean, uid: String?): Observable<AmeEmpty> {
        val obj = JSONObject()
        try {
            obj.put("gid", gid)
            if (!TextUtils.isEmpty(uid)) {
                obj.put("next_owner", uid)
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        val uri = if (isNewGroup) {
            GroupCoreConstants.LEAVE_GROUP_URL_V3
        } else {
            GroupCoreConstants.LEAVE_GROUP_URL
        }

        return RxIMHttp.getHttp(accountContext).put(BcmHttpApiHelper.getApi(uri),
                null, obj.toString(), object : TypeToken<AmeEmpty>() {}.type)
    }


    fun updateMyNameAndMuteSetting(accountContext: AccountContext, gid: Long, mute: Boolean?,
                                   nickname: String?, customNickname: String?, profileKey: String?): Observable<ServerResult<Void>> {
        val obj = JSONObject()
        try {
            obj.put("gid", gid)
            if (!TextUtils.isEmpty(nickname)) {
                obj.put("nickname", nickname)
            }

            if (!TextUtils.isEmpty(customNickname)) {
                obj.put("group_nickname", customNickname)
            }

            if (!TextUtils.isEmpty(profileKey)) {
                obj.put("profile_keys", profileKey)
            }

            if (null != mute) {
                obj.put("mute", (if (mute) 1 else 0))
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return RxIMHttp.getHttp(accountContext).put(BcmHttpApiHelper.getApi(GroupCoreConstants.SEND_GROUP_UPDATE_USER),
                null, obj.toString(), object : TypeToken<ServerResult<Void>>() {}.type)
    }

    fun queryPendingList(accountContext: AccountContext, gid: Long, fromUid: String, count: Long): Observable<List<GroupJoinPendingUserEntity>> {
        val obj = JSONObject()
        try {
            obj.put("gid", gid)
            obj.put("start", fromUid)
            obj.put("count", count)

        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return RxIMHttp.getHttp(accountContext).post<ServerResult<GroupJoinPendingListRes>>(BcmHttpApiHelper.getApi(GroupCoreConstants.QUERY_GROUP_PENDING_LIST),
                obj.toString(), object : TypeToken<ServerResult<GroupJoinPendingListRes>>() {
        }.type).subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AmeDispatcher.ioScheduler)
                .map { result ->
                    if (result.data != null && result.data.list != null) {
                        result.data.list
                    } else {
                        ArrayList()
                    }
                }
    }


    fun updateGroupNotice(accountContext: AccountContext, gid: Long, noticeContent: String): Observable<AmeEmpty> {
        val obj = JSONObject()
        try {
            obj.put("gid", gid)
            if (!TextUtils.isEmpty(noticeContent)) {
                obj.put("encrypted_notice", noticeContent)
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return RxIMHttp.getHttp(accountContext).put(BcmHttpApiHelper.getApi(GroupCoreConstants.UPDATE_GROUP_URL_V3)
                ,obj.toString(),AmeEmpty::class.java)
    }

    fun reviewJoinRequestByOwner(accountContext: AccountContext, gid: Long, reviewList: List<BcmGroupReviewAccept>, newGroup: Boolean): Observable<AmeEmpty> {
        val req = ReviewJoinGroupRequest()
        req.gid = gid
        req.list = reviewList
        req.sig = ""

        val uri = if (newGroup) {
            GroupCoreConstants.JOIN_GROUP_REVIEW_V3
        } else {
            GroupCoreConstants.JOIN_GROUP_REVIEW
        }

        return RxIMHttp.getHttp(accountContext).put(BcmHttpApiHelper.getApi(uri), null, GsonUtils.toJson(req), object : TypeToken<AmeEmpty>() {

        }.type)
    }

    fun autoReviewJoinRequest(accountContext: AccountContext, gid: Long, reviewList: List<BcmGroupReviewAccept>): Observable<AmeEmpty> {
        val req = ReviewJoinGroupRequest()
        req.gid = gid
        req.list = reviewList
        req.sig = ""

        return RxIMHttp.getHttp(accountContext).put(BcmHttpApiHelper.getApi(GroupCoreConstants.JOIN_GROUP_UPLOAD_PSW),
                null, GsonUtils.toJson(req), object : TypeToken<AmeEmpty>() {

        }.type)
    }


    class JoinGroupByCodeRes : NotGuard {
        @SerializedName("encrypted_group_info_secret")
        var encInfoSecret: String? = null
        @SerializedName("owner_confirm")
        var confirm: Boolean = false
    }

    fun joinGroupByCode(accountContext: AccountContext, gid: Long, shareCode: String, shareSign: String, comment: String, newGroup: Boolean): Observable<String> {
        val req = JoinGroupReq()
        try {
            req.gid = gid
            req.qr_token = shareSign
            req.qr_code = shareCode

            val myName = Recipient.fromSelf(AppContextHolder.APP_CONTEXT, true).name
            val reqComment = JoinGroupReqComment(myName, comment)
            req.comment = String(EncryptUtils.base64Encode(GsonUtils.toJson(reqComment).toByteArray()))

            val format = ByteArrayOutputStream()
            format.write(EncryptUtils.base64Decode(shareCode.toByteArray()))
            format.write(EncryptUtils.base64Decode(shareSign.toByteArray()))
            val signData = BCMEncryptUtils.signWithMe(AppContextHolder.APP_CONTEXT, format.toByteArray())

            req.signature = String(EncryptUtils.base64Encode(signData))
        } catch (e: Throwable) {
            ALog.e("GroupManageApi", "joinGroupByCode failed", e)
        }

        if (newGroup) {
            return RxIMHttp.getHttp(accountContext).put<JoinGroupByCodeRes>(BcmHttpApiHelper.getApi(GroupCoreConstants.JOIN_GROUP_BY_CODE_V3),
                    null, GsonUtils.toJson(req), JoinGroupByCodeRes::class.java)
                    .subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AmeDispatcher.ioScheduler)
                    .map {
                        if (!it.confirm) {
                            if (it.encInfoSecret.isNullOrEmpty()) {
                                throw Exception("join failed")
                            } else {
                                it.encInfoSecret
                            }
                        } else {
                            ""
                        }
                    }
        } else {
            return RxIMHttp.getHttp(accountContext).put<AmeEmpty>(BcmHttpApiHelper.getApi(GroupCoreConstants.JOIN_GROUP_BY_CODE),
                    null, GsonUtils.toJson(req), AmeEmpty::class.java)
                    .subscribeOn(AmeDispatcher.ioScheduler)
                    .observeOn(AmeDispatcher.ioScheduler)
                    .map { "" }
        }
    }


    class AddMeReq : NotGuard {
        @SerializedName("gid")
        var gid: Long = 0
        @SerializedName("group_info_secret")
        var infoSecret: String? = null
        @SerializedName("proof")
        var proof: String? = null
    }

    fun addMe(accountContext: AccountContext, gid: Long, infoSecret: String, proof: String): Observable<AmeEmpty> {
        val req = AddMeReq()
        req.gid = gid
        req.infoSecret = infoSecret
        req.proof = proof

        return RxIMHttp.getHttp(accountContext).put(BcmHttpApiHelper.getApi(GroupCoreConstants.GROUP_ADD_ME),
                null, GsonUtils.toJson(req), AmeEmpty::class.java)
    }

    fun checkQrCodeValid(accountContext: AccountContext, gid: Long, shareSign: String): Observable<Boolean> {
        val req = CheckQrCodeValidReq()
        try {
            req.gid = gid
            req.signature = shareSign
        } catch (e: Throwable) {
            ALog.e("GroupManageApi", "joinGroupByCode failed", e)
        }


        return RxIMHttp.getHttp(accountContext).post<ServerResult<CheckQrCodeValidRes>>(BcmHttpApiHelper.getApi(GroupCoreConstants.CHECK_QR_CODE_VALID),
                GsonUtils.toJson(req), object : TypeToken<ServerResult<CheckQrCodeValidRes>>() {

        }.type).map { result -> result.isSuccess && result.data.valid }
    }


    fun updateJoinConfirmSetting(accountContext: AccountContext, gid: Long, needConfirm: Int, shareConfirmSign: String): Observable<AmeEmpty> {
        val obj = JSONObject()
        try {
            obj.put("gid", gid)
            obj.put("owner_confirm", needConfirm)
            obj.put("share_and_owner_confirm_sig", shareConfirmSign)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return RxIMHttp.getHttp(accountContext).put(BcmHttpApiHelper.getApi(GroupCoreConstants.UPDATE_GROUP_URL_V3),
                null, obj.toString(), object : TypeToken<AmeEmpty>() {}.type)
    }

    fun updateShareCodeSetting(accountContext: AccountContext, gid: Long
                               , needConfirm: Int
                               , shareSetting: String
                               , shareSign: String
                               , shareConfirmSign: String
                               , encEphemeralKey: String
                               , encInfoSecret: String
                               , newGroup: Boolean): Observable<AmeEmpty> {
        val obj = JSONObject()
        try {
            obj.put("gid", gid)
            obj.put("owner_confirm", needConfirm)
            obj.put("share_and_owner_confirm_sig", shareConfirmSign)
            obj.put("share_qr_code_setting", shareSetting)
            obj.put("share_sig", shareSign)

            if (newGroup) {
                obj.put("encrypted_group_info_secret", encInfoSecret)
                obj.put("encrypted_ephemeral_key", encEphemeralKey)
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        val uri = if (newGroup) {
            GroupCoreConstants.UPDATE_GROUP_URL_V3
        } else {
            GroupCoreConstants.UPDATE_GROUP_URL_V2
        }
        return RxIMHttp.getHttp(accountContext).put(BcmHttpApiHelper.getApi(uri), null, obj.toString(), object : TypeToken<AmeEmpty>() {

        }.type)
    }


    fun checkJoinGroupNeedConfirm(accountContext: AccountContext, gid: Long): Observable<Boolean> {
        val obj = JSONObject()
        try {
            obj.put("gid", gid)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return RxIMHttp.getHttp(accountContext).post<ServerResult<CheckJoinGroupNeedConfirmRes>>(BcmHttpApiHelper.getApi(GroupCoreConstants.CHECK_OWNER_CONFIRM_STATE),
                obj.toString(), object : TypeToken<ServerResult<CheckJoinGroupNeedConfirmRes>>() {
        }.type).subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AmeDispatcher.ioScheduler)
                .map { result ->
                    result.isSuccess && result.data.owner_confirm == 1
                }
    }

    fun createGroupShareShortUrl(accountContext: AccountContext, shareJson: String): Observable<String> {
        val obj = JSONObject()
        try {
            obj.put("content", shareJson)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return RxIMHttp.getHttp(accountContext).put<GroupShortShareIndex>(BcmHttpApiHelper.getApi(GroupCoreConstants.GROUP_SHORT_SHARE),
                obj.toString(), object : TypeToken<GroupShortShareIndex>() {

        }.type).map { result -> result.index ?: "" }
    }


    private class QueryInfoBatchReq : NotGuard {
        internal var gids: List<Long>? = null
    }


    private class JoinGroupReq : NotGuard {
        internal var gid: Long = 0
        internal var qr_code: String? = null
        internal var qr_token: String? = null
        internal var signature: String? = null
        internal var comment: String? = null
    }

    private class ReviewJoinGroupRequest : NotGuard {
        internal var gid: Long = 0
        internal var list: List<BcmGroupReviewAccept>? = null
        internal var sig: String? = null
    }

    private class CheckJoinGroupNeedConfirmRes : NotGuard {
        internal var owner_confirm: Int = 0
    }

    private class CheckQrCodeValidReq : NotGuard {
        internal var gid: Long = 0
        internal var signature: String? = null
    }

    private class CheckQrCodeValidRes : NotGuard {
        internal var valid: Boolean = false
    }

    private class GroupJoinPendingListRes : NotGuard {
        internal var list: List<GroupJoinPendingUserEntity>? = null
    }

    private class GroupShortShareIndex : NotGuard {
        internal var index: String? = null
    }
}
