package com.bcm.messenger.chats.group.logic.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.chats.group.logic.GroupMessageLogic
import com.bcm.messenger.chats.group.logic.IGroupListener
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.corebean.*
import com.bcm.messenger.common.database.repositories.DraftRepo
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.manager.GroupInfoDataManager
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.grouprepository.room.entity.GroupInfo
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.orhanobut.logger.Logger
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import java.lang.ref.WeakReference
import kotlin.math.max

/**
 * group view model
 * Created by bcm.social.01 on 2018/5/30.
 */
@SuppressLint("CheckResult")
class GroupViewModel(private val groupId: Long) : IGroupListener {
    private val TAG = "GroupViewModel"
    private var threadId = -1L
    private var modelCache: GroupModelCache

    init {
        var info = GroupLogic.getGroupInfo(groupId)
        if (null == info) {
            info = AmeGroupInfo(groupId)
            info.memberSyncState = GroupMemberSyncState.DIRTY
        }

        modelCache = GroupModelCache(info) {
            ALog.i(TAG, "init $groupId")
            post(GroupInfoChangedEvent(groupId))
            checkSync()

            if (info.memberSyncState == GroupMemberSyncState.DIRTY || info.memberCount == 0) {
                GroupLogic.queryGroupInfo(groupId, null)
            }
        }
    }

    fun destroy() {
        GroupLogic.cancelSyncGroupMemberList(groupId)
    }

    fun groupId(): Long {
        return groupId
    }

    fun groupName(): String? {
        return modelCache.info.displayName
    }

    fun threadId(): Long {
        return threadId
    }

    fun setThreadId(threadId: Long) {
        this.threadId = threadId
        if (threadId <= 0L) {
            this.threadId = GroupLogic.getThreadId(Recipient.recipientFromNewGroupIdAsync(AppContextHolder.APP_CONTEXT, groupId))
        }
    }

    private fun post(event: Any) {
        AmeDispatcher.mainThread.dispatch {
            EventBus.getDefault().post(event)
        }
    }

    fun checkSync() {
        ALog.i(TAG, "checkSync $groupId")

        if (!modelCache.isCacheReady) {
            return
        }

        checkAndSyncGroupMemberList()

        if (modelCache.myRole == AmeGroupMemberInfo.OWNER && !modelCache.info.isProfileEncrypted) {
            uploadEncryptedNameAndNotice()
        }

        syncMyMemberInfo()
    }

    private fun checkAndSyncGroupMemberList() {
        ALog.i(TAG, "checkAndSyncGroupMemberList with role ${modelCache.myRole}")

        if (modelCache.myRole == AmeGroupMemberInfo.VISITOR) {
            return
        }

        val weakThis = WeakReference(this)
        GroupLogic.checkAndSyncGroupMemberList(groupId) { isFirstPage, allFinished ->
            if (isFirstPage || allFinished) {
                modelCache.reloadMemberList {
                    weakThis.get()?.post(MemberListChangedEvent())
                }
            }
        }

        GroupMessageLogic.syncJoinReqMessage(groupId)
    }

    private fun uploadEncryptedNameAndNotice() {
        GroupLogic.uploadEncryptedNameAndNotice(groupId) {
            if (it) {
                modelCache.info.isProfileEncrypted = true
            }
        }
    }

    fun refreshGroupAvatar() {
        GroupLogic.refreshGroupAvatar(groupId)
    }

    fun getGroupInfo(): AmeGroupInfo {
        return modelCache.info
    }

    private fun queryGroupInfo(forceUpdate: Boolean, callback: (groupInfo: AmeGroupInfo?) -> Unit) {
        if (!forceUpdate) {
            val groupInfo = GroupLogic.getGroupInfo(groupId)
            if (null != groupInfo) {
                callback(groupInfo)
                return
            }
        }

        val weakThis = WeakReference(this)
        GroupLogic.queryGroupInfo(groupId) { groupInfo, _, _ ->
            callback(groupInfo)
            weakThis.get()?.post(GroupInfoChangedEvent(groupId))
        }
    }

    fun leaveGroup(newOwner: String?, callback: (succeed: Boolean, error: String) -> Unit) {
        if (modelCache.info.shareEnable == true && myRole() == AmeGroupMemberInfo.OWNER) {
            disableShareGroup { succeed, error ->
                if (succeed) {
                    GroupLogic.leaveGroup(groupId, newOwner) { succeed, error ->
                        callback(succeed, error)
                    }
                } else {
                    callback(succeed, error ?: "Error")
                }
            }
        } else {
            GroupLogic.leaveGroup(groupId, newOwner) { succeed, error ->
                callback(succeed, error)
            }
        }
    }

    fun updateGroupPinMid(mid: Long, callback: (succeed: Boolean) -> Unit) {
        Observable.create<Boolean> {
            GroupLogic.updateGroupInfoPinMid(groupId, mid)
            it.onNext(true)
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback.invoke(it)
                }, {
                    callback.invoke(false)
                })

    }

    private fun syncMyMemberInfo() {
        GroupLogic.getGroupMemberInfo(groupId, AMESelfData.uid) { member, error ->
            if (member != null) {
                try {
                    if (member.uid.serialize() == AMESelfData.uid) {
                        val selfProfile = Recipient.self().privacyProfile
                        var newName: String? = null
                        if (member.nickname != selfProfile.name) {
                            newName = selfProfile.name
                        }

                        var newKeyConfig: AmeGroupMemberInfo.KeyConfig? = null
                        val selfKeyConfig = AmeGroupMemberInfo.KeyConfig()
                        selfKeyConfig.version = selfProfile.version
                        selfKeyConfig.avatarKey = selfProfile.avatarKey

                        if (member.keyConfig != selfKeyConfig && !TextUtils.isEmpty(selfKeyConfig.avatarKey)) {
                            newKeyConfig = selfKeyConfig
                        }

                        if (newName == null && newKeyConfig == null) {
                            ALog.i(TAG, "syncMyMemberInfo no changed")
                            return@getGroupMemberInfo
                        }

                        GroupLogic.updateMyMemberInfo(groupId, newName, null, newKeyConfig) { succeed, _ ->
                            if (succeed) {
                                modelCache.updateMyInfo(newName, null, newKeyConfig)
                                AmeDispatcher.mainThread.dispatch {
                                    post(MemberListChangedEvent())
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    ALog.e(TAG, "syncMyMemberInfo", e)
                }
            }

        }
    }

    @SuppressLint("CheckResult")
    private fun retrieveMemberInfo(memberList: List<AmeGroupMemberInfo>) {
        Observable.create(ObservableOnSubscribe<List<Recipient>> {
            try {
                it.onNext(memberList.map {
                    if (it.uid == null) {
                        null
                    } else {
                        Recipient.from(AppContextHolder.APP_CONTEXT, it.uid, false)
                    }
                }.filterNotNull())

            } catch (ex: Throwable) {
                it.onError(ex)
            } finally {
                it.onComplete()
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    Logger.d("RetrieveMemberInfo target list size: ${it.size}")
                }, {
                    Logger.e(it, "retrieveMemberInfo error")
                })

    }

    fun getGroupControlMemberList(): ArrayList<AmeGroupMemberInfo> {
        val ctrlList = ArrayList<AmeGroupMemberInfo>()
        var memberCount = 10

        if (!TextUtils.isEmpty(modelCache.info?.key)) {
            when (myRole()) {
                AmeGroupMemberInfo.OWNER -> {
                    memberCount = 8
                    ctrlList.add(AmeGroupMemberInfo.MEMBER_ADD_MEMBER)
                    ctrlList.add(AmeGroupMemberInfo.MEMBER_REMOVE)
                }
                AmeGroupMemberInfo.MEMBER -> {
                    memberCount = 9
                    ctrlList.add(AmeGroupMemberInfo.MEMBER_ADD_MEMBER)
                }
                else -> {
                }
            }
        }
        val merge = ArrayList<AmeGroupMemberInfo>()

        val memberList = modelCache.getMemberList()
        if (memberList.size > 0) {
            merge.addAll(memberList.subList(0, Math.min(memberCount, memberList.size)))
        }
        merge.addAll(ctrlList)

        retrieveMemberInfo(merge)

        return merge
    }

    fun updateGroupAvatar(avatar: String, result: (succeed: Boolean, error: String?) -> Unit): Boolean {
        val weakThis = WeakReference(this)
        return GroupLogic.updateGroupAvatar(groupId, avatar) { success, error ->
            weakThis.get()?.modelCache?.info?.iconUrl = avatar


            result(success, error)
            weakThis.get()?.post(GroupInfoChangedEvent(groupId))
        }
    }

    fun updateGroupName(name: String, result: (succeed: Boolean, error: String?) -> Unit) {
        val weakThis = WeakReference(this)
        return GroupLogic.updateGroupName(groupId, name) { succeed, error ->
            weakThis.get()?.modelCache?.info?.name = name

            result(succeed, error)
            weakThis.get()?.post(GroupInfoChangedEvent(groupId))
        }
    }

    fun getGroupMemberListWithRole(): ArrayList<AmeGroupMemberInfo> {
        val ctrlList = ArrayList<AmeGroupMemberInfo>()

        if (!TextUtils.isEmpty(modelCache.info.key)) {

            ctrlList.add(AmeGroupMemberInfo.MEMBER_SEARCH)
            ctrlList.add(AmeGroupMemberInfo.MEMBER_ADD_MEMBER)
        }

        val merge = ArrayList<AmeGroupMemberInfo>()
        merge.addAll(ctrlList)

        val memberList = modelCache.getMemberList()
        if (memberList.size > 0) {
            merge.addAll(memberList)
        }

        retrieveMemberInfo(merge)

        return merge
    }


    fun updateMember2Cache(members: List<AmeGroupMemberInfo>) {
        AmeDispatcher.mainThread.dispatch {
            modelCache.updateMemberInfoList(members)
            post(MemberListChangedEvent())
        }
    }

    fun getGroupMemberList(): List<AmeGroupMemberInfo> {
        return modelCache.getMemberList()
    }

    @SuppressLint("CheckResult")
    fun inviteMember(recipients: ArrayList<Recipient>, result: (succeed: Boolean, resultMessage: String?) -> Unit) {
        val weakThis = WeakReference(this)
        val memberList = recipients.map { it.address.serialize() }
        GroupLogic.inviteMember(groupId, memberList) { succeed, succeedList, resultMessage ->
            if (succeed) {
                val groupInfo = modelCache.info
                if (!groupInfo.needConfirm || myRole() == AmeGroupMemberInfo.OWNER) {
                    weakThis.get()?.onMemberJoin(groupId, succeedList?.toList() ?: listOf())
                }
                result(succeed, resultMessage)
            } else {
                result(succeed, resultMessage)
            }
        }
    }


    fun randomGetGroupMember(): AmeGroupMemberInfo? {
        return modelCache.getMemberWithoutSelf()
    }

    fun getGroupMember(uid: String): AmeGroupMemberInfo? {
        return modelCache.getMember(uid)
    }

    fun queryGroupMember(uid: String, result: (member: AmeGroupMemberInfo?) -> Unit) {
        GroupLogic.getGroupMemberInfo(groupId, uid) { member, _ ->
            AmeDispatcher.mainThread.dispatch {
                result(member)
            }
        }
    }

    fun memberCount(): Int {
        if (modelCache.myRole == AmeGroupMemberInfo.VISITOR) {
            return modelCache.getMemberList().size
        }
        return modelCache.info?.memberCount ?: 0
    }

    fun myRole(): Long {
        return modelCache.myRole
    }

    fun mute(mute: Boolean, callback: (succeed: Boolean, error: String?) -> Unit) {
        GroupLogic.muteGroup(groupId, mute) { success, msg ->
            if (success) {
                modelCache.info.mute = mute
                post(GroupMuteEnableEvent(mute))
            }
            callback.invoke(success, msg)
        }

    }

    fun isMute(): Boolean {
        return modelCache.info.mute
    }

    fun upLoadNoticeContent(notice: String, timestamp: Long, result: (succeed: Boolean, error: String?) -> Unit) {
        val weakThis = WeakReference(this)
        GroupLogic.updateGroupNotice(groupId, notice, timestamp) { succeed, error ->
            AmeDispatcher.mainThread.dispatch {
                modelCache.info.noticeContent = notice

                result(succeed, error)
                weakThis.get()?.post(GroupInfoChangedEvent(groupId))
            }
        }
    }

    fun deleteMember(list: ArrayList<AmeGroupMemberInfo>, result: (succeed: Boolean, error: String?) -> Unit) {
        if (list.size > 0) {
            val weakThis = WeakReference(this)
            GroupLogic.deleteMember(groupId, list) { succeed, succeedList, error ->
                if (succeed) {
                    weakThis.get()?.onMemberLeave(groupId, succeedList ?: listOf())
                }
                result(succeed, error)
            }
        }
    }

    fun saveDrafts(context: Context, drafts: DraftRepo.Drafts, callback: (success: Boolean) -> Unit) {
        Observable.create(ObservableOnSubscribe<Boolean> {
            val threadRepo = Repository.getThreadRepo()
            val draftRepo = Repository.getDraftRepo()
            if (drafts.size > 0) {
                draftRepo.insertDrafts(this.threadId, drafts)
                threadRepo.updateByNewGroup(groupId, System.currentTimeMillis())

            } else {
                draftRepo.clearDrafts(this.threadId)
                threadRepo.updateByNewGroup(groupId)
            }

            it.onNext(true)
            it.onComplete()
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback(it)
                }, {
                    ALog.e(TAG, "save draft fail", it)
                    callback(false)
                })
    }

    fun readAllMessage() {
        if (myRole() != AmeGroupMemberInfo.VISITOR) {
            GroupMessageLogic.readAllMessage(groupId, threadId, System.currentTimeMillis())
        }
    }

    fun fetchMessage(fromMid: Long, toMid: Long, callback: (result: List<AmeGroupMessageDetail>) -> Unit) {
        Observable.create(ObservableOnSubscribe<List<AmeGroupMessageDetail>> {
            try {
                val list = MessageDataManager.fetchMessageFromToMid(groupId, fromMid, toMid)
                it.onNext(list)
            } finally {
                it.onComplete()
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback.invoke(it)
                }, {
                    ALog.e(TAG, "fetchMessage error", it)
                    callback.invoke(listOf())
                })
    }

    fun fetchMessage(index: Long, count: Int, withUnread: Boolean, callback: (result: List<AmeGroupMessageDetail>, unread: Long) -> Unit) {
        Observable.create(ObservableOnSubscribe<Pair<List<AmeGroupMessageDetail>, Long>> {
            try {
                var unread = 0L
                val list = MessageDataManager.fetchMessageByGidAndIndexId(groupId, index, count)
                if (withUnread) {
                    unread = MessageDataManager.countUnreadCountFromLastSeen(groupId, 0)
                }
                it.onNext(Pair(list, unread))
            } finally {
                it.onComplete()
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback.invoke(it.first, it.second)
                }, {
                    ALog.e(TAG, "fetchMessage error", it)
                    callback.invoke(listOf(), 0L)
                })
    }

    fun getMessageDetailByMid(mid: Long, callback: (result: AmeGroupMessageDetail?) -> Unit) {
        Observable.create(ObservableOnSubscribe<AmeGroupMessageDetail> {
            try {
                val detail = MessageDataManager.getMessageByMid(groupId, mid)
                if (detail == null) {
                    it.onError(Exception("AmeGroupMessageDetail is null"))
                } else {
                    it.onNext(detail)
                }
            } finally {
                it.onComplete()
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback.invoke(it)
                }, {
                    ALog.e(TAG, "fetchMessageBefore error", it)
                    callback.invoke(null)
                })
    }

    fun enableShareGroup(result: (succeed: Boolean, error: String?) -> Unit) {
        GroupLogic.updateShareSetting(groupId, true) { succeed, shareCode, error ->
            if (succeed) {
                modelCache.updateShareSetting(true, shareCode)
            }
            post(GroupInfoChangedEvent(groupId))
            result(succeed, error)

        }
    }

    /**
     * disable share group
     * @param result succeed true , false failed, error failed reason
     */
    fun disableShareGroup(result: (succeed: Boolean, error: String?) -> Unit) {
        GroupLogic.updateShareSetting(groupId, false) { succeed, shareCode, error ->
            if (succeed) {
                modelCache.updateShareSetting(false, shareCode)
            }
            post(GroupInfoChangedEvent(groupId))
            result(succeed, error)
        }
    }

    /**
     * refresh
     * @param result succeed true, failed false, error failed reason
     */
    fun refreshShareData(result: (succeed: Boolean, error: String?) -> Unit) {
        enableShareGroup(result)
    }

    fun isShareGroupEnable(): Boolean {
        return modelCache.info?.shareEnable ?: false
    }

    @SuppressLint("CheckResult")
    fun getGroupShareData(groupId: Long, callback: (shareContent: AmeGroupMessage.GroupShareContent?) -> Unit) {
        Observable.create<GroupInfo> {
            val groupInfo = GroupInfoDataManager.queryOneGroupInfo(groupId)
                    ?: throw Exception("getGroupInfo null")
            it.onNext(groupInfo)
            it.onComplete()

        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ groupInfo ->
                    if (groupInfo.shareLink.isNullOrEmpty()) {
                        ALog.d(TAG, "getGroupShareData group share link not exist, to create new")
                        GroupLogic.createGroupShareShortUrl(groupId) { shareContent ->
                            val link = shareContent?.shareLink
                            callback.invoke(shareContent)
                        }

                    } else {
                        ALog.d(TAG, "getGroupShareData group share link is exist, just gen share QR: ${groupInfo.shareLink}")
                        val ekey = if (groupInfo.isNewGroup) {
                            groupInfo.ephemeralKey
                        } else {
                            null
                        }
                        val shareContent = AmeGroupMessage.GroupShareContent(groupId, groupInfo.name, groupInfo.iconUrl, groupInfo.shareCode
                                ?: "", groupInfo.shareCodeSettingSign
                                ?: "", ekey, System.currentTimeMillis(), groupInfo.shareLink)
                        callback.invoke(shareContent)
                    }

                }, {
                    ALog.e(TAG, "getGroupShareShortQR error", it)
                    callback.invoke(null)
                })
    }

    fun setNeedOwnerJoinConfirm(needConfirm: Boolean, result: (succeed: Boolean, error: String?) -> Unit) {
        GroupLogic.updateNeedConfirm(groupId, needConfirm) { succeed, error ->
            if (succeed) {
                modelCache.updateNeedConfirmSetting(needConfirm)
            }
            post(GroupInfoChangedEvent(groupId))
            result(succeed, error)

        }
    }

    fun isNeedOwnerJoinConfirm(): Boolean {
        return modelCache.info?.needConfirm ?: false
    }

    fun getJoinRequestList(): List<BcmGroupJoinRequest> {
        return modelCache.getJoinRequestList()
    }

    fun getJoinRequest(mid: Long): List<BcmGroupJoinRequest> {
        return modelCache.getJoinRequest(mid)
    }

    fun reviewJoinRequests(reviewList: List<BcmReviewGroupJoinRequest>, result: (succeed: Boolean, error: String?) -> Unit) {
        GroupLogic.reviewJoinRequest(groupId, reviewList) { succeed, error ->
            AmeDispatcher.mainThread.dispatch {
                refreshJoinRequestCache()
                result(succeed, error)
            }
        }
    }

    fun getJoinRequestCount(): Int {
        return modelCache.getJoinRequestList().size
    }

    fun getJoinRequestUnreadCount(): Int {
        return modelCache.getJoinRequestListUnreadCount()
    }

    fun readAllJoinRequest() {
        modelCache.readAllJoinRequest()
        post(JoinRequestListChanged(groupId))
    }

    fun readJoinRequest(requestList: List<BcmGroupJoinRequest>) {
        modelCache.readJoinRequests(requestList)
        post(JoinRequestListChanged(groupId))
    }

    fun updateNoticeShowState(noticeShowState: Boolean) {
        GroupLogic.updateNoticeShowState(groupId, noticeShowState)
    }

    fun refreshJoinRequestCache() {
        modelCache.refreshJoinRequestList {
            post(JoinRequestListChanged(groupId))
        }
    }

    override fun onGroupInfoChanged(newGroupInfo: AmeGroupInfo) {
        if (newGroupInfo.gid == groupId) {
            modelCache.updateGroupInfo(newGroupInfo)

            checkSync()
            GroupLogic.refreshGroupAvatar(groupId)

            AmeDispatcher.mainThread.dispatch {
                post(GroupInfoChangedEvent(groupId))
            }
        }
    }

    override fun onGroupAvatarChanged(gid: Long, newAvatar: String) {
        if (gid == groupId) {
            modelCache.info.iconUrl = newAvatar
        }
    }

    override fun onGroupNameChanged(gid: Long, newName: String) {
        if (gid == groupId) {
            modelCache.info.name = newName
            post(GroupInfoChangedEvent(groupId))
        }
    }

    override fun onMuteChanged(gid: Long, mute: Boolean) {
        if (gid == groupId) {
            modelCache.info.mute = mute
            AmeDispatcher.mainThread.dispatch {
                post(GroupMuteEnableEvent(mute))
            }
        }
    }

    override fun onMemberJoin(gid: Long, memberList: List<AmeGroupMemberInfo>) {
        if (gid == groupId && memberList.isNotEmpty()) {
            modelCache.addMember(memberList)
            modelCache.info.memberCount += memberList.count()
            val selfJoin = memberList.filter { it.uid.serialize() == AMESelfData.uid }
                    .takeIf {
                        it.isNotEmpty()
                    }?.first()


            post(MemberListChangedEvent())
            if (null != selfJoin) {
                post(MyRoleChangedEvent(selfJoin.role))
            }
        }

    }

    override fun onMemberUpdate(gid: Long, memberList: List<AmeGroupMemberInfo>) {
        if (gid == groupId && memberList.isNotEmpty()) {
            memberList.forEach {
                val member = modelCache.getMember(it.uid.serialize())
                member?.role = it.role
                if (it.nickname?.isNotEmpty() == true) {
                    member?.nickname = it.nickname
                }
                if (it.customNickname?.isNotEmpty() == true) {
                    member?.customNickname = it.customNickname
                }
            }
            post(MemberListChangedEvent())
        }
    }

    override fun onMemberLeave(gid: Long, memberList: List<AmeGroupMemberInfo>) {
        if (gid == groupId && memberList.isNotEmpty()) {
            modelCache.removeMember(memberList.map { it.uid.serialize() })
            modelCache.info.memberCount = max(1, modelCache.info.memberCount + memberList.count())

            post(MemberListChangedEvent())

            if (memberList.any { it.uid.serialize() == AMESelfData.uid }) {
                AmeDispatcher.mainThread.dispatch {
                    post(MyRoleChangedEvent(AmeGroupMemberInfo.VISITOR))
                }
            } else {
                queryGroupInfo(true) {
                    GroupLogic.refreshGroupAvatar(groupId)
                }
            }
        }
    }

    class GroupInfoChangedEvent(val groupId: Long)
    class GroupMuteEnableEvent(val enable: Boolean)
    class MemberListChangedEvent
    class MyRoleChangedEvent(val newRole: Long)
    class GroupChatAtEvent(val recipient: Recipient)
    class JoinRequestListChanged(val groupId: Long)

}