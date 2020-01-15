package com.bcm.messenger.common.crypto

import com.bcm.messenger.common.utils.privateKey
import com.bcm.messenger.common.utils.publicKey
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.libsignal.ecc.ECPrivateKey
import org.whispersystems.libsignal.ecc.ECPublicKey
import org.whispersystems.libsignal.kdf.HKDFv3
import org.whispersystems.signalservice.internal.util.Util
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import javax.crypto.*
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object ECCCipher {
    private const val VERSION = 1
    private const val PUBKEY_LEN = 33
    private const val PRIVATEKEY_LEN = 32

    fun encrypt(pubKey: ECPublicKey, plainData: ByteArray): ByteArray {
        try {
            val tmpKeyPair = Curve.generateKeyPair()

            val builder = encrypt(pubKey, tmpKeyPair.privateKey, plainData)

            builder.version = VERSION
            builder.cipherkey = ByteString.copyFrom(tmpKeyPair.publicKey.serialize())

            return builder.build().toByteArray()
        } catch (e: Throwable) {
            throw IOException(e)
        }
    }

    @Throws(IOException::class)
    fun decrypt(privKey: ECPrivateKey, cipherData: ByteArray): ByteArray {
        try {
            val data = ECCipherProto.ECCipherData.parseFrom(cipherData)
            if (data.version != VERSION) {
                throw IOException("not support version")
            }

            if (data.cipherkey.size() != PUBKEY_LEN) {
                throw IOException("wrong pubkey")
            }

            val pubKey = data.cipherkey.toByteArray().publicKey()
            return decrypt(data, pubKey, privKey)
        } catch (e: InvalidProtocolBufferException) {
            throw IOException(e)
        } catch (e: IOException) {
            throw e
        } catch (e: Throwable) {
            throw IOException(e)
        }
    }

    fun encrypt(privKey: ECPrivateKey, plainData: ByteArray): ByteArray {
        try {
            val tmpKeyPair = Curve.generateKeyPair()

            val builder = encrypt(tmpKeyPair.publicKey, privKey, plainData)

            builder.version = VERSION
            builder.cipherkey = ByteString.copyFrom(tmpKeyPair.privateKey.serialize())

            return builder.build().toByteArray()
        } catch (e: Throwable) {
            throw IOException(e)
        }
    }

    fun decrypt(pubKey: ECPublicKey, cipherData: ByteArray): ByteArray {
        try {
            val data = ECCipherProto.ECCipherData.parseFrom(cipherData)
            if (data.version != VERSION) {
                throw IOException("not support version")
            }

            if (data.cipherkey.size() != PRIVATEKEY_LEN) {
                throw IOException("wrong private key")
            }

            val privKey = data.cipherkey.toByteArray().privateKey()
            return decrypt(data, pubKey, privKey)
        } catch (e: InvalidProtocolBufferException) {
            throw IOException(e)
        } catch (e: IOException) {
            throw e
        } catch (e: Throwable) {
            throw IOException(e)
        }
    }

    private fun encrypt(pubKey: ECPublicKey, privKey: ECPrivateKey, plainData: ByteArray): ECCipherProto.ECCipherData.Builder {
        val sharedSecret = Curve.calculateAgreement(pubKey, privKey)
        val derivedSecret = HKDFv3().deriveSecrets(sharedSecret, "BCM_ECC_CIPHER".toByteArray(), 64)
        val parts = Util.split(derivedSecret, 32, 32)
        val iv = Util.split(parts[1], 16, 16)
        val ciphertext = getCipherText(parts[0], iv[1], plainData)
        val mac = getMac(parts[1], plainData)

        val builder = ECCipherProto.ECCipherData.newBuilder()
        builder.ciphertext = ByteString.copyFrom(ciphertext)
        builder.mac = ByteString.copyFrom(mac)
        return builder
    }

    private fun decrypt(cipherData: ECCipherProto.ECCipherData, pubKey: ECPublicKey, privKey: ECPrivateKey): ByteArray {
        val sharedSecret = Curve.calculateAgreement(pubKey, privKey)
        val derivedSecret = HKDFv3().deriveSecrets(sharedSecret, "BCM_ECC_CIPHER".toByteArray(), 64)
        val parts = Util.split(derivedSecret, 32, 32)
        val iv = Util.split(parts[1], 16, 16)

        val plaintext = getPlainText(parts[0], iv[1], cipherData.ciphertext.toByteArray())
        val mac = getMac(parts[1], plaintext)

        if (!MessageDigest.isEqual(mac, cipherData.mac.toByteArray())) {
            throw IOException("mac verify failed")
        }
        return plaintext
    }

    private fun getPlainText(key: ByteArray, iv: ByteArray, cipherData: ByteArray): ByteArray {
        try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))

            return cipher.doFinal(cipherData)
        } catch (e: NoSuchAlgorithmException) {
            throw AssertionError(e)
        } catch (e: NoSuchPaddingException) {
            throw AssertionError(e)
        } catch (e: java.security.InvalidKeyException) {
            throw AssertionError(e)
        } catch (e: IllegalBlockSizeException) {
            throw AssertionError(e)
        } catch (e: BadPaddingException) {
            throw AssertionError(e)
        }
    }

    private fun getCipherText(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))

            return cipher.doFinal(data)
        } catch (e: NoSuchAlgorithmException) {
            throw AssertionError(e)
        } catch (e: NoSuchPaddingException) {
            throw AssertionError(e)
        } catch (e: java.security.InvalidKeyException) {
            throw AssertionError(e)
        } catch (e: IllegalBlockSizeException) {
            throw AssertionError(e)
        } catch (e: BadPaddingException) {
            throw AssertionError(e)
        }

    }

    private fun getMac(key: ByteArray, message: ByteArray): ByteArray {
        try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))

            return mac.doFinal(message)
        } catch (e: NoSuchAlgorithmException) {
            throw AssertionError(e)
        } catch (e: java.security.InvalidKeyException) {
            throw AssertionError(e)
        }

    }

}