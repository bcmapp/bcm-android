package com.bcm.messenger.chats.group.core.group

import com.bcm.messenger.common.grouprepository.room.entity.GroupInfo
import com.bcm.messenger.common.utils.BCMPrivateKeyUtils
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.logger.ALog
import java.io.ByteArrayOutputStream

object GroupShareSettingUtil {

    fun parseIntoGroupInfo(ownerIdentityKey: String?,
                           shareSetting: String?,
                           shareSettingSign: String?,
                           shareConfirmSign: String?,
                           infoSecret: String,
                           ownerConfirm: Int,
                           dbGroupInfo: GroupInfo): Boolean {
        try {
            if (null == ownerIdentityKey) {
                return false
            }

            if (!shareConfirmSign.isNullOrEmpty()
                    && !shareSettingSign.isNullOrEmpty()
                    && !shareSetting.isNullOrEmpty() && !infoSecret.isNullOrEmpty()) {
                val setting = parseShareSetting(shareSetting, infoSecret)
                if (null != setting) {
                    if (dbGroupInfo.shareEpoch == null || dbGroupInfo.shareEpoch <= setting.share_epoch) {
                        if (verifySign(ownerIdentityKey, shareSetting, shareSettingSign, ownerConfirm, shareConfirmSign)) {
                            dbGroupInfo.shareCodeSetting = shareSetting
                            dbGroupInfo.shareCodeSettingSign = shareSettingSign
                            dbGroupInfo.shareSettingAndConfirmSign = shareConfirmSign
                            dbGroupInfo.needOwnerConfirm = ownerConfirm
                            dbGroupInfo.infoSecret = infoSecret
                            dbGroupInfo.shareCode = setting.share_code
                            dbGroupInfo.shareEnabled = setting.share_enabled
                            dbGroupInfo.shareEpoch = setting.share_epoch

                            ALog.i("GroupInfoEntity", "parse group:${dbGroupInfo.gid} info succeed")
                            return true
                        }
                        else if (!verifySign(ownerIdentityKey,
                                        dbGroupInfo.shareCodeSetting,
                                        dbGroupInfo.shareCodeSettingSign,
                                        dbGroupInfo.needOwnerConfirm,
                                        dbGroupInfo.shareSettingAndConfirmSign)) {
                            dbGroupInfo.shareEnabled = 0
                            dbGroupInfo.shareCode = ""
                            dbGroupInfo.shareCodeSetting = ""
                            dbGroupInfo.shareCodeSettingSign = ""
                            dbGroupInfo.shareSettingAndConfirmSign = ""
                        }
                    } else {
                        ALog.i("GroupInfoEntity", "parse group:${dbGroupInfo.gid} epoch error")
                    }
                }
            } else {
                ALog.w("GroupInfoEntity", "s:${shareSetting?.length} sn:${shareSettingSign?.length} scm:${shareConfirmSign?.length} ins:${infoSecret?.length}")
            }
        } catch (e:Exception) {
            ALog.e("GroupInfoEntity", "share setting parse failed", e)
        }

        return false
    }

    fun verifySign(ownerIdentityKey:String, shareSetting:String, shareSettingSign:String, shareConfirm:Int, shareConfirmSign:String): Boolean {
        if (shareSetting.isEmpty() || shareSettingSign.isEmpty() || shareConfirmSign.isEmpty()) {
            return false
        }

        val shareSettingByteArray = EncryptUtils.base64Decode(shareSetting.toByteArray())

        val format = ByteArrayOutputStream()
        format.write(shareSettingByteArray)
        format.write(shareConfirm.toString().toByteArray())
        val shareSettingWithConfirmByteArray = format.toByteArray()

        val pubkey = BCMPrivateKeyUtils.identityKey2PublicKey(EncryptUtils.base64Decode(ownerIdentityKey.toByteArray()))
        if(!BCMEncryptUtils.verifySignature(pubkey, shareSettingByteArray, EncryptUtils.base64Decode(shareSettingSign.toByteArray()))){
            ALog.e("GroupInfoEntity", "verifySign shareSettingSign failed")
            return false
        }

        if(!BCMEncryptUtils.verifySignature(pubkey, shareSettingWithConfirmByteArray, EncryptUtils.base64Decode(shareConfirmSign.toByteArray()))){
            ALog.e("GroupInfoEntity", "verifySign shareConfirmSign failed")
            return false
        }

        return true
    }

    private fun parseShareSetting(shareSetting: String, infoSecret:String): GroupShareSettingEntity? {
        try {
            val json = EncryptUtils.aes256DecryptAndBase64(shareSetting, EncryptUtils.base64Decode(infoSecret.toByteArray()))
            return GsonUtils.fromJson<GroupShareSettingEntity>(json, GroupShareSettingEntity::class.java)
        } catch (e:Exception) {
            ALog.e("GroupInfoEntity", "parseShareSetting", e)
        }
        return null
    }
}