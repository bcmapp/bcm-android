package com.bcm.messenger.common.utils

import android.text.TextUtils
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.HexUtil
import com.bcm.messenger.utility.logger.ALog
import com.orhanobut.logger.Logger
import org.jetbrains.annotations.TestOnly
import org.whispersystems.curve25519.Curve25519
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.libsignal.ecc.ECKeyPair
import org.whispersystems.libsignal.ecc.ECPrivateKey
import org.whispersystems.libsignal.kdf.HKDF
import org.whispersystems.libsignal.util.ByteUtil
import org.whispersystems.signalservice.internal.util.Base58
import org.whispersystems.signalservice.internal.util.Base64
import org.whispersystems.signalservice.internal.util.Util
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom

/**
 * BCM账号生成，公私钥对生成相关帮助类
 */
object BCMPrivateKeyUtils {

    private const val TAG = "BCMPrivateKeyUtils"

    private var hkdfInstance: HKDF? = null
    private const val KDF_ITERATION_COUNT = 10000
    private const val CBC_MODE = "AES/CBC/PKCS7Padding"

    private const val FINGERPRINT_LENGHT = 4
    private const val HEADER_SIZE = 5
    private const val SALT_LENGTH_03 = 4//盐的长度

    private const val HASH_SIZE = 32
    private const val BASE58_VERSION = 0

    //key version
    private const val KEY_V3: Byte = 3
    private const val KEY_V2: Byte = 2

    val KDF_INFO = "BCM".toByteArray()

    fun getHkdfInstance(): HKDF {
        if (hkdfInstance == null) {
            synchronized(this) {
                if (hkdfInstance == null) {
                    hkdfInstance = HKDF.createFor(3)
                }
            }
        }
        return hkdfInstance!!
    }

    @TestOnly
    fun test() {
        Curve25519.getInstance(Curve25519.BEST).generateKeyPair()
        val encryptData = encryptPrivateKey("aaa".toByteArray(), "123".toByteArray())
        val decryptData =  decryptPrivateKey(encryptData,"123".toByteArray())
    }

    /**
     * 根据公钥转化成UID
     */
    fun provideUid(publicKey: ByteArray): String {
        val keyBytes = if (publicKey.size > 32) {
            val keyBytes = ByteArray(32)
            System.arraycopy(publicKey, 1, keyBytes, 0, keyBytes.size)
            keyBytes
        } else {
            publicKey
        }
        return Base58.encodeChecked(BASE58_VERSION, EncryptUtils.sha256hash160(keyBytes))
    }


    fun identityKey2PublicKey(identityKey: ByteArray): ByteArray {
        return if (identityKey.size > 32) {
            val keyBytes = ByteArray(32)
            System.arraycopy(identityKey, 1, keyBytes, 0, keyBytes.size)
            keyBytes
        } else {
            identityKey
        }
    }

    /**
     * 根据signal的identityKey转化成UID
     */
    fun provideUid(identityKey: String): String {
        val identityKeyArray = Base64.decode(identityKey)
        return provideUid(identityKeyArray)
    }

    fun getSecret(size: Int): String {
        val secret = getSecretBytes(size)
        return Base64.encodeBytes(secret)
    }

    fun getSecretBytes(size: Int): ByteArray {
        val secret = ByteArray(size)
        getSecureRandom().nextBytes(secret)
        return secret
    }

    private fun getSecureRandom(): SecureRandom {
        try {
            return SecureRandom.getInstance("SHA1PRNG")
        } catch (e: NoSuchAlgorithmException) {
            throw AssertionError(e)
        }

    }

    /**
     * 生成open id key pair
     */
    fun generateKeyPair(): ECKeyPair {
        return Curve.generateKeyPair()
    }

    /**
     * 签名
     */
    fun sign(pk: ECPrivateKey, msg: String): String {
        return Base64.encodeBytes(Curve.calculateSignature(pk, msg.toByteArray()))
    }

    /**
     * 签名
     */
    fun sign(pk: ECPrivateKey, data: ByteArray): ByteArray {
        return Curve.calculateSignature(pk, data)
    }

    private fun addHeaderForEncryptData(encryptData: ByteArray): ByteArray {
        val header = String.format("BCM")
        val destStream = ByteArrayOutputStream()
        destStream.write(header.toByteArray())
        val versionByte = ByteArray(2)
        versionByte[0] = 0
        versionByte[1] = 2
        destStream.write(versionByte)
        destStream.write(encryptData)
        return destStream.toByteArray()
    }

    private fun appendByteArray(prefix: ByteArray, suffix: ByteArray): ByteArray {
        val dataStream = ByteArrayOutputStream()
        dataStream.write(prefix)
        dataStream.write(suffix)
        val data = dataStream.toByteArray()
        return data
    }

    private fun parseAESKey128(pinHash256: ByteArray): ByteArray {
        val AESKey128 = ByteArray(16)
        System.arraycopy(pinHash256, 0, AESKey128, 0, 16)
        return AESKey128
    }

    /**
     * 解密open id private key
     * 加密逆过程
     * 输入：加密数据的 hex 字串，pin 值
     * 对 pin 做 hash 截取前 128位作为解密密钥
     * 对加密数据去头，进行 AES 解密
     * 解密后取出后缀hash ，对比 hash 是否和 pin 计算出的 hash 相同
     * 相同则密钥正确，返回解密后的私钥，不同则解密失败
     *
     */
    @Throws(ErrorPinException::class)
    fun decodePrivateKey(encryptHexString: String, pin: String): ByteArray {
        try {
            val encryptedData = HexUtil.fromString(encryptHexString)
            if (encryptedData.size < HEADER_SIZE + HASH_SIZE) {
                throw ErrorPinException()
            }
            val pinHashData = EncryptUtils.computeSHA256(pin.toByteArray())
            val key = parseAESKey128(pinHashData)


            //提取头部
            val dataStream = ByteArrayInputStream(encryptedData)
            val header = parseHeader(dataStream)

            if (String(header).startsWith("BCM")) {
                //提取加密数据
                val encryptData = ByteArray(encryptedData.size - HEADER_SIZE)
                dataStream.read(encryptData, 0, encryptData.size)

                val decyptedData = EncryptUtils.decryptAES(encryptData, key,
                        "AES/ECB/PKCS7Padding", null)
                if (decyptedData.size < HASH_SIZE) {
                    throw ErrorPinException()
                }
                val decryptedStream = ByteArrayInputStream(decyptedData)
                val privateKey = parseDecryptedPrivateKey(decryptedStream)
                val decryptedHashData = parseDecryptedHash(decryptedStream)

                if (!TextUtils.equals(HexUtil.toString(decryptedHashData), HexUtil.toString(pinHashData))) {
                    //如果两个 hash 值不一致，说明 pin 码错误，解密失败
                    throw ErrorPinException()
                }
                return privateKey
            } else {
                return EncryptUtils.decryptAES(encryptedData,
                        key,
                        "AES/ECB/PKCS7Padding", null)
            }
        } catch (e: Exception) {
            throw ErrorPinException()
        }
    }

    /**
     * 根据seed产生私钥
     */
    fun generatePrivateKey(seed: ByteArray): ByteArray? {
        val curveProvider = getCurve25519Provider()
        if (curveProvider != null) {
            var method: Method? = null
            try {
                method = curveProvider.javaClass.getMethod("generatePrivateKey", seed.javaClass)
            } catch (e: NoSuchMethodException) {
                e.printStackTrace()
                Logger.e(e, "method error")
            }

            if (null != method) {
                try {
                    val ret = method.invoke(curveProvider, seed as Any)
                    return ret as? ByteArray
                } catch (e: IllegalAccessException) {
                    ALog.e(TAG, "generatePrivateKey error", e)
                } catch (e: InvocationTargetException) {
                    ALog.e(TAG, "generatePrivateKey error", e)

                }

            }
        }
        return null
    }

    /**
     * 根据现有私钥产生公钥
     */
    fun generatePublicKey(privateKey: ByteArray): ByteArray? {
        val curveProvider = getCurve25519Provider()
        if (curveProvider != null) {
            var method: Method? = null
            try {
                method = curveProvider.javaClass.getMethod("generatePublicKey", privateKey.javaClass)
            } catch (e: NoSuchMethodException) {
                e.printStackTrace()
                Logger.e(e, "method error")
            }

            if (null != method) {
                try {
                    val ret = method.invoke(curveProvider, privateKey as Any)
                    return ret as? ByteArray
                } catch (e: IllegalAccessException) {
                    ALog.e(TAG, "generatePublicKey error", e)
                } catch (e: InvocationTargetException) {
                    ALog.e(TAG, "generatePublicKey error", e)
                }

            }
        }
        return null
    }

    /**
     * 根据现有私钥产生公钥
     */
    fun generatePublicKeyWithDJB(privateKey: ByteArray): ByteArray? {
        val pubKey = generatePublicKey(privateKey)
        return if (pubKey == null) {
            null
        }else {
            try {
                val type = byteArrayOf(Curve.DJB_TYPE.toByte())
                ByteUtil.combine(type, pubKey)
            }catch (ex: Exception) {
                ALog.e(TAG, "generatePublicKeyWithDJB error", ex)
                null
            }
        }
    }

    /**
     * 输入 pin 对私钥进行加密，返回加密后字串
     *
     * @param privateKey
     * @param pin
     * @return
     * Pair<encryptPrivateKey,plainSalt>
     */
    fun encryptPrivateKey(privateKey: ByteArray, pin: ByteArray): String {
        //获取 pin 的 hash
        val pin_hash = EncryptUtils.computeSHA256(pin)
        //生成安全随机数
        val plainSalt = Util.getSecretBytes(SALT_LENGTH_03)
        //FIXME:方便调试盐先用4 byte 0
//        val plainSalt = ByteArray(SALT_LENGTH_03)
        //KDF 迭代衍生密钥
        val derivativePk = excuteKdfs(pin_hash, plainSalt, KDF_ITERATION_COUNT)
        //在 privateKey 后面添加指纹
        val realPrivateKey = addSuffixForPlainPrivateKey(privateKey)
        //用衍生密钥对 privateKey 进行加密
        val encryptPrivateKey = encryptWithAES(realPrivateKey, derivativePk)
        //为加密后密钥添加头部,添加盐到尾部
        val encapsulatedPrivateKey = encapsulatedForEncryptData(encryptPrivateKey,plainSalt)
//        Log.e(TAG,"encapsulation privateKey=== "+ Base64.encodeBytes(encapsulatedPrivateKey))
        return Base64.encodeBytes(encapsulatedPrivateKey)
    }

    /**
     * 解密数据，从二维码读出数据，需要加密后私钥
     * @param encapsulatedPrivateKey String
     * @param pin ByteArray
     * @return Boolean:isSuccess; ByteArray:解密后私钥; Int: 错误码
     */
    fun decryptPrivateKey(encapsulatedPrivateKey: String, pin: ByteArray): Triple<Boolean, ByteArray?, Int> {
        return decryptPrivateKey(Base64.decode(encapsulatedPrivateKey), pin)
    }

    private fun decryptPrivateKey(encapsulatedPrivateKey: ByteArray, pin: ByteArray): Triple<Boolean, ByteArray?, Int> {
        val pin_hash = EncryptUtils.computeSHA256(pin)
        //对私钥进行解封装处理，返回头部信息和实体信息
        val encrypteprivateKeyTriple:  Triple<ByteArray, ByteArray,ByteArray> = decapsulationPrivateKey(encapsulatedPrivateKey)
        val header = encrypteprivateKeyTriple.first
        val encryptBody = encrypteprivateKeyTriple.second
        val plainSalt = encrypteprivateKeyTriple.third
        //FIXME: 判断加密数据版本号
        if (header.contentEquals(getKeyHeader(KEY_V3))) {
            ALog.i("BCMPrivateKeyUtils", "V3 version")
            //计算衍生密钥
            val derivativePk = excuteKdfs(pin_hash, plainSalt, KDF_ITERATION_COUNT)
            //解密私钥密文
            val decryptedPrivateKey = decryptWithAES(encryptBody, derivativePk)
            //分离私钥明文和指纹，进行验证
            val privateKeyAndFingerprintPair: Pair<ByteArray, ByteArray> = parseSuffixFromPrivateKey(decryptedPrivateKey)
            val privateKey = privateKeyAndFingerprintPair.first
            val suffix = privateKeyAndFingerprintPair.second
            //计算私钥指纹
            val privateKeyFingerprint = getFingerprint(privateKey)
            if (privateKeyFingerprint.contentEquals(suffix)) {
                return Triple(true, privateKey, 200)
            } else {
                return Triple(false, null, 500)
            }
        } else if (header.contentEquals(getKeyHeader(KEY_V2))){
            ALog.i("BCMPrivateKeyUtils", "V2 version")

            try {
                val priv = decodePrivateKey(HexUtil.toString(encapsulatedPrivateKey), String(pin))
                return Triple(true, priv, 200)
            } catch (e:ErrorPinException){
                ALog.e("BCMPrivateKeyUtils", e)
            }
        } else {
            ALog.i("BCMPrivateKeyUtils", "V1 version")
            try {
                val priv = EncryptUtils.decryptAES(encapsulatedPrivateKey,
                    pin,
                    "AES/ECB/PKCS7Padding", null)
                return Triple(true, priv, 200)
            } catch (e:ErrorPinException){
                ALog.e("BCMPrivateKeyUtils", e)
            }
        }
        return Triple(false, null, -1)
    }


    private fun decapsulationPrivateKey(encapsulatedPrivateKey: ByteArray): Triple<ByteArray, ByteArray,ByteArray> {
        val inputStream = ByteArrayInputStream(encapsulatedPrivateKey)
        val header = ByteArray(HEADER_SIZE)
        val body = ByteArray(encapsulatedPrivateKey.size - HEADER_SIZE- SALT_LENGTH_03)
        val salt = ByteArray(SALT_LENGTH_03)
        inputStream.read(header, 0, HEADER_SIZE)
        inputStream.read(body, 0, body.size)
        inputStream.read(salt,0, SALT_LENGTH_03)
        return Triple(header, body,salt)
    }

    /**
     * 用衍生密钥对私钥进行加密
     *
     * @param plainData
     * @param derivativePk
     * @return
     */
    private fun encryptWithAES(plainData: ByteArray, derivativePk: ByteArray?): ByteArray {
        val inputStream = ByteArrayInputStream(derivativePk)
        val aesKey128 = ByteArray(16)
        val iv = ByteArray(16)
        inputStream.read(aesKey128, 0, 16)
        inputStream.read(iv, 0, 16)
        return EncryptUtils.encryptAES(plainData, aesKey128, CBC_MODE, iv)
    }


    /**
     * 用衍生密钥对私钥进行解密
     *
     * @param encryptData
     * @param derivativePk
     * @return
     */
    private fun decryptWithAES(encryptData: ByteArray, derivativePk: ByteArray): ByteArray {
        val inputStream = ByteArrayInputStream(derivativePk)
        val aesKey128 = ByteArray(16)
        val iv = ByteArray(16)
        inputStream.read(aesKey128, 0, 16)
        inputStream.read(iv, 0, 16)
        return EncryptUtils.decryptAES(encryptData, aesKey128, CBC_MODE, iv)
    }

    /**
     * 在私钥后面添加私钥的指纹，用于解密时验证解密正确性
     *
     * @param privateKey
     * @return
     */
    private fun addSuffixForPlainPrivateKey(privateKey: ByteArray): ByteArray {
        val privateKeyFingerprint = getFingerprint(privateKey)
        val dataStream = ByteArrayOutputStream()
        try {
            dataStream.write(privateKey)
            dataStream.write(privateKeyFingerprint)
        } catch (e: IOException) {
            Logger.e("BCMPrivateKeyUtils", "add suffix for plain private key exception")
        }

        return dataStream.toByteArray()
    }

    private fun parseSuffixFromPrivateKey(decryptedPrivateKey: ByteArray): Pair<ByteArray, ByteArray> {
        val dataStream = ByteArrayInputStream(decryptedPrivateKey)
        val privateKey = ByteArray(decryptedPrivateKey.size - FINGERPRINT_LENGHT)
        val suffix = ByteArray(FINGERPRINT_LENGHT)
        try {
            dataStream.read(privateKey, 0, privateKey.size)
            dataStream.read(suffix, 0, suffix.size)
        } catch (e: IOException) {
            Logger.e(TAG, "add suffix for plain private key exception")
        }
        return Pair(privateKey, suffix)
    }

    /**
     * 添加 header 和 salt 到加密后密钥
     * @param encryptData ByteArray
     * @param salt ByteArray
     * @return ByteArray
     */
    private fun encapsulatedForEncryptData(encryptData: ByteArray,salt: ByteArray): ByteArray {
        val header = String.format("BCM")
        val destStream = ByteArrayOutputStream()
        destStream.write(header.toByteArray())
        val versionByte = ByteArray(2)
        versionByte[0] = 3
        versionByte[1] = 0
        destStream.write(versionByte)
        destStream.write(encryptData)
        destStream.write(salt)
        return destStream.toByteArray()
    }

    private fun getKeyHeader(version:Byte): ByteArray {
        val header = String.format("BCM")
        val destStream = ByteArrayOutputStream()
        destStream.write(header.toByteArray())
        val versionByte = ByteArray(2)
        versionByte[0] = version
        versionByte[1] = 0
        destStream.write(versionByte)
        return destStream.toByteArray()
    }

    /**
     * 获取私钥的指纹
     *
     * @param privateKey
     * @return
     */
    private fun getFingerprint(privateKey: ByteArray): ByteArray {
        val fingerprint = ByteArray(FINGERPRINT_LENGHT)
        val hash_data = EncryptUtils.computeSHA256(privateKey)
        val inputStream = ByteArrayInputStream(hash_data)
        inputStream.read(fingerprint, 0, FINGERPRINT_LENGHT)
        return fingerprint
    }


    /**
     * @param password   原密钥
     * @param salt       盐
     * @param iterations 迭代次数
     * @return kdf 衍生密钥
     */
    private fun excuteKdfs(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
        var temp = password
        val startTime = System.currentTimeMillis()
        for (i in 0 until iterations) {
            temp = getHkdfInstance().deriveSecrets(temp, salt, KDF_INFO, 32)
        }
        return temp
    }

    private fun getCurve25519Provider(): Any? {
        val provider = Curve25519.getInstance(Curve25519.BEST)
        for (field in provider.javaClass.getDeclaredFields()) {
            field.setAccessible(true)
            val targetType = field.getType()
            if (targetType.getName().contains("Curve25519Provider")) {
                var curve25519: Any? = null
                try {
                    curve25519 = field.get(provider)
                } catch (e: IllegalAccessException) {
                    ALog.e(TAG, "getCurve25519Provider error", e)
                }

                return curve25519
            }
        }

        return null
    }

    private fun parseDecryptedHash(decryptedStream: ByteArrayInputStream): ByteArray {
        val hashData = ByteArray(HASH_SIZE)
        decryptedStream.read(hashData, 0, hashData.size)
        return hashData
    }

    private fun parseDecryptedPrivateKey(decryptedStream: ByteArrayInputStream): ByteArray {
        val privateKey = ByteArray(decryptedStream.available() - HASH_SIZE)
        decryptedStream.read(privateKey, 0, privateKey.size)
        return privateKey
    }

    private fun parseHeader(encryptedDataInput: ByteArrayInputStream): ByteArray {
        val header = ByteArray(HEADER_SIZE)
        encryptedDataInput.read(header, 0, HEADER_SIZE)
        return header
    }

    class ErrorPinException() : Exception()
}
