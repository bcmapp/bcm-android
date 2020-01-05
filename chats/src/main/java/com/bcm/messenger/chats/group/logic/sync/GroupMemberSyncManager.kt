package com.bcm.messenger.chats.group.logic.sync

import com.bcm.messenger.chats.group.core.GroupMemberCore
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.grouprepository.manager.GroupInfoDataManager
import com.bcm.messenger.common.grouprepository.room.entity.GroupMember
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * bcm.social.01 2019/3/26.
 */
class GroupMemberSyncManager(private val accountContext: AccountContext) {
    companion object {
        const val PAGE_SIZE = 500L
    }

    private val syncTaskList = ConcurrentLinkedQueue<Long>()

    fun syncGroupMember(gid: Long, fromUid: String, fromTime: Long, roles: List<Long>, finished: (firstPage: Boolean, finish: Boolean, memberList: List<GroupMember>) -> Unit) {
        if (syncTaskList.contains(gid)) {
            ALog.i("GroupMemberSyncManager", "syncGroupMember $gid exist")
            return
        }

        syncTaskList.add(gid)
        syncGroupMemberImpl(gid, roles, fromUid, fromTime) { firstPage, finish, memberList ->

            if (finish) {
                syncTaskList.remove(gid)
            }
            finished(firstPage, finish, memberList)
        }
    }

    private fun syncGroupMemberImpl(gid: Long, roles: List<Long>, fromUid: String, createTime: Long, finished: (firstPage: Boolean, finish: Boolean, memberList: List<GroupMember>) -> Unit) {
        ALog.i("GroupMemberSyncManager", "syncGroupMemberImpl $gid from: $fromUid createTime: $createTime")
        GroupMemberCore.getGroupMemberByPage(accountContext, gid, roles, fromUid, createTime, PAGE_SIZE)
                .subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AmeDispatcher.ioScheduler)
                .subscribe({
                    ALog.i("GroupMemberSyncManager", "syncGroupMember result $gid ${it.isSuccess} ${it.msg}")
                    if (it.isSuccess) {
                        val groupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, gid)?:throw Exception("")
                        val channelKey = groupInfo.channel_key
                        val list = ArrayList<GroupMember>()
                        for (u in it.data.members) {
                            if (u.uid != null) {
                                val member = u.toDbMember(gid, channelKey, groupInfo)?:continue
                                list.add(member)
                            }
                        }

                        val finishSync = list.size < PAGE_SIZE
                        finished(fromUid.isEmpty(), finishSync, list)

                        if (!finishSync && it.isSuccess) {
                            if (syncTaskList.contains(gid)) {
                                syncGroupMemberImpl(gid, roles, list.last().uid, list.last().joinTime, finished)
                            }
                        }

                    }
                }, {
                    ALog.e("GroupMemberSyncManager", "syncGroupMember $gid", it)
                    throw it
                })
    }

    fun isSyncing(gid: Long): Boolean {
        return syncTaskList.contains(gid)
    }

    fun cancelSync(gid: Long) {
        syncTaskList.remove(gid)
    }
}