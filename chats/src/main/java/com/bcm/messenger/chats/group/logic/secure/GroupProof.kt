package com.bcm.messenger.chats.group.logic.secure

import com.bcm.messenger.common.grouprepository.room.entity.GroupInfo
import com.bcm.messenger.common.utils.BCMPrivateKeyUtils
import com.bcm.messenger.common.utils.base64Decode
import com.bcm.messenger.common.utils.base64Encode
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.utils.format
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.proguard.NotGuard
import com.google.gson.annotations.SerializedName
import org.whispersystems.libsignal.ecc.Curve

object GroupProof {
    private const val MEMBER_PROOF_VERSION = 0

    data class GroupMemberProof(
            @SerializedName("proof")
            val proof:String?,
            @SerializedName("encrypt_version")
            val version:Int = MEMBER_PROOF_VERSION
    ):NotGuard

    fun encodeMemberProof(proof:GroupMemberProof): String {
        return GsonUtils.toJson(proof).toByteArray().base64Encode().format()
    }

    fun decodeMemberProof(proofString:String?): GroupMemberProof? {
        if (proofString.isNullOrEmpty()) {
            return null
        }

        val json = proofString.toByteArray().base64Decode().format()
        try {
            return GsonUtils.fromJson(json, GroupMemberProof::class.java)
        } catch (e:Throwable) {
            ALog.e("GroupProof", "decodeMemberProof", e)
        }
        return null
    }

    fun checkMember(groupInfo:GroupInfo, uid:String, proof:GroupMemberProof): Boolean {
        if (proof.proof.isNullOrEmpty()) {
            return false
        }

        val proofBytes = EncryptUtils.base64Decode(proof.proof.toByteArray())
        if (null == proofBytes) {
            ALog.e("GroupMemberProof", "proof base decode failed")
            return false
        }

        val pubKey = BCMPrivateKeyUtils.identityKey2PublicKey(groupInfo.groupPublicKey)
        return BCMEncryptUtils.verifySignature(pubKey, uid.toByteArray(), proofBytes)
    }

    fun signMember(groupInfo: GroupInfo, uid: String): GroupMemberProof {
        if (!groupInfo.infoSecret.isNullOrEmpty()) {
            try {
                val sign = Curve.calculateSignature(Curve.decodePrivatePoint(groupInfo.groupPrivateKey), uid.toByteArray())
                return GroupMemberProof(String(EncryptUtils.base64Encode(sign)))
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
        return GroupMemberProof(null)
    }
}