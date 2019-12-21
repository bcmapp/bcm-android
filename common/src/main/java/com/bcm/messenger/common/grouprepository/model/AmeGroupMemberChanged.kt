package com.bcm.messenger.common.grouprepository.model

import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.grouprepository.manager.UserDataManager
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.proguard.NotGuard

/**
 * Created by bcm.social.01 on 2018/6/7.
 */
data class AmeGroupMemberChanged(val groupId:Long, val messageId:Long) {
    companion object {
        val JOIN = 1L
        val LEAVE = 3L
        val UPDATE = 2L
    }

    var fromUid: String? = ""
    var action: Long? = JOIN
    var createTime: Long? = 0
    lateinit var memberList: ArrayList<AmeGroupMemberInfo>

    /**
     * true join 
     */
    fun isMyJoin(): Boolean {
        if (action == JOIN){
            for (i in memberList){
                if (i.uid.serialize() == AMESelfData.uid){
                    return true
                }
            }
        }
        return false
    }

    /**
     * true leave 
     */
    fun isMyLeave(): Boolean {
        if (action == LEAVE){
            for (i in memberList){
                if (i.uid.serialize() == AMESelfData.uid){
                    return true
                }
            }
        }
        return false
    }


    fun toDetail(): AmeGroupMessageDetail {
        val detail = AmeGroupMessageDetail()
        detail.gid = groupId
        detail.senderId = fromUid
        detail.serverIndex = messageId
        detail.sendTime = createTime ?: AmeTimeUtil.serverTimeMillis()
        val theOperators: List<String> = memberList.map { it -> it.uid.serialize() }

        when (action) {
            JOIN -> {
                val action = AmeGroupMessage.SystemContent.TIP_JOIN
                val systemContent = AmeGroupMessage.SystemContent(action, detail.senderId, theOperators, "")
                detail.message = AmeGroupMessage(AmeGroupMessage.SYSTEM_INFO, systemContent)

                if (memberList.isNotEmpty()) {
                    for (u in memberList) {
                        UserDataManager.insertGroupMembers(memberList)
                    }
                }
            }
            LEAVE -> {
                val action = AmeGroupMessage.SystemContent.TIP_KICK
                val systemContent = AmeGroupMessage.SystemContent(action, detail.senderId, theOperators, "")
                detail.message = AmeGroupMessage(AmeGroupMessage.SYSTEM_INFO, systemContent)

                if (memberList.isNotEmpty()) {
                    UserDataManager.deleteMember(memberList)
                }
            }
            UPDATE -> {
                val systemContent = AmeGroupMessage.SystemContent(AmeGroupMessage.SystemContent.TIP_UPDATE, detail.senderId, theOperators, "")
                detail.message = AmeGroupMessage(AmeGroupMessage.SYSTEM_INFO, systemContent)

                if (memberList.isNotEmpty()) {
                    UserDataManager.updateGroupMembers(memberList)
                }
            }
            else -> {
                // Action 
            }
        }
        return detail
    }


    class MemberX : NotGuard {
        var role: Long? = 0
        var nick: String? = ""
        var uid: String? = ""
    }

    class MemberChangeMessage : NotGuard {
        val action: Long? = 1
        var members: java.util.ArrayList<MemberX>? = null
    }
}