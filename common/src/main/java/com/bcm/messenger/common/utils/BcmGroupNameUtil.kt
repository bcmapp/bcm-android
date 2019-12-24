package com.bcm.messenger.common.utils

import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.utility.logger.ALog

/**
 * 
 */
object BcmGroupNameUtil {

    fun getGroupMemberName(recipient: Recipient, groupMember: AmeGroupMemberInfo?): String {
        ALog.d("BcmGroupNameUtil", "getGroupMemberName, customNick: ${groupMember?.customNickname}, nick: ${groupMember?.nickname}")
        // group custom nick > extra local name > bcm Profile > group nick > UID

        return if(groupMember?.customNickname?.isNotEmpty() == true) {
            groupMember.customNickname
        }
        else if(recipient.localName?.isNotEmpty() == true) {
            recipient.localName ?: ""
        }
        else if (recipient.bcmName?.isNotEmpty() == true) {
            recipient.bcmName ?: ""
        }
        else if(groupMember?.nickname?.isNotEmpty() == true) {
            groupMember.nickname
        }
        else {
            recipient.address.format()
        }
    }

    fun getGroupMemberAtName(recipient: Recipient, groupMember: AmeGroupMemberInfo?): String {
        ALog.d("BcmGroupNameUtil", "getGroupMemberAtName, customNick: ${groupMember?.customNickname}, nick: ${groupMember?.nickname}")
        // group custom nick > bcm Profile > group nick > UID

        return if(groupMember?.customNickname?.isNotEmpty() == true) {
            groupMember.customNickname
        }
        else if (recipient.bcmName?.isNotEmpty() == true) {
            recipient.bcmName ?: ""
        }
        else if(groupMember?.nickname?.isNotEmpty() == true) {
            groupMember.nickname
        }
        else {
            recipient.address.format()
        }
    }

}