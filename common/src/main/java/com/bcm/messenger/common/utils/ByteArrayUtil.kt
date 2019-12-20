package com.bcm.messenger.common.utils

import android.util.Base64
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.HexUtil
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.ecc.*
import java.util.concurrent.ThreadLocalRandom

fun Byte.Companion.empty(): ByteArray {
    return ByteArray(0)
}

fun ByteArray.base64Encode(): ByteArray {
    return EncryptUtils.base64Encode(this)
}

fun ByteArray.base64Decode(): ByteArray {
    return EncryptUtils.base64Decode(this)
}

fun ByteArray.identityKey(): IdentityKey {
    return IdentityKey(Curve.decodePoint(this, 0))
}

fun ByteArray.publicKey(): ECPublicKey {
    return Curve.decodePoint(this, 0)
}

fun ByteArray.publicKey32(): ByteArray {
    if (this.size == 33) {
        return (Curve.decodePoint(this, 0) as DjbECPublicKey).publicKey
    }
    return this
}

fun ByteArray.privateKey(): ECPrivateKey {
    return Curve.decodePrivatePoint(this)
}

fun ByteArray.aesEncode(key:ByteArray): ByteArray? {
    return EncryptUtils.aes256Encrypt(this, key)
}

fun ByteArray.aesDecode(key:ByteArray): ByteArray? {
    return EncryptUtils.aes256Decrypt(this, key)
}

fun ByteArray.md5(): ByteArray {
    if (this.isNotEmpty()) {
        return EncryptUtils.encryptMD5(this)
    }
    return ByteArray(0)
}

fun ByteArray.hex(): String {
    return HexUtil.toString(this)
}

fun ByteArray.base64URLEncode(): ByteArray {
    return Base64.encode(this, Base64.URL_SAFE.and(Base64.NO_WRAP))
}

fun ByteArray.base64URLDecode(): ByteArray {
    return Base64.decode(this, Base64.URL_SAFE.and(Base64.NO_WRAP))
}

fun ByteArray.format(): String {
    return String(this)
}