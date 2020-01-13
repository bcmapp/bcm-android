package com.bcm.messenger.common.database.records

import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.provider.AmeModuleCenter
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
            val provider = AmeModuleCenter.app()
            return if (provider.testEnvEnable() && !provider.lbsEnable()) {
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
    var name: String? = null//
    var encryptedAvatarLD: String? = null
    var avatarLD: String? = null//（）
    var avatarLDUri: String? = null//uri
    var isAvatarLdOld = false//
    var encryptedAvatarHD: String? = null
    var avatarHD: String? = null//（）
    var avatarHDUri: String? = null//uri
    var isAvatarHdOld = false//
    var namePubKey: String? = null//DHnameKey
    var nameKey: String? = null//
    var avatarPubKey: String? = null//DHavatarKey
    var avatarKey: String? = null//
    var allowStranger = true
    var shortLink: String? = null//
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