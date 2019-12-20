package com.bcm.messenger.chats.group.core.group

import android.text.TextUtils
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.core.corebean.GroupMemberSyncState
import com.bcm.messenger.common.crypto.GroupProfileDecryption
import com.bcm.messenger.common.grouprepository.room.entity.GroupInfo
import com.bcm.messenger.common.utils.aesDecode
import com.bcm.messenger.common.utils.base64Decode
import com.bcm.messenger.common.utils.base64Encode
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.utils.format
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.proguard.NotGuard
import com.google.gson.annotations.SerializedName
import com.orhanobut.logger.Logger

/**
 * ling created in 2018/5/23
 */
class GroupInfoEntity : NotGuard {
    var gid: Long = 0
    var name: String? = null
    @SerializedName("encrypted_name")
    var encryptedName: String? = null // Base64 encoded
    var icon: String? = null
    @SerializedName("encrypted_icon")
    var encryptedIcon: String? = null // Base64 encoded
    var last_mid: Long = 0
    var last_ack_mid: Long = 0
    var create_time: Long = 0
    var permission: Int = 0
    var broadcast: Int = 0
    var member_cn: Int = 0
    var channel: String? = null
    var encrypted_key: String? = null
    var encrypted: Int = 0//0:not encrypted，1:encrypted
    var role = 0//1:group owner，2:manager，3:member，4: subscriber
    var plain_channel_key: String? = null
    var notice: NoticeBean? = null//group announcement
    @SerializedName("encrypted_notice")
    var encryptedNotice: String? = null // Base64 encoded
    /**
     * subscriber number
     */
    var subscriber_cn: Int = 0
    var owner: String? = null
    var intro: String? = null
    var share_qr_code_setting: String? = null
    var owner_confirm: Int = 0
    var share_sig: String? = null
    var share_and_owner_confirm_sig: String? = null
    var group_info_secret: String? = null
    var encrypted_ephemeral_key: String? = null
    var version:Int = 0

    fun toDbGroup(dbGroupInfo:GroupInfo, ownerIdentityKey:String?, parseCount:Boolean): GroupInfo {
        if (parseCount) {
            dbGroupInfo.member_count = member_cn
            dbGroupInfo.subscriber_count = subscriber_cn
        }

        dbGroupInfo.permission = permission
        dbGroupInfo.createTime = create_time
        dbGroupInfo.iconUrl = icon?.trim()?:""
        dbGroupInfo.name = name
        dbGroupInfo.owner = owner
        dbGroupInfo.broadcast = broadcast
        dbGroupInfo.share_content = intro
        dbGroupInfo.share_url = channel
        dbGroupInfo.gid = gid
        dbGroupInfo.role = role.toLong()
        dbGroupInfo.member_sync_state = GroupMemberSyncState.DIRTY.toString()
        dbGroupInfo.version = version

        val notice = this.notice
        if (null != notice) {
            dbGroupInfo.notice_content = notice.content
            dbGroupInfo.notice_update_time = notice.updateTime
        }

        if (!TextUtils.isEmpty(encrypted_key)) {
            val result: Pair<String, Int> = BCMEncryptUtils.decryptGroupPassword(encrypted_key)
            if (result.second == GroupInfo.LEGITIMATE_GROUP) {
                dbGroupInfo.illegal = GroupInfo.LEGITIMATE_GROUP
                val key = when (dbGroupInfo.role) {
                    1L, 2L, 3L -> result.first
                    else -> null
                }

                if (key != null) {
                    if (TextUtils.isEmpty(dbGroupInfo.currentKey)) {
                        dbGroupInfo.currentKey = key
                    } else {
                        ALog.i("GroupInfoEntity", "group key exist ${dbGroupInfo.gid}")
                    }
                }
            } else {
                Logger.e("GroupLogic ILLEGAL groupName =" + dbGroupInfo.name + " gid =" + dbGroupInfo.gid)
            }
        }

        val shareSettingSign = this.share_sig
        val shareConfirmSign = this.share_and_owner_confirm_sig
        val shareSetting = this.share_qr_code_setting
        val ownerConfirm = this.owner_confirm
        try {
            val infoSecret = BCMEncryptUtils.decryptGroupPassword(this.group_info_secret).first

            if (TextUtils.isEmpty(dbGroupInfo.shareCodeSetting)) {
                if(GroupShareSettingUtil.parseIntoGroupInfo(ownerIdentityKey,
                        shareSetting,
                        shareSettingSign,
                        shareConfirmSign,
                        infoSecret,
                        ownerConfirm,
                        dbGroupInfo)) {
                    if (TextUtils.isEmpty(dbGroupInfo.infoSecret)) {
                        dbGroupInfo.infoSecret = infoSecret
                    }
                } else if(TextUtils.isEmpty(dbGroupInfo.infoSecret)){
                    dbGroupInfo.infoSecret = infoSecret
                }
            } else if (dbGroupInfo.role == AmeGroupMemberInfo.OWNER) {
                if(ownerIdentityKey == null || !GroupShareSettingUtil.verifySign(ownerIdentityKey,
                                dbGroupInfo.shareCodeSetting,
                                dbGroupInfo.shareCodeSettingSign,
                                dbGroupInfo.needOwnerConfirm,
                                dbGroupInfo.shareSettingAndConfirmSign)) {
                    dbGroupInfo.shareEnabled = 0
                    dbGroupInfo.shareCode = ""
                    dbGroupInfo.shareCodeSetting = ""
                    dbGroupInfo.shareCodeSettingSign = ""
                    dbGroupInfo.shareSettingAndConfirmSign = ""
                    dbGroupInfo.shareLink = ""
                }
            }
        } catch (e:Exception) {
            ALog.e("GroupInfoEntity", "decrypt secret key failed", e)
        }

        if (dbGroupInfo.infoSecret != null && dbGroupInfo.ephemeralKey.isNullOrEmpty()) {
            val encEk = encrypted_ephemeral_key
            if (!encEk.isNullOrEmpty()) {
                val ek = encEk.toByteArray().base64Decode()
                        .aesDecode(dbGroupInfo.infoSecret.toByteArray().base64Decode())?.base64Encode()?.format()
                if (ek?.length == 64) {
                    dbGroupInfo.ephemeralKey = ek
                }
            }
        }

        try {
            if (encryptedName.isNullOrBlank()) {
                dbGroupInfo.name = name
            } else {
                val decryptName = GroupProfileDecryption.decryptProfile(encryptedName!!, dbGroupInfo.groupPrivateKey)
                if (decryptName != null) {
                    dbGroupInfo.name = decryptName
                    dbGroupInfo.isProfileEncrypted = true
                }
            }

            if (encryptedIcon.isNullOrBlank()) {
                dbGroupInfo.iconUrl = icon?.trim().orEmpty()
            } else {
                val decryptIcon = GroupProfileDecryption.decryptProfile(encryptedIcon!!, dbGroupInfo.groupPrivateKey)
                if (decryptIcon != null) {
                    dbGroupInfo.iconUrl = decryptIcon
                    dbGroupInfo.isProfileEncrypted = true
                }
            }

            if (encryptedNotice.isNullOrBlank()) {
                if (notice != null) {
                    dbGroupInfo.notice_content = notice.content
                    dbGroupInfo.notice_update_time = notice.updateTime
                }
            } else {
                val decryptNotice = GroupProfileDecryption.decryptProfile(encryptedNotice!!, dbGroupInfo.groupPrivateKey)
                if (!decryptNotice.isNullOrBlank()) {
                    val noticeBean = GsonUtils.fromJson(decryptNotice, NoticeBean::class.java)
                    dbGroupInfo.notice_content = noticeBean.content
                    dbGroupInfo.notice_update_time = noticeBean.updateTime
                    dbGroupInfo.isProfileEncrypted = true
                }
            }
        } catch (tr: Throwable) {
            ALog.e("GroupInfoEntity", "Decrypt group profile failed", tr)
        }

        return dbGroupInfo
    }

    class NoticeBean : NotGuard {
        /**
         * content : test notice 2
         * updateTime : 1543397335444
         */
        var content: String? = null
        var updateTime: Long = 0
    }
}