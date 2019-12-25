package com.bcm.messenger.chats.group.logic.secure

import com.bcm.messenger.chats.group.core.GroupMemberCore
import com.bcm.messenger.common.crypto.storage.SignalProtocolStoreImpl
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.utils.BCMPrivateKeyUtils
import com.bcm.messenger.common.utils.base64Decode
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.utils.privateKey
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.google.protobuf.ByteString
import io.reactivex.Observable
import org.whispersystems.libsignal.InvalidKeyException
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.libsignal.ecc.ECPrivateKey
import org.whispersystems.libsignal.kdf.HKDFv3
import org.whispersystems.libsignal.state.PreKeyBundle
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.collections.HashMap

object StrongKeyExchangeParam {
    private const val TAG = "GroupKeyExchangeParam"

    data class ParamIndex (val uid:String, val deviceId:Int)

    private fun getDiscontinuityBytes(): ByteArray {
        val discontinuity = ByteArray(32)
        Arrays.fill(discontinuity, 0xFF.toByte())
        return discontinuity
    }

    fun getStrongKeysContent(uidList: List<String>,
                             groupKey: ByteArray,
                             groupInfoSecretPrivateKey: ByteArray): Observable<GroupKeysContent> {
        return GroupMemberCore.getPreKeyBundles(uidList)
                .subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AmeDispatcher.ioScheduler)
                .map { bundleList ->
                    val map = HashMap<ParamIndex, GroupKeyExchange.StrongKeyParams>()
                    bundleList.getPreKeyBundleList(uidList.toSet()).forEach {
                        val param = Builder()
                                .withBundle(it)
                                .withUid(BCMPrivateKeyUtils.provideUid(it.identityKey.serialize()))
                                .withKey(groupKey)
                                .withGroupInfoSecretPrivateKey(groupInfoSecretPrivateKey)
                                .aliceBuild()

                        if (null != param) {
                            val uid = BCMPrivateKeyUtils.provideUid(it.identityKey.serialize())
                            if (uidList.contains(uid)) {
                                val index = ParamIndex(uid, it.deviceId)
                                map[index] = param
                            }
                        }
                    }

                    val strongKeyList = mutableListOf<GroupKeysContent.StrongKeyContent>()

                    uidList.forEach { uid ->
                        val params = map.filterKeys { it.uid == uid }
                        if (params.isNotEmpty()) {
                            for ((index,param) in params) {
                                val strongKey = EncryptUtils.base64Encode(param.toByteString().toByteArray())
                                strongKeyList.add(GroupKeysContent.StrongKeyContent(uid, String(strongKey), index.deviceId))
                            }
                        } else {
                            strongKeyList.add(GroupKeysContent.StrongKeyContent(uid, null, 0))
                        }
                    }

                   GroupKeysContent(strongModeKeys = strongKeyList)
                }
    }

    fun getStrongKeysContent(keyBundleList:List<PreKeyBundle>,
                             groupKey: ByteArray,
                             groupInfoSecretPrivateKey: ByteArray): GroupKeysContent {
        val map = HashMap<ParamIndex, GroupKeyExchange.StrongKeyParams>()
        keyBundleList.forEach {
            val param = Builder()
                    .withBundle(it)
                    .withUid(BCMPrivateKeyUtils.provideUid(it.identityKey.serialize()))
                    .withKey(groupKey)
                    .withGroupInfoSecretPrivateKey(groupInfoSecretPrivateKey)
                    .aliceBuild()

            if (null != param) {
                val uid = BCMPrivateKeyUtils.provideUid(it.identityKey.serialize())
                val index = ParamIndex(uid, it.deviceId)
                map[index] = param
            }
        }

        val strongKeyList = mutableListOf<GroupKeysContent.StrongKeyContent>()

        for ((index,param) in map) {
            val strongKey = EncryptUtils.base64Encode(param.toByteString().toByteArray())
            strongKeyList.add(GroupKeysContent.StrongKeyContent(index.uid, String(strongKey), index.deviceId))
        }

        return GroupKeysContent(strongModeKeys = strongKeyList)
    }

    fun strongKeyContentToGroupKey(content:GroupKeysContent.StrongKeyContent, infoSecretPublicKey:ByteArray):ByteArray? {
        if(content.uid == AMELogin.uid && content.key?.isNotEmpty() == true ) {
            val params = GroupKeyExchange.StrongKeyParams.parseFrom(content.key.base64Decode())
            val parser = Parser()
            parser.withGroupInfoSecretPublicKey(infoSecretPublicKey)
            parser.withParam(params)

            return parser.bobParse()
        }
        return null
    }

    class Builder {
        private lateinit var preKeyBundle: PreKeyBundle
        private lateinit var groupKey: ByteArray
        private lateinit var uid: String
        private lateinit var groupInfoSecretPrivateKey: ByteArray

        fun withBundle(preKeyBundle: PreKeyBundle): Builder {
            this.preKeyBundle = preKeyBundle
            return this
        }

        fun withKey(key: ByteArray): Builder {
            this.groupKey = key
            return this
        }

        fun withUid(uid: String): Builder {
            this.uid = uid
            return this
        }

        fun withGroupInfoSecretPrivateKey(key: ByteArray): Builder {
            this.groupInfoSecretPrivateKey = key
            return this
        }

        fun aliceBuild(): GroupKeyExchange.StrongKeyParams? {
            val builder = GroupKeyExchange.StrongKeyParams.newBuilder()
            val baseKeyPair = Curve.generateKeyPair()
            val myPrivateKey: ECPrivateKey = Curve.decodePrivatePoint(BCMEncryptUtils.getMyPrivateKey(AppContextHolder.APP_CONTEXT))

            if (preKeyBundle.signedPreKey != null && !Curve.verifySignature(preKeyBundle.identityKey.publicKey,
                            preKeyBundle.signedPreKey.serialize(),
                            preKeyBundle.signedPreKeySignature)) {
                ALog.e(TAG, "Invalid signature on device key!")
                return null
            }

            if (preKeyBundle.signedPreKey == null) {
                ALog.e(TAG, "No signed prekey!")
                return null
            }

            val secrets = ByteArrayOutputStream()

            secrets.write(getDiscontinuityBytes())

            secrets.write(Curve.calculateAgreement(preKeyBundle.signedPreKey,
                    myPrivateKey))
            secrets.write(Curve.calculateAgreement(preKeyBundle.identityKey.publicKey,
                    baseKeyPair.privateKey))
            secrets.write(Curve.calculateAgreement(preKeyBundle.signedPreKey,
                    baseKeyPair.privateKey))

            if (preKeyBundle.preKey != null && preKeyBundle.preKeyId > 0) {
                secrets.write(Curve.calculateAgreement(preKeyBundle.preKey,
                        baseKeyPair.privateKey))

                builder.prekeyId = preKeyBundle.preKeyId
            } else {
                builder.prekeyId = 0
            }

            val kdf = HKDFv3()
            val key = kdf.deriveSecrets(secrets.toByteArray(), "group.key.exchange".toByteArray(), 64)

            builder.basePublicKey = ByteString.copyFrom(baseKeyPair.publicKey.serialize())
            builder.alicePublickey = ByteString.copyFrom(BCMEncryptUtils.getMyIdentityKey(AppContextHolder.APP_CONTEXT))
            builder.aliceUid = AMELogin.uid
            builder.signedPrekeyId = preKeyBundle.signedPreKeyId

            val encryptedKey = EncryptUtils.aes256Encrypt(groupKey, key)
            builder.encyptedGroupKey = ByteString.copyFrom(encryptedKey)

            try {
                val sign = Curve.calculateSignature(groupInfoSecretPrivateKey.privateKey(), groupKey)
                builder.signature = ByteString.copyFrom(sign)
            } catch (e: InvalidKeyException) {
                ALog.e(TAG, "aliceBuild param failed", e)
                return null
            }
            return builder.build()
        }
    }

    class Parser {
        private lateinit var params: GroupKeyExchange.StrongKeyParams
        private lateinit var groupInfoSecretPublicKey: ByteArray

        fun withParam(params: GroupKeyExchange.StrongKeyParams): Parser {
            this.params = params
            return this
        }

        fun withGroupInfoSecretPublicKey(key: ByteArray): Parser {
            groupInfoSecretPublicKey = key
            return this
        }

        /**
         * return group key plain text
         */
        fun bobParse(): ByteArray? {

            try {
                val myPrivateKey = Curve.decodePrivatePoint(BCMEncryptUtils.getMyPrivateKey(AppContextHolder.APP_CONTEXT))
                val theirIdentityKey = Curve.decodePoint(params.alicePublickey.toByteArray(), 0)
                val basePublicKey = Curve.decodePoint(params.basePublicKey.toByteArray(), 0)

                val keyStore = SignalProtocolStoreImpl(AppContextHolder.APP_CONTEXT)

                val prekey = if (params.prekeyId > 0) {
                    val k = keyStore.loadPreKey(params.prekeyId)
                    if (k == null) {
                        ALog.e(TAG, "bobParse failed, preKey not found")
                        return null
                    }
                    k
                } else {
                    null
                }

                val signedPreKey = keyStore.loadSignedPreKey(params.signedPrekeyId)
                if (null == signedPreKey) {
                    ALog.e(TAG, "bobParse failed, signedPreKey not found")
                    return null
                }
                val secrets = ByteArrayOutputStream()

                secrets.write(getDiscontinuityBytes())

                secrets.write(Curve.calculateAgreement(theirIdentityKey,
                        signedPreKey.keyPair.privateKey))
                secrets.write(Curve.calculateAgreement(basePublicKey,
                        myPrivateKey))
                secrets.write(Curve.calculateAgreement(basePublicKey,
                        signedPreKey.keyPair.privateKey))

                if (prekey != null) {
                    secrets.write(Curve.calculateAgreement(basePublicKey,
                            prekey.keyPair.privateKey))

                    keyStore.removePreKey(prekey.id)

                    AmeModuleCenter.login().refreshPrekeys()
                }

                val kdf = HKDFv3()
                val key = kdf.deriveSecrets(secrets.toByteArray(), "group.key.exchange".toByteArray(), 64)

                val result = EncryptUtils.aes256Decrypt(params.encyptedGroupKey.toByteArray(), key)

                val infoSecretPublicKey = BCMPrivateKeyUtils.identityKey2PublicKey(groupInfoSecretPublicKey)
                if (null != result) {
                    if (BCMEncryptUtils.verifySignature(infoSecretPublicKey, result, params.signature.toByteArray())) {
                        return result
                    } else {
                        ALog.e(TAG, "bobParse failed, signature verify failed")
                    }
                } else {
                    ALog.e(TAG, "bobParse failed, decrypted failed")
                }
            } catch (e:Throwable) {
                ALog.e(TAG, "Parse group key failed", e)
                return null
            }

            return null
        }
    }
}