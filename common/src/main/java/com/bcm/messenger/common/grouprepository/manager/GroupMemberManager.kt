package com.bcm.messenger.common.grouprepository.manager

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.database.db.UserDatabase
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.modeltransform.GroupMemberTransform
import com.bcm.messenger.common.grouprepository.room.dao.GroupMemberDao
import com.bcm.messenger.common.grouprepository.room.entity.GroupMember

object GroupMemberManager {

    fun deleteMember(accountContext: AccountContext, gid: Long, uid: String) {
        if (uid.isNotEmpty()) {
            val member = AmeGroupMemberInfo()
            member.gid = gid
            member.uid = Address.from(uid)
            deleteMember(accountContext, member)
        }
    }

    fun deleteMember(accountContext: AccountContext, member:AmeGroupMemberInfo) {
        getDao(accountContext)?.deleteGroupMember(GroupMemberTransform.transToDb(member))
    }

    fun deleteMember(accountContext: AccountContext, list:List<AmeGroupMemberInfo>) {
        if (list.isNotEmpty()) {
            getDao(accountContext)?.deleteGroupMember(GroupMemberTransform.transToDbList(list))
        }
    }

    fun deleteMember(accountContext: AccountContext, gid: Long, mlist:List<String>) {
        if (mlist.isNotEmpty()) {
            getDao(accountContext)?.deleteGroupMember(gid, mlist)
        }
    }

    /**

     * @param gid
     * @param role: 0 ，1 ，2 ，3 ，-1 ，default：-1
     * @return
     */
    fun queryGroupMemberByRole(accountContext: AccountContext, gid: Long, role: Int): ArrayList<AmeGroupMemberInfo> {
        val dao = getDao(accountContext)?:return arrayListOf()
        return GroupMemberTransform.transToModelList(dao.loadGroupMembersByGidAndRole(gid, role))
    }

    fun insertGroupMember(accountContext: AccountContext, member: AmeGroupMemberInfo) {
        if (member.uid != null) {
            getDao(accountContext)?.insertGroupMember(GroupMemberTransform.transToDb(member))
        }
    }

    fun insertGroupMembers(accountContext: AccountContext, userList: List<AmeGroupMemberInfo>) {
        if (userList.isNotEmpty()){
            getDao(accountContext)?.insertGroupMember(GroupMemberTransform.transToDbList(userList))
        }
    }

    fun insertGroupDbMembers(accountContext: AccountContext, userList: List<GroupMember>) {
        if (userList.isNotEmpty()){
            getDao(accountContext)?.insertGroupMember(userList)
        }
    }

    fun updateGroupMember(accountContext: AccountContext, member: AmeGroupMemberInfo) {
        if (member.uid != null) {
            getDao(accountContext)?.insertGroupMember(GroupMemberTransform.transToDb(member))
        }
    }

    fun updateGroupMembers(accountContext: AccountContext, userList: List<AmeGroupMemberInfo>) {
        if (userList.isNotEmpty()){
            getDao(accountContext)?.updateGroupMember(GroupMemberTransform.transToDbList(userList))
        }
    }

    fun queryGroupMember(accountContext: AccountContext, gid: Long, uid: String): AmeGroupMemberInfo? {
        val member = getDao(accountContext)?.queryGroupMember(gid, uid)
        if (null != member) {
            return GroupMemberTransform.transToModel(member)
        }
        return null
    }

    fun queryGroupMemberList(accountContext: AccountContext, gid: Long, uidList: List<String>): List<AmeGroupMemberInfo> {
        val memberList = getDao(accountContext)?.queryGroupMemberList(gid, uidList.toTypedArray())?: listOf()
        return GroupMemberTransform.transToModelList(memberList)
    }

    fun queryTopNGroupMember(accountContext: AccountContext, gid: Long, n: Long): List<AmeGroupMemberInfo> {
        val memberList = getDao(accountContext)?.queryGroupMemberList(gid, n)?: listOf()
        return GroupMemberTransform.transToModelList(memberList)
    }

    fun queryGroupMemberRole(accountContext: AccountContext, gid:Long, uid:String): Long {
        return getDao(accountContext)?.queryGroupMember(gid, uid)?.role
                ?: AmeGroupMemberInfo.VISITOR
    }


    fun clear(accountContext: AccountContext, gid: Long) {
        getDao(accountContext)?.clear(gid)
    }


    fun getLastMember(accountContext: AccountContext, gid: Long): GroupMember? {
        val list = getDao(accountContext)?.loadGroupMembers(gid)
        if (list?.isNotEmpty() == true) {
            return list.last()
        }
        return null
    }

    private fun getDao(accountContext: AccountContext): GroupMemberDao? {
        return Repository.getGroupMemberRepo(accountContext)
    }
}
