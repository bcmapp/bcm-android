package com.bcm.messenger.common.utils

import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.HexUtil
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.libsignal.ecc.ECPublicKey
import java.io.IOException

fun String.base64Decode(): ByteArray {
    return EncryptUtils.base64Decode(toByteArray())
}

fun String.identityKey(): IdentityKey {
    return IdentityKey(base64Decode(), 0)
}

fun String.publicKey(): ECPublicKey {
    return Curve.decodePoint(base64Decode(), 0)
}

fun String.md5(): String {
    if (this.isNotEmpty()) {
        return EncryptUtils.encryptMD5ToString(this)
    }
    return ""
}

@Throws(IOException::class)
fun String.hex(): ByteArray {
    return HexUtil.fromString(this)
}

fun String.aesEncode(key: ByteArray): String {
    return EncryptUtils.aes256Encrypt(toByteArray(), key)?.base64Encode()?.format() ?: ""
}

fun String.aesDecode(key: ByteArray): String {
    return EncryptUtils.aes256Decrypt(toByteArray().base64Decode(), key)?.format() ?: ""
}


fun String.base64URLEncode(): String {
    return toByteArray().base64Encode().format()
}

fun String.base64URLDecode(): String {
    return toByteArray().base64Decode().format()
}

fun String.front(len: Int = 9): String {
    if (this.length > len) {
        return substring(0, len)
    }
    return this
}