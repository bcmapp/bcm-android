package com.bcm.messenger.chats.group.logic.cache

import android.annotation.SuppressLint
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.common.core.corebean.AmeGroupInfo
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.core.corebean.GroupMemberSyncState
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.manager.GroupInfoDataManager
import com.bcm.messenger.common.grouprepository.manager.GroupLiveInfoManager
import com.bcm.messenger.common.grouprepository.manager.UserDataManager
import com.bcm.messenger.common.grouprepository.modeltransform.GroupInfoTransform
import com.bcm.messenger.common.grouprepository.room.entity.GroupInfo
import com.bcm.messenger.common.grouprepository.room.entity.GroupMember
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

/**
 * Created by bcm.social.01 on 2018/11/13.
 */
class GroupCache(val cacheReady: () -> Unit) {

    private val TAG = "GroupCache"

    private val otherGroupList: HashMap<Long, AmeGroupInfo> = HashMap()
    private val myGroupList: HashMap<Long, AmeGroupInfo> = HashMap()
    private var isCachedReady: Boolean = false

    @SuppressLint("CheckResult")
    fun init() {
        clearCache()

        //load from cache
        Observable.create(ObservableOnSubscribe<List<AmeGroupInfo>> {
            val list = GroupInfoDataManager.loadAll()
            for (info in list) {
                info.member_sync_state = GroupMemberSyncState.DIRTY.toString()
            }
            GroupInfoDataManager.saveGroupInfos(list)
            it.onNext(list.map { GroupInfoTransform.transformToModel(it) })
            it.onComplete()
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    it.map {
                        when (it.role) {
                            AmeGroupMemberInfo.ADMIN, AmeGroupMemberInfo.MEMBER, AmeGroupMemberInfo.OWNER -> {
                                myGroupList[it.gid] = it
                            }
                            else -> {
                                otherGroupList[it.gid] = it
                            }
                        }
                    }
                    isCachedReady = true
                    cacheReady()
                }, {
                    ALog.e(TAG, it)
                    isCachedReady = true
                    cacheReady()
                })
    }


    fun clearCache() {
        otherGroupList.clear()
        myGroupList.clear()
        isCachedReady = false

        GroupLogic.updateGroupFinderSource()
    }

    fun saveGroupInfo(groupInfo: GroupInfo) {
        val group = GroupInfoTransform.transformToModel(groupInfo)
        when (groupInfo.role) {
            AmeGroupMemberInfo.ADMIN, AmeGroupMemberInfo.MEMBER, AmeGroupMemberInfo.OWNER -> {
                myGroupList[group.gid] = group
                otherGroupList.remove(group.gid)
            }
            else -> {
                GroupInfoDataManager.clearShareSetting(groupInfo.gid)

                otherGroupList[group.gid] = group
                myGroupList.remove(group.gid)
            }
        }
        val recipient = Recipient.recipientFromNewGroup(AppContextHolder.APP_CONTEXT, group)
        Repository.getRecipientRepo()?.setProfile(recipient, null, group.name, group.iconUrl)

        GroupInfoDataManager.insertGroupInfo(groupInfo)
        GroupLogic.updateGroupFinderSource()
    }

    fun saveGroupInfos(groupList: List<GroupInfo>) {
        if (groupList.isEmpty()) {
            return
        }

        for (dbGroup in groupList) {
            val group = GroupInfoTransform.transformToModel(dbGroup)

            when (group.role) {
                AmeGroupMemberInfo.ADMIN, AmeGroupMemberInfo.MEMBER, AmeGroupMemberInfo.OWNER -> {
                    myGroupList[group.gid] = group
                    otherGroupList.remove(group.gid)
                }
                else -> {
                    dbGroup.shareCode = ""
                    dbGroup.shareCodeSettingSign = ""
                    dbGroup.shareCodeSetting = ""
                    dbGroup.shareSettingAndConfirmSign = ""
                    dbGroup.shareEpoch = 0
                    dbGroup.shareEnabled = 0
                    dbGroup.shareLink = ""
                    otherGroupList[group.gid] = group
                    myGroupList.remove(group.gid)
                }
            }

            val recipient = Recipient.recipientFromNewGroup(AppContextHolder.APP_CONTEXT, group)
            Repository.getRecipientRepo()?.setProfile(recipient, null, group.name, group.iconUrl)
        }

        GroupInfoDataManager.saveGroupInfos(groupList)
        GroupLogic.updateGroupFinderSource()
    }

    fun updateRole(gid: Long, role: Long) {
        val group = getGroupInfo(gid)?:return
        if (group.role != role) {
            group.role = role
            if (role == AmeGroupMemberInfo.VISITOR) {
                //已经不在群里了，清掉直播状态
                GroupLiveInfoManager.getInstance().deleteLiveInfoWhenLeaveGroup(gid)
                GroupInfoDataManager.clearShareSetting(group.gid)
            }
            GroupInfoDataManager.updateGroupRole(gid, role)
        }
    }

    fun getGroupInfo(gid: Long): AmeGroupInfo? {
        return myGroupList[gid] ?: otherGroupList[gid]
    }

    fun getGroupList(): List<Long> {
        return myGroupList.keys.toList()
    }

    fun getGroupInfoList(): List<AmeGroupInfo> {
        return myGroupList.values.toList()
    }

    fun removeGroupInfo(gid: Long) {
        myGroupList.remove(gid)
        otherGroupList.remove(gid)
        GroupInfoDataManager.removeGroupInfo(gid)
        GroupLogic.updateGroupFinderSource()
    }


    fun updateEnableNotify(groupId: Long, b: Boolean) {
        val group = getGroupInfo(groupId)?:return
        group.mute = !b

        val recipient = Recipient.recipientFromNewGroupId(AppContextHolder.APP_CONTEXT, groupId)
        if (!b) {
            Repository.getRecipientRepo()?.setMuted(recipient, System.currentTimeMillis() + TimeUnit.DAYS.toMillis(365 * 10))
        } else {
            Repository.getRecipientRepo()?.setMuted(recipient, 0)
        }
        GroupInfoDataManager.updateGroupNotificationEnable(groupId, b)
    }

    fun updateNotice(groupId: Long, noticeContent: String, timestamp: Long) {
        val group = getGroupInfo(groupId)?:return
        group.noticeContent = noticeContent
        group.noticeUpdateTime = timestamp
        GroupInfoDataManager.updateGroupNotice(groupId, noticeContent, timestamp)
    }

    fun updateNoticeShowState(groupId: Long, noticeShowState: Boolean) {
        val groupInfo = getGroupInfo(groupId)?:return
        groupInfo.isShowNotice = noticeShowState
        GroupInfoDataManager.updateNoticeShowState(groupId, noticeShowState)
    }

    fun updatePinState(groupId: Long, mid: Long) {
        GroupInfoDataManager.updateGroupPinMid(groupId, mid)
        val pinVisible = mid > 0
        GroupInfoDataManager.updateGroupPinVisible(groupId, pinVisible)

        val groupInfo = getGroupInfo(groupId)?:return
        groupInfo.hasPin = pinVisible
        groupInfo.pinMid = mid
    }

    fun setGroupMemberState(gid: Long, syncState: GroupMemberSyncState) {
        val group = getGroupInfo(gid)?:return
        group.memberSyncState = syncState
        GroupInfoDataManager.updateMemberSyncState(gid, syncState)
    }

    fun updateKey(gid: Long, version: Long, groupKey: String) {
        val groupInfo = getGroupInfo(gid)
        if (null != groupInfo) {
            groupInfo.key = groupKey
            GroupInfoDataManager.updateGroupKey(gid, version, groupKey)
        }
    }

    fun updateGroupInfoKey(gid: Long, groupInfoSecret: String) {
        val groupInfo = getGroupInfo(gid)
        if (null != groupInfo) {
            groupInfo.infoSecret = groupInfoSecret
            GroupInfoDataManager.updateGroupInfoSecret(gid, groupInfoSecret)
        }
    }

    fun updateNeedConfirm(gid: Long, confirm: Int, shareConfirmSign: String) {
        val groupInfo = GroupInfoDataManager.queryOneGroupInfo(gid)
        if (null != groupInfo) {
            getGroupInfo(gid)?.needConfirm = confirm == 1
            groupInfo.needOwnerConfirm = confirm
            groupInfo.shareSettingAndConfirmSign = shareConfirmSign
            GroupInfoDataManager.insertGroupInfo(groupInfo)
        }
    }

    fun updateShareSetting(gid: Long,
                           shareEnable: Int,
                           shareEpoch: Int,
                           shareCode: String,
                           shareSetting: String,
                           shareSettingSign: String,
                           shareConfirmSign: String,
                           ephemeralKey: String) {
        val groupInfo = GroupInfoDataManager.queryOneGroupInfo(gid)
        if (null != groupInfo) {
            getGroupInfo(gid)?.shareEnable = shareEnable == 1
            getGroupInfo(gid)?.shareCode = shareCode


            groupInfo.shareEnabled = shareEnable
            groupInfo.shareCodeSetting = shareSetting
            groupInfo.shareSettingAndConfirmSign = shareConfirmSign
            groupInfo.shareCodeSettingSign = shareSettingSign
            groupInfo.shareEpoch = shareEpoch
            groupInfo.shareCode = shareCode
            groupInfo.ephemeralKey = ephemeralKey
            groupInfo.shareLink = ""

            GroupInfoDataManager.insertGroupInfo(groupInfo)
        }
    }

    fun updateGroupNameAndAvatar2Cache(gid: Long, combineName: String, chnCombineName: String, path: String?) {
        val groupInfo = getGroupInfo(gid)
        if (null != groupInfo) {
            if (combineName.isNotEmpty()) {
                groupInfo.spliceName = combineName
            }

            if (!path.isNullOrEmpty()) {
                groupInfo.spliceAvatarPath = path
            }

            if (chnCombineName.isNotEmpty()) {
                groupInfo.chnSpliceName = chnCombineName
            }

            GroupInfoDataManager.updateGroupAutoGenNameAndAvatar(gid, combineName, chnCombineName, path)
        }
    }

    fun saveMember(dbMember: List<GroupMember>) {
        UserDataManager.insertGroupDbMembers(dbMember)
    }

    fun deleteMembers(groupId: Long, mlist: MutableList<String>) {
        UserDataManager.deleteMember(groupId, mlist)
    }

    fun setBroadcastSharingData(gid: Long, doing: Boolean) {
        val gInfo = GroupInfoDataManager.queryOneGroupInfo(gid) ?: return
        if (doing) {
            if (gInfo.status.and(GroupInfo.GroupStatus.BROADCAST_SHARE_DATA_ING.status) == 0) {
                gInfo.status = gInfo.status.or(GroupInfo.GroupStatus.BROADCAST_SHARE_DATA_ING.status)
                GroupInfoDataManager.insertGroupInfo(gInfo)
            }
        } else if (gInfo.status.and(GroupInfo.GroupStatus.BROADCAST_SHARE_DATA_ING.status) != 0) {
            gInfo.status = gInfo.status.and(GroupInfo.GroupStatus.BROADCAST_SHARE_DATA_ING.status.inv())
            GroupInfoDataManager.insertGroupInfo(gInfo)
        }
    }

    fun isBroadcastSharingData(gid: Long): Boolean {
        val gInfo = GroupInfoDataManager.queryOneGroupInfo(gid) ?: return false
        return gInfo.status.and(GroupInfo.GroupStatus.BROADCAST_SHARE_DATA_ING.status) != 0
    }

    fun setProfileEncrypted(gid: Long, isEncrypted: Boolean) {
        GroupInfoDataManager.setProfileEncrypted(gid, isEncrypted)
    }

    fun updateGroupNameAndAvatar(gid: Long, newName: String, newIcon: String) {
        val groupInfo = getGroupInfo(gid)
        groupInfo?.name = newName
        groupInfo?.iconUrl = newIcon
        GroupInfoDataManager.updateGroupNameAndAvatar(gid, newName, newIcon)
    }
}