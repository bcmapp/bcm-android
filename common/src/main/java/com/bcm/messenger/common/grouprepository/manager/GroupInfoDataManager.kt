package com.bcm.messenger.common.grouprepository.manager

import android.text.TextUtils
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.corebean.AmeGroupInfo
import com.bcm.messenger.common.core.corebean.GroupKeyParam
import com.bcm.messenger.common.core.corebean.GroupMemberSyncState
import com.bcm.messenger.common.database.db.UserDatabase
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.modeltransform.GroupInfoTransform
import com.bcm.messenger.common.grouprepository.room.dao.GroupInfoDao
import com.bcm.messenger.common.grouprepository.room.entity.GroupInfo
import com.bcm.messenger.common.grouprepository.room.entity.GroupKey
import com.bcm.messenger.common.utils.base64Decode
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.dispatcher.AmeDispatcher

object GroupInfoDataManager {
    fun updateGroupRole(accountContext: AccountContext, gid: Long, role: Long) {
        getDao(accountContext)?.updateRole(gid, role)
    }

    fun updateGroupNotice(accountContext: AccountContext, gid: Long, noticeContent: String, updateTime: Long) {
        val info = queryOneGroupInfo(accountContext, gid)
        if (info != null) {
            info.notice_update_time = updateTime
            info.notice_content = noticeContent
            getDao(accountContext)?.update(info)
        }
    }

    fun updateNoticeShowState(accountContext: AccountContext, gid: Long, isShow: Boolean) {
        val info = queryOneGroupInfo(accountContext, gid)
        if (info != null) {
            if (isShow) {
                info.is_show_notice = GroupInfo.SHOW_NOTICE
            } else {
                info.is_show_notice = GroupInfo.NOT_SHOW_NOTICE
            }
            getDao(accountContext)?.update(info)
        }

    }

    fun queryGroupKeyParam(accountContext: AccountContext, gid: Long, version: Long): GroupKeyParam? {
        val groupKey = Repository.getGroupKeyRepo(accountContext)?.queryKeys(gid, listOf(version))
        return if (groupKey?.isNotEmpty() == true) {
            GroupKeyParam(groupKey.first().key.base64Decode(), groupKey.first().version)
        } else {
            val groupInfo = queryOneGroupInfo(accountContext, gid) ?: return null
            if (!groupInfo.isNewGroup && !TextUtils.isEmpty(groupInfo.currentKey)) {
                GroupKeyParam(groupInfo.currentKey.base64Decode(), groupInfo.currentKeyVersion)
            } else {
                null
            }
        }
    }

    fun queryGroupKeyList(accountContext: AccountContext, gid: Long, versions: List<Long>): List<GroupKey> {
        return Repository.getGroupKeyRepo(accountContext)?.queryKeys(gid, versions)?: listOf()
    }

    fun saveGroupKeyParam(accountContext: AccountContext, gid: Long, version: Long, key: String) {
        Repository.getGroupKeyRepo(accountContext)?.saveKeys(listOf(GroupKey().apply {
            this.gid = gid
            this.version = version
            this.key = key
        }))
    }

    fun queryLastGroupKeyVersion(accountContext: AccountContext, gid: Long): GroupKey? {
        return Repository.getGroupKeyRepo(accountContext)?.queryLastVersionKey(gid).takeIf { it?.key?.isNotEmpty() == true }
    }

    fun updateGroupNotificationEnable(accountContext: AccountContext, gid: Long, enable: Boolean) {
        val info = queryOneGroupInfo(accountContext, gid)
        if (info != null) {
            if (enable) {
                info.notification_enable = GroupInfo.NOTIFICATION_ENABLE
            } else {
                info.notification_enable = GroupInfo.NOTIFICATION_DISABLE
            }
            getDao(accountContext)?.update(info)
        }
    }

    fun updateGroupKey(accountContext: AccountContext, gid: Long, keyVersion: Long, key: String) {
        getDao(accountContext)?.updateGroupKey(gid, keyVersion, key)
    }

    fun updateGroupPinMid(accountContext: AccountContext, gid: Long, mid: Long) {
        val info = queryOneGroupInfo(accountContext, gid)
        if (info != null) {
            info.pinMid = mid
            getDao(accountContext)?.update(info)
        }
    }

    fun updateGroupPinVisible(accountContext: AccountContext, gid: Long, hasPin: Boolean) {
        val info = queryOneGroupInfo(accountContext, gid)
        if (info != null) {
            info.hasPin = if (hasPin) {
                1
            } else {
                0
            }
            getDao(accountContext)?.update(info)
        }
    }

    fun updateGroupShareShortLink(accountContext: AccountContext, gid: Long, shareLink: String) {
        getDao(accountContext)?.updateShareShortLink(gid, shareLink)
    }

    fun insertGroupInfo(accountContext: AccountContext, groupInfo: GroupInfo) {
        //recipient
        val recipient = Recipient.recipientFromNewGroupId(accountContext, groupInfo.gid)
        Repository.getRecipientRepo(accountContext)?.setProfile(recipient, null, groupInfo.name, groupInfo.iconUrl)

        if (getDao(accountContext)?.loadGroupInfoByGid(groupInfo.gid) == null) {
            getDao(accountContext)?.insert(groupInfo)
        } else {
            getDao(accountContext)?.update(groupInfo)
        }
    }

    fun updateMemberSyncState(accountContext: AccountContext, gid: Long, syncState: GroupMemberSyncState) {
        AmeDispatcher.io.dispatch {
            getDao(accountContext)?.updateMemberSyncState(gid, syncState.toString())
        }
    }

    fun removeGroupInfo(accountContext: AccountContext, gid: Long) {
        val info = getDao(accountContext)?.loadGroupInfoByGid(gid) ?: return
        getDao(accountContext)?.delete(info)
    }

    fun queryOneAmeGroupInfo(accountContext: AccountContext, gid: Long): AmeGroupInfo? {
        val info = getDao(accountContext)?.loadGroupInfoByGid(gid) ?: return null
        return GroupInfoTransform.transformToModel(info)
    }

    //
    fun queryOneGroupInfo(accountContext: AccountContext, gid: Long): GroupInfo? {
        return getDao(accountContext)?.loadGroupInfoByGid(gid)
    }

    //
    fun queryGroupInfoList(accountContext: AccountContext, gidList: List<Long>): List<GroupInfo> {
        return getDao(accountContext)?.loadGroupInfoListByGid(gidList.toLongArray())?: listOf()
    }

    private fun getDao(accountContext: AccountContext): GroupInfoDao? {
        return Repository.getGroupInfoRepo(accountContext)
    }


    /**
     * （）
     */
    fun getGroupInfo(accountContext: AccountContext, groupId: Long): AmeGroupInfo? {
        return queryOneAmeGroupInfo(accountContext, groupId)
    }

    fun loadAll(accountContext: AccountContext): List<GroupInfo> {
        return getDao(accountContext)?.loadAll()?: listOf()
    }

    fun saveGroupInfos(accountContext: AccountContext, list: List<GroupInfo>) {
        if (list.isEmpty()) {
            return
        }
        getDao(accountContext)?.insertOrUpdateAll(list)
    }

    fun updateGroupInfoSecret(accountContext: AccountContext, gid: Long, groupInfoSecret: String) {
        getDao(accountContext)?.updateGroupInfoKey(gid, groupInfoSecret)
    }

    fun updateGroupAutoGenNameAndAvatar(accountContext: AccountContext, gid: Long, combineName: String, chnCombineName: String, path: String?) {
        val info = queryOneGroupInfo(accountContext, gid)
        if (null != info) {
            if (combineName.isNotEmpty()) {
                info.spliceName = combineName
            }

            if (!path.isNullOrEmpty()) {
                info.spliceAvatar = path
            }

            if (chnCombineName.isNotEmpty()) {
                info.chnSpliceName = chnCombineName
            }

            getDao(accountContext)?.update(info)
        }
    }

    fun setProfileEncrypted(accountContext: AccountContext, gid: Long, isEncrypted: Boolean) {
        getDao(accountContext)?.setProfileEncrypted(gid, isEncrypted)
    }

    fun updateGroupName(accountContext: AccountContext, gid: Long, newName: String) {
        getDao(accountContext)?.updateName(gid, newName)
    }

    fun updateGroupAvatar(accountContext: AccountContext, gid: Long, newIcon: String) {
        getDao(accountContext)?.updateAvatar(gid, newIcon)
    }

    fun clearShareSetting(accountContext: AccountContext, gid: Long) {
        getDao(accountContext)?.clearShareSetting(gid)
    }

    fun increaseMemberCount(accountContext: AccountContext, gid: Long, increaseCount: Long) {
        val count = getDao(accountContext)?.queryMemberCount(gid)?:0 + increaseCount
        if (count > 0) {
            getDao(accountContext)?.updateMemberCount(gid, count)
        }
    }

    fun updateOwner(accountContext: AccountContext, gid: Long, ownerUid: String) {
        getDao(accountContext)?.updateOwner(gid, ownerUid)
    }

    fun updateNeedConfirm(accountContext: AccountContext, gid: Long, needConfirm:Int, confirmSign:String) {
        getDao(accountContext)?.updateNeedConfirm(gid, needConfirm, confirmSign)
    }
}
