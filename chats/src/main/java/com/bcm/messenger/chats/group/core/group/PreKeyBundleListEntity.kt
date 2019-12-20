package com.bcm.messenger.chats.group.core.group

import com.bcm.messenger.common.utils.base64Decode
import com.bcm.messenger.common.utils.identityKey
import com.bcm.messenger.common.utils.publicKey
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.proguard.NotGuard
import com.google.gson.annotations.SerializedName
import org.whispersystems.libsignal.ecc.ECPublicKey
import org.whispersystems.libsignal.state.PreKeyBundle

data class PreKeyBundleListEntity(
        @SerializedName("keys")
        val list:List<PreKeyBundleEntity>?): NotGuard {

    data class PreKeyBundleEntity(
            @SerializedName("uid")
            val uid:String,
            @SerializedName("device_id")
            val deviceId:Int,
            @SerializedName("identity_key")
            val identityKey: String,
            @SerializedName("registration_id")
            val registrationId:Int,
            @SerializedName("signed_prekey")
            val signedPreKey:SignedPreKeyEntity?,
            @SerializedName("onetime_key")
            val preKey:PreKeyEntity?,
            @SerializedName("state")
            val state:Int//0 login，1 logout
    ): NotGuard

    fun getPreKeyBundleList(validSet:Set<String>):List<PreKeyBundle> {
        val list = this.list?:return listOf()

        val bundles = ArrayList<PreKeyBundle>()
        for (entity in list) {
            if (!validSet.contains(entity.uid)) {
                ALog.e("PreKeyBundleListEntity", "illegal member found")
                continue
            }
            var preKey: ECPublicKey? = null
            var signedPreKey: ECPublicKey? = null
            var signedPreKeySignature: ByteArray? = null
            var preKeyId = -1
            var signedPreKeyId = -1

            if (entity.signedPreKey != null && entity.signedPreKey.publicKey?.isNotEmpty() == true) {
                signedPreKey = entity.signedPreKey.publicKey.publicKey()
                signedPreKeyId = entity.signedPreKey.keyId
                signedPreKeySignature = entity.signedPreKey.signature.toByteArray().base64Decode()
            }

            if (entity.preKey != null && entity.preKey.publicKey?.isNotEmpty() == true) {
                preKeyId = entity.preKey.keyId
                preKey = entity.preKey.publicKey.publicKey()
            }

            bundles.add(PreKeyBundle(entity.registrationId, entity.deviceId, preKeyId,
                    preKey, signedPreKeyId, signedPreKey, signedPreKeySignature,
                    entity.identityKey.identityKey()))
        }
        return bundles
    }

    open class PreKeyEntity(@SerializedName("keyId")
                            val keyId: Int,
                            @SerializedName("publicKey")
                            val publicKey: String?):NotGuard

    class SignedPreKeyEntity(keyId: Int, publicKey: String, @SerializedName("signature")
                                  val signature: String):PreKeyEntity(keyId, publicKey)


}