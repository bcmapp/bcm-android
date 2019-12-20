package com.bcm.messenger.common.utils

import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.utility.logger.ALog

/**
 * 群成员名称显示帮助类
 */
object BcmGroupNameUtil {

    /**
     * 获取当前群成员的昵称
     */
    fun getGroupMemberName(recipient: Recipient, groupMember: AmeGroupMemberInfo?): String {
        ALog.d("BcmGroupNameUtil", "getGroupMemberName, customNick: ${groupMember?.customNickname}, nick: ${groupMember?.nickname}")
        //名称显示 优先级: 群用户自定义昵称 > 通讯录备注 > 个人Profile加密昵称 > 个人profile明文昵称 > UID

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

}