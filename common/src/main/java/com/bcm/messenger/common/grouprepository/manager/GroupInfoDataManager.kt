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
    fun updateGroupName(gid: Long, name: String) {
        val info = queryOneGroupInfo(gid)
        if (info != null) {
            info.name = name
            getDao().update(info)
        }
    }

    fun updateGroupRole(gid: Long, role: Long) {
        getDao().updateRole(gid, role)
    }

    fun updateGroupMemberCount(gid: Long, memberCount: Int, suberCount: Int) {
        val info = queryOneGroupInfo(gid)
        if (info != null) {
            info.member_count = memberCount
            info.subscriber_count = suberCount
            getDao().update(info)
        }
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

    fun queryIsNoticeShow(gid: Long): Boolean {
        val info = queryOneGroupInfo(gid)
        if (info != null) {
            when (info.is_show_notice) {
                GroupInfo.SHOW_NOTICE -> return true
                else -> return false
            }
        }
        return false
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

    fun saveGroupKeyParam(list: List<GroupKey>) {
        UserDatabase.getDatabase().groupKeyDao().saveKeys(list)
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

    fun queryDBGroupInfoByChannelShareUrl(shareUrl: String?): GroupInfo? {
        if (shareUrl == null) {
            return null
        }
        return getDao().loadGroupInfoByShareUrl(shareUrl)
    }

    fun updateGroupKey(gid: Long, keyVersion: Long, key: String) {
        getDao().updateGroupKey(gid, keyVersion, key)
    }

    fun updateGroupShareContent(gid: Long, shareContent: String) {
        val info = queryOneGroupInfo(gid)
        if (info != null) {
            info.share_content = shareContent
            getDao().update(info)
        }
    }

    fun updateGroupShareUrl(gid: Long, shareUrl: String) {
        val info = queryOneGroupInfo(gid)
        if (info != null) {
            info.share_url = shareUrl
            getDao().update(info)
        }
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

    //更新群权限信息，群主和管理员可操作
    fun updateGroupPermission(gid: Long, permission: Int) {
        val info = queryOneGroupInfo(gid)
        if (info != null) {
            info.permission = permission
            getDao().update(info)
        }
    }

    fun updateGroupSpliceName(gid: Long, spliceName: String) {
        val info = queryOneGroupInfo(gid)
        if (info != null && info.spliceName != spliceName) {
            info.spliceName = spliceName
            getDao().update(info)
            val recipient = Recipient.recipientFromNewGroupId(AppContextHolder.APP_CONTEXT, gid)
            Repository.getRecipientRepo()?.setProfile(recipient, null, info.name, info.iconUrl)
        }
    }

    fun updateGroupSpliceAvatar(gid: Long, avatarPath: String) {
        val info = queryOneGroupInfo(gid)
        if (info != null) {
            info.spliceAvatar = avatarPath
            getDao().update(info)
            val recipient = Recipient.recipientFromNewGroupId(AppContextHolder.APP_CONTEXT, gid)
            Repository.getRecipientRepo()?.setProfile(recipient, null, info.name, info.iconUrl)
        }
    }

    /**
     * 更新群的分享短链
     */
    fun updateGroupShareShortLink(gid: Long, shareLink: String) {
        getDao().updateShareShortLink(gid, shareLink)
    }

    //必需先取出该信息，更新相应字段，再去插入，切勿构造 GroupInfo插入，除非之前是空的
    //插入一条新群组信息
    fun insertGroupInfo(groupInfo: GroupInfo) {
        //同时更新到recipient数据库
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

    //用于更新群信息时使用
    fun queryOneGroupInfo(gid: Long): GroupInfo? {
        return getDao().loadGroupInfoByGid(gid)
    }

    //用于更新群信息时使用
    fun queryGroupInfoList(gidList: List<Long>): List<GroupInfo> {
        return getDao().loadGroupInfoListByGid(gidList.toLongArray())
    }

    private fun getDao(): GroupInfoDao {
        return UserDatabase.getDatabase().groupInfoDao()
    }


    /**
     * 获取群信息（同步）
     */
    fun getGroupInfo(groupId: Long): AmeGroupInfo? {
        return GroupInfoDataManager.queryOneAmeGroupInfo(groupId)
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

    fun updateGroupKey(gid: Long, groupKey: String, groupInfoSecret: String) {
        val info = queryOneGroupInfo(gid)
        if (null != info) {
            info.currentKey = groupKey
            info.infoSecret = groupInfoSecret
            getDao().update(info)
        }
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

    fun updateGroupNameAndAvatar(gid: Long, newName: String, newIcon: String) {
        getDao().updateNameAndAvatar(gid, newName, newIcon)
    }

    fun clearShareSetting(gid: Long) {
        getDao().clearShareSetting(gid)
    }
}
