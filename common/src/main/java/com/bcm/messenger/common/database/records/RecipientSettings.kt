package com.bcm.messenger.common.database.records

import androidx.room.Ignore
import com.bcm.messenger.common.config.BcmFeatureSupport
import com.bcm.messenger.utility.Base64
import com.bcm.messenger.utility.GsonUtils
import org.spongycastle.util.encoders.DecoderException
import java.io.IOException

/**
 * Created by Kin on 2019/9/25
 */
class RecipientSettings() : RecipientModel() {

    companion object {
        const val CONTACT_SYNC_VERSION = 2
    }

    constructor(
            uid: String,
            isBlock: Boolean,
            muteUntil: Long,
            expiresTime: Long,
            localName: String?,
            localAvatar: String?,
            profileKey: ByteArray?,
            profileName: String?,
            profileAvatar: String?,
            isProfileSharing: Boolean,
            privacyProfile: PrivacyProfile,
            relationship: Int,
            featureSupport: BcmFeatureSupport?) : this() {

        this.uid = uid
        this.block = if (isBlock) 1 else 0
        this.muteUntil = muteUntil
        this.expiresTime = expiresTime
        this.localName = localName
        this.localAvatar = localAvatar
        if (profileKey != null) {
            try {
                this.profileKey = Base64.encodeBytes(profileKey)
            } catch (e: Exception) {}
        }
        this.profileName = profileName
        this.profileAvatar = profileAvatar
        this.profileSharingApproval = if (isProfileSharing) 1 else 0
        this.privacyProfile = privacyProfile
        this.relationship = relationship
        if (featureSupport != null) {
            this.supportFeature = featureSupport.toString()
        }
    }

    constructor(uid: String) : this() {
        this.uid = uid
    }

    @Ignore
    var contactVersion: Int = CONTACT_SYNC_VERSION //

    fun isBlocked() = block == 1

    fun getProfileKeyByteArray(): ByteArray {
        return try {
            if (profileKey.isNullOrEmpty()) {
                byteArrayOf()
            } else {
                Base64.decode(profileKey)
            }
        } catch (e: IOException) {
            byteArrayOf()
        }
    }

    fun setProfileKeyByteArray(profileKey: ByteArray?) {
        if (profileKey == null) return
        try {
            this.profileKey = Base64.encodeBytes(profileKey)
        } catch (e: IOException) { }
    }

    fun getFeatureSupport(): BcmFeatureSupport? {
        if (supportFeature.isEmpty()) return null
        return try {
            BcmFeatureSupport(supportFeature)
        } catch (e: DecoderException) {
            null
        }
    }

    fun isProfileSharing() = profileSharingApproval == 1

    fun setLocalProfile(localName: String?, localAvatar: String?) {
        this.localName = localName
        this.localAvatar = localAvatar
    }

    fun setTemporaryProfile(profileName: String?, profileAvatar: String?) {
        this.profileName = profileName
        this.profileAvatar = profileAvatar
    }

    fun setPrivacyKey(nameKey: String?, avatarKey: String?) {
        this.privacyProfile.nameKey = nameKey
        this.privacyProfile.avatarKey = avatarKey
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        val os = other as? RecipientSettings

        return this.uid == os?.uid
    }

    override fun hashCode(): Int {
        return uid.hashCode()
    }

    override fun toString(): String {
        return GsonUtils.toJson(this)
    }


}