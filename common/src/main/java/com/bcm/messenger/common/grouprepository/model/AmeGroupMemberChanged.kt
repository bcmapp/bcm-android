package com.bcm.messenger.common.grouprepository.model

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.grouprepository.manager.GroupMemberManager
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.proguard.NotGuard

/**
 * bcm.social.01 2018/6/7.
 */
data class AmeGroupMemberChanged(val accountContext: AccountContext, val groupId: Long, val messageId: Long) {
    companion object {
        val JOIN = 1L
        val LEAVE = 3L
        val UPDATE = 2L
    }

    var fromUid: String? = ""
    var action: Long? = JOIN
    var createTime: Long? = 0
    lateinit var memberList: ArrayList<AmeGroupMemberInfo>

    fun contains(uid: String): Boolean {
        return null != memberList.filter { it.uid == uid }
                .takeIf { it.isNotEmpty() }
                ?.first()
    }

    fun ownerUid(): String? {
        return memberList.filter { it.role == AmeGroupMemberInfo.OWNER }
                .takeIf { it.isNotEmpty() }
                ?.first()?.uid
    }

    fun toDetail(): AmeGroupMessageDetail {
        val detail = AmeGroupMessageDetail()
        detail.gid = groupId
        detail.senderId = fromUid
        detail.serverIndex = messageId
        detail.sendTime = createTime ?: AmeTimeUtil.serverTimeMillis()
        val theOperators: List<String> = memberList.map { it -> it.uid }

        when (action) {
            JOIN -> {
                val action = AmeGroupMessage.SystemContent.TIP_JOIN
                val systemContent = AmeGroupMessage.SystemContent(action, detail.senderId, theOperators, "")
                detail.message = AmeGroupMessage(AmeGroupMessage.SYSTEM_INFO, systemContent)

                if (memberList.isNotEmpty()) {
                    for (u in memberList) {
                        GroupMemberManager.insertGroupMembers(accountContext, memberList)
                    }
                }
            }
            LEAVE -> {
                val action = AmeGroupMessage.SystemContent.TIP_KICK
                val systemContent = AmeGroupMessage.SystemContent(action, detail.senderId, theOperators, "")
                detail.message = AmeGroupMessage(AmeGroupMessage.SYSTEM_INFO, systemContent)

                if (memberList.isNotEmpty()) {
                    GroupMemberManager.deleteMember(accountContext, memberList)
                }
            }
            UPDATE -> {
                val systemContent = AmeGroupMessage.SystemContent(AmeGroupMessage.SystemContent.TIP_UPDATE, detail.senderId, theOperators, "")
                detail.message = AmeGroupMessage(AmeGroupMessage.SYSTEM_INFO, systemContent)

                if (memberList.isNotEmpty()) {
                    GroupMemberManager.updateGroupMembers(accountContext, memberList)
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