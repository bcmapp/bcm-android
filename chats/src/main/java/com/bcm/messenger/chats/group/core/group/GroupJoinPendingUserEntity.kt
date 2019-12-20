package com.bcm.messenger.chats.group.core.group

import android.text.TextUtils
import com.bcm.messenger.common.core.corebean.BcmGroupJoinStatus
import com.bcm.messenger.common.core.corebean.IdentityKeyInfo
import com.bcm.messenger.common.grouprepository.room.entity.GroupInfo
import com.bcm.messenger.common.grouprepository.room.entity.GroupJoinRequestInfo
import com.bcm.messenger.common.grouprepository.room.entity.JoinGroupReqComment
import com.bcm.messenger.common.utils.BCMPrivateKeyUtils
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.proguard.NotGuard
import java.io.ByteArrayOutputStream

class GroupJoinPendingUserEntity(val gid:Long, val uid:String, val inviter:String?, val signature:String?, val comment:String?): NotGuard {
    private fun isValid(groupInfo:GroupInfo, uidKey:IdentityKeyInfo): Boolean {
        val signature = this.signature?:return false

        if (uidKey.uid != uid || !TextUtils.isEmpty(inviter)) {
            return false
        }

        try {
            val format = ByteArrayOutputStream()
            format.write(EncryptUtils.base64Decode(groupInfo.shareCode.toByteArray()))
            format.write(EncryptUtils.base64Decode(groupInfo.shareCodeSettingSign.toByteArray()))
            val shareSettingWithConfirmByteArray = format.toByteArray()

            val pubkey = BCMPrivateKeyUtils.identityKey2PublicKey(EncryptUtils.base64Decode(uidKey.identityKey.toByteArray()))
            return BCMEncryptUtils.verifySignature( pubkey, shareSettingWithConfirmByteArray, EncryptUtils.base64Decode(signature.toByteArray()))
        } catch ( e:Throwable ) {
            ALog.e("GroupJoinPendingUserEntity", "isValid", e)
        }

        return false
    }

    private fun getInviteData(groupInfo: GroupInfo, uidKey:IdentityKeyInfo, inviterKey:IdentityKeyInfo): GroupInviteDataEntity? {
        if (inviterKey.uid != inviter || TextUtils.isEmpty(inviter)) {
            return null
        }

        val signature = this.signature?:return null
        try {
            val inviterData = GroupInviteSignDataEntity.signData2InviteData(signature, inviterKey.identityKey, groupInfo.infoSecret)
            if (null != inviterData) {
                if (inviterData.uid != uidKey.uid) {
                    return  null
                }
            }

            return inviterData
        } catch (e:Throwable) {
            ALog.e("GroupJoinPendingUserEntity", "isValidWithInviter", e)
        }

        return null
    }

    fun toJoinRequest(groupInfo:GroupInfo, uidKey:IdentityKeyInfo, inviterKey:IdentityKeyInfo?): GroupJoinRequestInfo? {
        ALog.i("GroupJoinPendingUserEntity", "review $gid, auto join:${null == inviter}")

        val inviteData = if(inviterKey != null){
            getInviteData(groupInfo, uidKey, inviterKey)
        } else {
            null
        }

        if (null != inviteData || isValid(groupInfo, uidKey)) {
            val reviewType = when(groupInfo.needOwnerConfirm) {
                0 -> BcmGroupJoinStatus.WAIT_MEMBER_REVIEW
                else ->BcmGroupJoinStatus.WAIT_OWNER_REVIEW
            }

            ALog.i("GroupJoinPendingUserEntity", "review $gid, review type:$reviewType")

            val comment = this.comment
            val request = GroupJoinRequestInfo(0, groupInfo.gid, uid)
            request.uidIdentityKey = uidKey.identityKey
            request.inviter = inviter?:""
            request.inviterIdentityKey = inviterKey?.identityKey?:""
            request.read = 0
            request.timestamp = inviteData?.timestamp ?: AmeTimeUtil.serverTimeMillis()
            request.status = reviewType.status

            if (null != comment && comment.isNotEmpty()) {
                request.comment = String(EncryptUtils.base64Decode(comment.toByteArray()))
            } else if (inviteData != null) {
                request.comment = GsonUtils.toJson(JoinGroupReqComment(inviteData.name, uidKey.uid))
            }
            return request
        } else {
            ALog.e("GroupJoinPendingUserEntity", "toJoinRequest failed $gid auto join:${null == inviter}")
        }
        return null
    }
}