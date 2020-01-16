package com.bcm.messenger.chats.group.logic.secure

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.utils.BCMPrivateKeyUtils
import com.bcm.messenger.common.utils.base64Decode
import com.bcm.messenger.common.utils.base64Encode
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.utils.format
import com.bcm.messenger.common.utils.log.ACLog
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.logger.ALog
import com.google.protobuf.ByteString
import io.reactivex.Observable
import org.whispersystems.libsignal.InvalidKeyException
import org.whispersystems.libsignal.ecc.Curve

object NormalKeyExchangeParam {
    fun getNormalKeysContent(accountContext: AccountContext, groupKey: ByteArray,
                             groupInfoSecret: ByteArray,
                             groupInfoSecretPrivateKey: ByteArray
    ): Observable<GroupKeysContent> {
        return Observable.create<GroupKeysContent> {
            val builder = GroupKeyExchange.NormalKeyParams.newBuilder()
            builder.encyptedGroupKey = ByteString.copyFrom(EncryptUtils.aes256Encrypt(groupKey, groupInfoSecret))
            try {
                val privateKey = Curve.decodePrivatePoint(groupInfoSecretPrivateKey)
                val sign = Curve.calculateSignature(privateKey, groupKey)
                builder.signature = ByteString.copyFrom(sign)
            } catch (e: InvalidKeyException) {
                ACLog.e(accountContext,  "NormalKeyExchangeParam", "aliceBuild param failed", e)
                throw e
            }

            val key = builder.build().toByteString().toByteArray().base64Encode().format()

            it.onNext(GroupKeysContent(normalModeKeys = GroupKeysContent.NormalKeyContent(key)))
            it.onComplete()
        }
    }

    fun normalKeyContentToGroupKey(accountContext:AccountContext, content: GroupKeysContent.NormalKeyContent,
                                  groupInfoSecret: ByteArray,
                                  groupInfoSecretPublicKey: ByteArray): ByteArray? {
        val params = GroupKeyExchange.NormalKeyParams.parseFrom(content.key.toByteArray().base64Decode())
        val groupKey = EncryptUtils.aes256Decrypt(params.encyptedGroupKey.toByteArray(), groupInfoSecret)
        if (null != groupKey) {
            val infoSecretPublicKey = BCMPrivateKeyUtils.identityKey2PublicKey(groupInfoSecretPublicKey)
            if (BCMEncryptUtils.verifySignature(infoSecretPublicKey, groupKey, params.signature.toByteArray())) {
                return groupKey
            } else {
                ACLog.e(accountContext,  "NormalKeyExchangeParam", "normalKeyParamsToGroupKey failed, signature verify failed")
            }
        }
        return null
    }
}