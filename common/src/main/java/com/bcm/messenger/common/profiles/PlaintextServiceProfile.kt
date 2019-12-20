package com.bcm.messenger.common.profiles

import com.google.gson.annotations.SerializedName
import com.orhanobut.logger.Logger
import org.whispersystems.signalservice.api.profiles.ISignalProfile
import org.whispersystems.signalservice.internal.util.Base64
import java.nio.charset.Charset

/**
 * 明文的联系人profile信息
 * Created by wjh on 2018/7/4
 */
class PlaintextServiceProfile : ISignalProfile {

    override fun getEncryptPhone(): String? {
        return encryptNumber
    }

    override fun getEncryptPubkey(): String? {
        return numberPubkey
    }

    @SerializedName("profileKey")
    private val profileKey: String? = null

    @SerializedName(value = "identityKey")
    private val identityKey: String? = null

    @SerializedName(value = "name")
    private val namePlaintext: String? = null

    @SerializedName(value = "nickname")
    private val encryptName: String? = null

    @SerializedName(value = "avatar")
    private val avatarNamePlaintext: String? = null

    @SerializedName(value = "ldAvatar")
    private val encryptAvatarLD: String? = null

    @SerializedName(value = "hdAvatar")
    private val encryptAvatarHD: String? = null

    @SerializedName(value = "number")
    private val phone: String? = null

    @SerializedName("encryptNumber")
    private val encryptNumber: String? = null

    @SerializedName("numberPubkey")
    private val numberPubkey: String? = null

    @SerializedName("privacy")
    private val privacy: ProfilePrivacy? = null

    @SerializedName("features")
    private val features: String? = null

    override fun getName(): String? {
        try {
            return String(Base64.decodeWithoutPadding(namePlaintext), Charset.forName("UTF-8"))
        } catch (ex: Exception) {
            Logger.e("PlaintextServiceProfile getName error", ex)
        }
        return null
    }

    override fun getPhone(): String? {
        return phone
    }

    override fun getAvatar(): String? {
        return avatarNamePlaintext
    }

    override fun getIdentityKey(): String? {
        return identityKey
    }

    override fun getProfileKey(): String? {
        return profileKey
    }

    override fun getProfileKeyArray(): ByteArray? {
        try {
            return Base64.decode(profileKey ?: return null)
        } catch (ex: Exception) {
            Logger.e("PlaintextServiceProfile getProfileKeyArray fail", ex)
        }
        return null
    }

    override fun getProfileBackupTime(): Long {
        return 0L
    }

    override fun isAllowStrangerMessages(): Boolean {
        return privacy?.allowStrangerMessage ?: true
    }

    override fun getEncryptName(): String? {
        return encryptName
    }

    override fun getEncryptAvatarLD(): String? {
        return encryptAvatarLD
    }

    override fun getEncryptAvatarHD(): String? {
        return encryptAvatarHD
    }

    override fun getSupportFeatures(): String? {
        return features
    }
}