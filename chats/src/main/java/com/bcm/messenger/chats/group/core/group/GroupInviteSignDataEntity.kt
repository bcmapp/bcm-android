package com.bcm.messenger.chats.group.core.group

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.utils.BCMPrivateKeyUtils
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.proguard.NotGuard

class GroupInviteSignDataEntity(val data:String, val sig:String): NotGuard {
    companion object {
        fun inviteData2SignData(accountContext: AccountContext, inviteDataEntity: GroupInviteDataEntity, infoSecret:String): String {
            val inviteData = GsonUtils.toJson(inviteDataEntity)
            val data = EncryptUtils.aes256EncryptAndBase64(inviteData, EncryptUtils.base64Decode(infoSecret.toByteArray()))
            val signByteArray = BCMEncryptUtils.signWithMe(accountContext, EncryptUtils.base64Decode(data.toByteArray()))
            val signData = GroupInviteSignDataEntity(data, String(EncryptUtils.base64Encode(signByteArray)))
            return String(EncryptUtils.base64Encode(GsonUtils.toJson(signData).toByteArray()))
        }

        fun signData2InviteData(signData:String, inviterIdentityKey:String, infoSecret: String): GroupInviteDataEntity? {
            try {
                val signJson = String(EncryptUtils.base64Decode(signData.toByteArray()))
                val signDataEntity = GsonUtils.fromJson<GroupInviteSignDataEntity>(signJson, GroupInviteSignDataEntity::class.java)

                val pubKey = BCMPrivateKeyUtils.identityKey2PublicKey(EncryptUtils.base64Decode(inviterIdentityKey.toByteArray()))
                if(BCMEncryptUtils.verifySignature(pubKey,
                                EncryptUtils.base64Decode(signDataEntity.data.toByteArray()),
                                EncryptUtils.base64Decode(signDataEntity.sig.toByteArray()))) {
                    val inviteData = EncryptUtils.aes256DecryptAndBase64(signDataEntity.data, EncryptUtils.base64Decode(infoSecret.toByteArray()))
                    return GsonUtils.fromJson(inviteData, GroupInviteDataEntity::class.java)
                }
            } catch (e:Throwable) {
                ALog.e("GroupInviteSignDataEntity", "signData2InviteData failed", e)
            }
            return null
        }
    }
}