package com.bcm.messenger.common.grouprepository.manager

import android.text.TextUtils
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
    fun updateGroupRole(gid: Long, role: Long) {
        getDao().updateRole(gid, role)
    }

    fun updateGroupNotice(gid: Long, noticeContent: String, updateTime: Long) {
        val info = queryOneGroupInfo(gid)
        if (info != null) {
            info.notice_update_time = updateTime
            info.notice_content = noticeContent
            getDao().update(info)
        }
    }

    fun updateNoticeShowState(gid: Long, isShow: Boolean) {
        val info = queryOneGroupInfo(gid)
        if (info != null) {
            if (isShow) {
                info.is_show_notice = GroupInfo.SHOW_NOTICE
            } else {
                info.is_show_notice = GroupInfo.NOT_SHOW_NOTICE
            }
            getDao().update(info)
        }

    }

    fun queryGroupKeyParam(gid: Long, version: Long): GroupKeyParam? {
        val groupKey = UserDatabase.getDatabase().groupKeyDao().queryKeys(gid, listOf(version))
        return if (groupKey.isNotEmpty()) {
            GroupKeyParam(groupKey.first().key.base64Decode(), groupKey.first().version)

        } else {
            val groupInfo = queryOneGroupInfo(gid) ?: return null
            if (!groupInfo.isNewGroup && !TextUtils.isEmpty(groupInfo.currentKey)) {
                GroupKeyParam(groupInfo.currentKey.base64Decode(), groupInfo.currentKeyVersion)
            } else {
                null
            }
        }
    }

    fun queryGroupKeyList(gid: Long, versions: List<Long>): List<GroupKey> {
        return UserDatabase.getDatabase().groupKeyDao().queryKeys(gid, versions)
    }

    fun saveGroupKeyParam(gid: Long, version: Long, key: String) {
        UserDatabase.getDatabase().groupKeyDao().saveKeys(listOf(GroupKey().apply {
            this.gid = gid
            this.version = version
            this.key = key
        }))
    }

    fun queryLastGroupKeyVersion(gid: Long): GroupKey? {
        return UserDatabase.getDatabase().groupKeyDao().queryLastVersionKey(gid)
    }

    fun updateGroupNotificationEnable(gid: Long, enable: Boolean) {
        val info = queryOneGroupInfo(gid)
        if (info != null) {
            if (enable) {
                info.notification_enable = GroupInfo.NOTIFICATION_ENABLE
            } else {
                info.notification_enable = GroupInfo.NOTIFICATION_DISABLE
            }
            getDao().update(info)
        }
    }

    fun updateGroupKey(gid: Long, keyVersion: Long, key: String) {
        getDao().updateGroupKey(gid, keyVersion, key)
    }

    fun updateGroupPinMid(gid: Long, mid: Long) {
        val info = queryOneGroupInfo(gid)
        if (info != null) {
            info.pinMid = mid
            getDao().update(info)
        }
    }

    fun updateGroupPinVisible(gid: Long, hasPin: Boolean) {
        val info = queryOneGroupInfo(gid)
        if (info != null) {
            info.hasPin = if (hasPin) {
                1
            } else {
                0
            }
            getDao().update(info)
        }
    }

    fun updateGroupShareShortLink(gid: Long, shareLink: String) {
        getDao().updateShareShortLink(gid, shareLink)
    }

    fun insertGroupInfo(groupInfo: GroupInfo) {
        //recipient
        val recipient = Recipient.recipientFromNewGroupId(AppContextHolder.APP_CONTEXT, groupInfo.gid)
        Repository.getRecipientRepo()?.setProfile(recipient, null, groupInfo.name, groupInfo.iconUrl)

        if (getDao().loadGroupInfoByGid(groupInfo.gid) == null) {
            getDao().insert(groupInfo)
        } else {
            getDao().update(groupInfo)
        }
    }

    fun updateMemberSyncState(gid: Long, syncState: GroupMemberSyncState) {
        AmeDispatcher.io.dispatch {
            getDao().updateMemberSyncState(gid, syncState.toString())
        }
    }

    fun removeGroupInfo(gid: Long) {
        val info = getDao().loadGroupInfoByGid(gid) ?: return
        getDao().delete(info)
    }

    fun queryOneAmeGroupInfo(gid: Long): AmeGroupInfo? {
        val info = getDao().loadGroupInfoByGid(gid) ?: return null
        return GroupInfoTransform.transformToModel(info)
    }

    //
    fun queryOneGroupInfo(gid: Long): GroupInfo? {
        return getDao().loadGroupInfoByGid(gid)
    }

    //
    fun queryGroupInfoList(gidList: List<Long>): List<GroupInfo> {
        return getDao().loadGroupInfoListByGid(gidList.toLongArray())
    }

    private fun getDao(): GroupInfoDao {
        return UserDatabase.getDatabase().groupInfoDao()
    }


    /**
     * （）
     */
    fun getGroupInfo(groupId: Long): AmeGroupInfo? {
        return queryOneAmeGroupInfo(groupId)
    }

    fun loadAll(): List<GroupInfo> {
        return getDao().loadAll()
    }

    fun saveGroupInfos(list: List<GroupInfo>) {
        if (list.isEmpty()) {
            return
        }
        getDao().insertOrUpdateAll(list)
    }

    fun updateGroupInfoSecret(gid: Long, groupInfoSecret: String) {
        getDao().updateGroupInfoKey(gid, groupInfoSecret)
    }

    fun updateGroupAutoGenNameAndAvatar(gid: Long, combineName: String, chnCombineName: String, path: String?) {
        val info = queryOneGroupInfo(gid)
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

            getDao().update(info)
        }
    }

    fun setProfileEncrypted(gid: Long, isEncrypted: Boolean) {
        getDao().setProfileEncrypted(gid, isEncrypted)
    }

    fun updateGroupName(gid: Long, newName: String) {
        getDao().updateName(gid, newName)
    }

    fun updateGroupAvatar(gid: Long, newIcon: String) {
        getDao().updateAvatar(gid, newIcon)
    }

    fun clearShareSetting(gid: Long) {
        getDao().clearShareSetting(gid)
    }
}
