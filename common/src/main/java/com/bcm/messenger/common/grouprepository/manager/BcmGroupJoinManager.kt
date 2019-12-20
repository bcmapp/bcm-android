package com.bcm.messenger.common.grouprepository.manager

import com.bcm.messenger.common.database.db.UserDatabase
import com.bcm.messenger.common.grouprepository.room.dao.GroupJoinInfoDao
import com.bcm.messenger.common.grouprepository.room.entity.GroupJoinRequestInfo

object BcmGroupJoinManager {
    fun queryJoinRequestByGid(gid:Long):List<GroupJoinRequestInfo> {
        return getDao().queryJoinListByGid(gid)
    }

    fun queryJoinRequestByReqId(reqIdList:List<Long>):List<GroupJoinRequestInfo> {
        return getDao().queryJoinInfoByReqId(reqIdList)
    }

    private fun getDao(): GroupJoinInfoDao {
        return UserDatabase.getDatabase().groupJoinInfoDao()
    }

    fun saveJoinGroupInfos(list: List<GroupJoinRequestInfo>) {
        getDao().saveJoinInfos(list)
    }

    fun updateJoinRequests(list: List<GroupJoinRequestInfo>) {
        getDao().updateJoinInfos(list)
    }

    fun getJoinInfoByDetail(gid:Long, uid:String, status:Int, inviter:String): GroupJoinRequestInfo? {
        return getDao().queryByDetail(gid, uid, status,inviter)
    }

    fun getJoinInfoByInviterData(gid:Long, inviter:String, uid:String, timestamp:Long): GroupJoinRequestInfo? {
        return getDao().queryByInviteData(gid, inviter, uid, timestamp)
    }

    fun getJoinInfoByUidList(gid:Long, uidList:List<String>): List<GroupJoinRequestInfo> {
        return getDao().queryByUid(gid, uidList)
    }

    fun readAllJoinRequest(reqIdList: List<Long>) {
        if (reqIdList.isNotEmpty()) {
            val list = getDao().queryJoinInfoByReqId(reqIdList)
            for (i in list) {
                i.read = 1
            }
            getDao().updateJoinInfos(list)
        }
    }
}