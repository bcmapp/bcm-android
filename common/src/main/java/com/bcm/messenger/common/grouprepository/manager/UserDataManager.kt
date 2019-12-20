package com.bcm.messenger.common.grouprepository.manager

import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.database.db.UserDatabase
import com.bcm.messenger.common.grouprepository.modeltransform.GroupMemberTransform
import com.bcm.messenger.common.grouprepository.room.dao.GroupMemberDao
import com.bcm.messenger.common.grouprepository.room.entity.GroupMember

object UserDataManager {
    private const val IS_DIRTY = "IS_DIRTY"

    /**
     * 群内成员身份变更
     * @param gid
     * @param uid
     * @param role: 1群主 ，2 管理员，3 一般成员，4 订阅者, 0和群无关人员，default：0
     * @return -1:群成员不存在,0:正常
     */
    fun updateGroupMemberRole(gid: Long, uid: String, role: Long): Int {
        val groupUser = getDao().queryGroupMember(gid, uid)
        if (groupUser != null) {
            groupUser.role = role
            getDao().updateGroupMember(groupUser)
            return 0
        }
        return -1
    }

    fun deleteMember(gid: Long, uid: String) {
        if (uid.isNotEmpty()) {
            val member = AmeGroupMemberInfo()
            member.gid = gid
            member.uid = Address.fromSerialized(uid)
            deleteMember(member)
        }
    }

    fun deleteMember(member:AmeGroupMemberInfo) {
        getDao().deleteGroupMember(GroupMemberTransform.transToDb(member))
    }

    fun deleteMember(list:List<AmeGroupMemberInfo>) {
        if (list.isNotEmpty()) {
            getDao().deleteGroupMember(GroupMemberTransform.transToDbList(list))
        }
    }

    fun deleteMember(gid: Long, mlist:List<String>) {
        if (mlist.isNotEmpty()) {
            getDao().deleteGroupMember(gid, mlist)
        }
    }

    /**

     * @param gid
     * @param role: 0群主 ，1 管理员，2 一般成员，3 订阅者，-1 和群无关人员，default：-1
     * @return
     */
    fun queryGroupMemberByRole(gid: Long, role: Int): ArrayList<AmeGroupMemberInfo> {
        return GroupMemberTransform.transToModelList(getDao().loadGroupMembersByGidAndRole(gid, role))
    }

    fun insertGroupMember(member: AmeGroupMemberInfo) {
        if (member.uid != null) {
            getDao().insertGroupMember(GroupMemberTransform.transToDb(member))
        }
    }

    fun insertGroupMembers(userList: List<AmeGroupMemberInfo>) {
        if (userList.isNotEmpty()){
            getDao().insertGroupMember(GroupMemberTransform.transToDbList(userList))
        }
    }

    fun insertGroupDbMembers(userList: List<GroupMember>) {
        if (userList.isNotEmpty()){
            getDao().insertGroupMember(userList)
        }
    }

    fun updateGroupMember(member: AmeGroupMemberInfo) {
        if (member.uid != null) {
            getDao().insertGroupMember(GroupMemberTransform.transToDb(member))
        }
    }

    fun updateGroupMembers(userList: List<AmeGroupMemberInfo>) {
        if (userList.isNotEmpty()){
            getDao().updateGroupMember(GroupMemberTransform.transToDbList(userList))
        }
    }

    fun queryGroupMember(gid: Long, uid: String): AmeGroupMemberInfo? {
        val member = getDao().queryGroupMember(gid, uid)
        if (null != member) {
            return GroupMemberTransform.transToModel(member)
        }
        return null
    }

    fun queryGroupMemberList(gid: Long, uidList: List<String>): List<AmeGroupMemberInfo> {
        val memberList = getDao().queryGroupMemberList(gid, uidList.toTypedArray())
        return GroupMemberTransform.transToModelList(memberList)
    }

    fun queryTopNGroupMember(gid: Long, n: Long): List<AmeGroupMemberInfo> {
        val memberList = getDao().queryGroupMemberList(gid, n)
        return GroupMemberTransform.transToModelList(memberList)
    }

    fun queryGroupMemberRole(gid:Long, uid:String): Long {
        return getDao().queryGroupMember(gid, uid)?.role
                ?: AmeGroupMemberInfo.VISITOR
    }


    fun clear(gid: Long) {
        getDao().clear(gid)
    }


    fun getLastMember(gid: Long): GroupMember? {
        val list = getDao().loadGroupMembers(gid)
        if (!list.isEmpty()) {
            return list.last()
        }
        return null
    }

    private fun getDao(): GroupMemberDao {
        return UserDatabase.getDatabase().groupMemberDao()
    }
}
