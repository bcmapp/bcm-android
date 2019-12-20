package com.bcm.messenger.common.database.records

import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.IAmeAppModule
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.proguard.NotGuard
import org.json.JSONObject

/**
 * Created by Kin on 2019/9/25
 */
class PrivacyProfile : NotGuard {
    companion object {
        private const val TAG = "PrivacyProfile"
        //private const val SHARE_SHORT_LINK_PRE = "https://s.bcm.social/member/"

        const val CURRENT_VERSION = 1

        fun getShortLinkHost(): String {
            val provider = AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)
            return if (provider?.testEnvEnable() == true && !provider.lbsEnable()) {
                "https://39.108.124.60:9200/member/"
            }else {
                "https://s.bcm.social/member/"
            }
        }

        fun fromString(json: String): PrivacyProfile {
            if (json.isEmpty()) return PrivacyProfile()
            return try {
                GsonUtils.fromJson(json, PrivacyProfile::class.java)
            } catch (tr: Throwable) {
                PrivacyProfile()
            }
        }

        fun isShortLink(url: String?): Boolean {
            return url?.startsWith(getShortLinkHost()) ?: false
        }

        fun getMaxLDSize(): Int {
            return 50.dp2Px()
        }
    }

    var encryptedName: String? = null
    var name: String? = null//昵称
    var encryptedAvatarLD: String? = null
    var avatarLD: String? = null//头像地址（低清）
    var avatarLDUri: String? = null//保存路径uri
    var isAvatarLdOld = false//标识低清头像是否旧的
    var encryptedAvatarHD: String? = null
    var avatarHD: String? = null//头像地址（高清）
    var avatarHDUri: String? = null//保存路径uri
    var isAvatarHdOld = false//标识高清头像是否旧的
    var namePubKey: String? = null//用于DH出nameKey的外部公钥
    var nameKey: String? = null//昵称解密密钥
    var avatarPubKey: String? = null//用于DH出avatarKey的外部公钥
    var avatarKey: String? = null//头像解密密钥
    var allowStranger = true
    var shortLink: String? = null//二维码短链
    var version = CURRENT_VERSION

    fun setShortLink(shortIndex: String, hashBase62: String) {
        this.shortLink = "${getShortLinkHost()}$shortIndex#$hashBase62"
        ALog.d(TAG, "setShortLink: $shortLink")
    }

    fun getUploadKeys(): String {
        try {
            val json = JSONObject()
            json.put("namePubKey", this.namePubKey)
            json.put("avatarPubKey", this.avatarPubKey)
            json.put("version", this.version)
            val result = JSONObject()
            result.put("encrypt", json.toString())
            return result.toString()
        } catch (ex: Exception) {
            ALog.e(TAG, "getUploadPubKeys error", ex)
        }
        return ""
    }

    fun copy(): PrivacyProfile {
        val newProfile = PrivacyProfile()
        newProfile.name = name
        newProfile.encryptedName = encryptedName
        newProfile.namePubKey = namePubKey
        newProfile.nameKey = nameKey
        newProfile.avatarHD = avatarHD
        newProfile.avatarHDUri = avatarHDUri
        newProfile.avatarLD = avatarLD
        newProfile.avatarLDUri = avatarLDUri
        newProfile.avatarKey = avatarKey
        newProfile.avatarPubKey = avatarPubKey
        newProfile.isAvatarHdOld = isAvatarHdOld
        newProfile.isAvatarLdOld = isAvatarLdOld
        newProfile.allowStranger = allowStranger
        newProfile.version = version
        return newProfile
    }

    fun setPrivacyProfile(profile: PrivacyProfile) {
        this.name = profile.name
        this.encryptedName = profile.encryptedName
        this.namePubKey = profile.namePubKey
        this.nameKey = profile.nameKey
        this.avatarHD = profile.avatarHD
        this.avatarHDUri = profile.avatarHDUri
        this.avatarLD = profile.avatarLD
        this.avatarLDUri = profile.avatarLDUri
        this.avatarKey = profile.avatarKey
        this.avatarPubKey = profile.avatarPubKey
        this.isAvatarHdOld = profile.isAvatarHdOld
        this.isAvatarLdOld = profile.isAvatarLdOld
        this.allowStranger = profile.allowStranger
        this.version = profile.version
    }

    override fun toString(): String {
        return GsonUtils.toJson(this)
    }


}