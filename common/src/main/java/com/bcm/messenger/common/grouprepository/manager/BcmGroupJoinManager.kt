package com.bcm.messenger.common.grouprepository.manager

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.database.db.UserDatabase
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.room.dao.GroupJoinInfoDao
import com.bcm.messenger.common.grouprepository.room.entity.GroupJoinRequestInfo

object BcmGroupJoinManager {
    fun queryJoinRequestByGid(accountContext: AccountContext, gid: Long): List<GroupJoinRequestInfo> {
        return getDao(accountContext)?.queryJoinListByGid(gid) ?: listOf()
    }

    fun queryJoinRequestByReqId(accountContext: AccountContext, reqIdList: List<Long>): List<GroupJoinRequestInfo> {
        return getDao(accountContext)?.queryJoinInfoByReqId(reqIdList) ?: listOf()
    }

    private fun getDao(accountContext: AccountContext): GroupJoinInfoDao? {
        return Repository.getGroupJoinInfoRepo(accountContext)
    }

    fun saveJoinGroupInfos(accountContext: AccountContext, list: List<GroupJoinRequestInfo>) {
        getDao(accountContext)?.saveJoinInfos(list)
    }

    fun updateJoinRequests(accountContext: AccountContext, list: List<GroupJoinRequestInfo>) {
        getDao(accountContext)?.updateJoinInfos(list)
    }

    fun getJoinInfoByDetail(accountContext: AccountContext, gid: Long, uid: String, status: Int, inviter: String): GroupJoinRequestInfo? {
        return getDao(accountContext)?.queryByDetail(gid, uid, status, inviter)
    }

    fun getJoinInfoByInviterData(accountContext: AccountContext, gid: Long, inviter: String, uid: String, timestamp: Long): GroupJoinRequestInfo? {
        return getDao(accountContext)?.queryByInviteData(gid, inviter, uid, timestamp)
    }

    fun getJoinInfoByUidList(accountContext: AccountContext, gid: Long, uidList: List<String>): List<GroupJoinRequestInfo> {
        return getDao(accountContext)?.queryByUid(gid, uidList) ?: listOf()
    }

    fun readAllJoinRequest(accountContext: AccountContext, reqIdList: List<Long>) {
        if (reqIdList.isNotEmpty()) {
            val list = getDao(accountContext)?.queryJoinInfoByReqId(reqIdList) ?: listOf()
            for (i in list) {
                i.read = 1
            }

            if (list.isNotEmpty()) {
                getDao(accountContext)?.updateJoinInfos(list)
            }
        }
    }
}