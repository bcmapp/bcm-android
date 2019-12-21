package com.bcm.messenger.common.crypto.encrypt

import android.content.Context
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import com.bcm.messenger.common.core.AmeFileUploader
import com.bcm.messenger.common.core.corebean.GroupKeyParam
import com.bcm.messenger.common.crypto.IdentityKeyUtil
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.crypto.MasterSecretUtil
import com.bcm.messenger.common.exception.DecryptSourceException
import com.bcm.messenger.common.grouprepository.room.entity.GroupInfo
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.Base64
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.logger.ALog
import com.google.gson.Gson
import org.json.JSONObject
import org.whispersystems.curve25519.Curve25519
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.libsignal.ecc.DjbECPrivateKey
import org.whispersystems.libsignal.ecc.DjbECPublicKey
import java.io.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.security.*
import java.util.*
import javax.crypto.*
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


object BCMEncryptUtils {

    private const val TAG = "BCMEncryptUtils"

    private const val CBC_MODE = "AES/CBC/PKCS7Padding"

    private var masterSecret: MasterSecret? = null

    @Synchronized
    fun getMasterSecret(context: Context): MasterSecret? {
        if (masterSecret == null && AMESelfData.isLogin) {
            try {
                masterSecret = MasterSecretUtil.getMasterSecret(context, MasterSecretUtil.UNENCRYPTED_PASSPHRASE)
            } catch (e: Exception) {
                ALog.e(TAG, e)
            }

        }
        return masterSecret
    }

    @Synchronized
    fun clearMasterSecret() {
        masterSecret = null
    }

    //
    fun getMyPrivateKey(context: Context): ByteArray {
        val myKeyPair = IdentityKeyUtil.getIdentityKeyPair(context)
        return (myKeyPair.privateKey as DjbECPrivateKey).privateKey
    }

    //
    fun getMyPublicKey(context: Context): ByteArray {
        val myKeyPair = IdentityKeyUtil.getIdentityKeyPair(context)
        return (myKeyPair.publicKey.publicKey as DjbECPublicKey).publicKey
    }

    //
    fun getMyIdentityKey(context: Context): ByteArray {
        return IdentityKeyUtil.getIdentityKeyPair(context).publicKey.serialize()
    }

    /**
     * otherKey
     * @param context
     * @param otherKey
     * @return
     */
    fun signWithMe(context: Context, otherKey: ByteArray): ByteArray? {
        try {
            return Curve.calculateSignature(IdentityKeyUtil.getIdentityKeyPair(context).privateKey, otherKey)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

        return null
    }

    // DH 
    fun calculateAgreementKeyWithMe(context: Context, otherPublicKey: ByteArray): ByteArray? {
        try {
            return Curve25519.getInstance(Curve25519.BEST).calculateAgreement(otherPublicKey, getMyPrivateKey(context))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }


    //
    fun calculateMySelfAgreementKey(context: Context, myTempPrivateKey: ByteArray): ByteArray? {
        try {
            return Curve25519.getInstance(Curve25519.BEST).calculateAgreement(getMyPublicKey(context), myTempPrivateKey)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }


    /**
     * Applies the MurmurHash3 (x86_32) algorithm to the given data.
     * See this [C++ code for the original.](https://github.com/aappleby/smhasher/blob/master/src/MurmurHash3.cpp)
     */
    fun murmurHash3(rootSeed: Long, item: ByteArray): Long {

        fun rotateLeft32(x: Int, r: Int): Int {
            return x shl r or x.ushr(32 - r)
        }

        var h1 = rootSeed.toInt()
        val c1 = 0xcc9e2d51.toInt()
        val c2 = 0x1b873593

        val mask = 0x3

        val numBlocks = item.size and mask.inv()
        //val numBlocks = item.size / 4 * 4
        // body
        var i = 0
        while (i < numBlocks) {
            val itemInt = item[i].toInt()
            var k1 = item[i].toInt() and 0xFF or
                    (item[i + 1].toInt() and 0xFF shl 8) or
                    (item[i + 2].toInt() and 0xFF shl 16) or
                    (item[i + 3].toInt() and 0xFF shl 24)

            k1 *= c1
            k1 = rotateLeft32(k1, 15)
            k1 *= c2

            h1 = h1 xor k1
            h1 = rotateLeft32(h1, 13)
            h1 = (h1 * 5 + 0xe6546b64L).toInt()
            i += 4
        }

        var k1 = 0
        when (item.size and 3) {
            3 -> {
                k1 = k1 xor (item[numBlocks + 2].toInt() and 0xff shl 16)
                k1 = k1 xor (item[numBlocks + 1].toInt() and 0xff shl 8)
                k1 = k1 xor (item[numBlocks].toInt() and 0xff)
                k1 *= c1
                k1 = rotateLeft32(k1, 15)
                k1 *= c2
                h1 = h1 xor k1
            }
            // Fall through.
            2 -> {
                k1 = k1 xor (item[numBlocks + 1].toInt() and 0xff shl 8)
                k1 = k1 xor (item[numBlocks].toInt() and 0xff)
                k1 *= c1
                k1 = rotateLeft32(k1, 15)
                k1 *= c2
                h1 = h1 xor k1
            }
            // Fall through.
            1 -> {
                k1 = k1 xor (item[numBlocks].toInt() and 0xff)
                k1 *= c1
                k1 = rotateLeft32(k1, 15)
                k1 *= c2
                h1 = h1 xor k1
            }
            // Fall through.
            else -> {
            }
        }// Do nothing.

        // finalization
        h1 = h1 xor item.size
        h1 = h1 xor h1.ushr(16)
        h1 = (h1 * 0x85ebca6bL).toInt()
        h1 = h1 xor h1.ushr(13)
        h1 = (h1 * 0xc2b2ae35L).toInt()
        h1 = h1 xor h1.ushr(16)

        return (h1.toLong() and 0xFFFFFFFFL)
    }


    //
    fun encryptGroupPk(groupPk: ByteArray, ecdhPassword: ByteArray?): String {
        return encodeByteArray(encryptByAES256(groupPk, ecdhPassword))
    }


    /**
     * AES
     * @param content
     * @param aesKey
     * @return Base64
     */
    fun encryptByAES256(content: ByteArray, aesKey: ByteArray?): ByteArray {
        val sha512Data = EncryptUtils.encryptSHA512(aesKey)
        val aesKey256 = ByteArray(32)
        val iv = ByteArray(16)
        //ecdhPassword 16 aesKey128，16 iv
        System.arraycopy(sha512Data, 0, aesKey256, 0, 32)
        System.arraycopy(sha512Data, 48, iv, 0, 16)
        return EncryptUtils.encryptAES(content, aesKey256, CBC_MODE, iv)
    }

    fun decryptByAES256(content: ByteArray, aesKey: ByteArray): ByteArray {
        val sha512Data = EncryptUtils.encryptSHA512(aesKey)
        val aesKey256 = ByteArray(32)
        val iv = ByteArray(16)
        //ecdhPassword 16 aesKey128，16 iv
        System.arraycopy(sha512Data, 0, aesKey256, 0, 32)
        System.arraycopy(sha512Data, 48, iv, 0, 16)
        return EncryptUtils.decryptAES(content, aesKey256, CBC_MODE, iv)
    }

    //，
    fun generate64BitKey(): ByteArray {
        return EncryptUtils.getSecretBytes(64)
    }

    //base64 encode 
    fun encodeByteArray(input: ByteArray): String {
        return Base64.encodeBytes(input)
    }

    /**
     * 
     *
     * @param encryptedKey
     * @return
     */
    fun decryptGroupPassword(encryptedKey: String?): Pair<String, Int> {
        if (encryptedKey.isNullOrEmpty()) {
            return Pair("", -1)
        }
        try {
            val decodeEncryptkey = String(Base64.decode(encryptedKey))
            val gson = Gson()

            val encryptKeySpec = gson.fromJson(decodeEncryptkey, EncryptKeySpec::class.java)
            return if (TextUtils.equals(encryptKeySpec.invitee_public_key, Base64.encodeBytes(getMyPublicKey(AppContextHolder.APP_CONTEXT)))) {
                val ecdhPassword = calculateAgreementKeyWithMe(AppContextHolder.APP_CONTEXT, Base64.decode(encryptKeySpec.inviter_public_key))
                val sha512Data = EncryptUtils.encryptSHA512(ecdhPassword)
                val aesKey256 = ByteArray(32)
                val iv = ByteArray(16)
                //ecdhPassword 16 aesKey128，16 iv
                System.arraycopy(sha512Data, 0, aesKey256, 0, 32)
                System.arraycopy(sha512Data, 48, iv, 0, 16)
                Pair(encodeByteArray(EncryptUtils.decryptAES(Base64.decode(encryptKeySpec.encrypt_key), aesKey256, CBC_MODE, iv)), GroupInfo.LEGITIMATE_GROUP)
            } else {
                Pair("", GroupInfo.ILLEGAL_GROUP)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "decrypt group key error")
        }

        return Pair("", -1)
    }


    /**
     * 
     *
     * @param gid  id
     * @param path 
     * @param path 
     */
    fun decodeFile(gid: Long?, path: String, sign: String, keyParam: GroupKeyParam): String {
        try {
            val extension = path.substring(path.lastIndexOf("/"))
            val destPath = AmeFileUploader.DECRYPT_DIRECTORY + extension
            decodeFile(gid, path, destPath, sign, keyParam)

        } catch (ex: Exception) {
            ALog.e(TAG, "decodeFile error", ex)
        }

        return ""
    }


    /**
     * 
     *
     * @param gid  id
     * @param path 
     * @param path 
     */
    fun decodeFile(gid: Long?, path: String, destPath: String, sign: String, keyParam: GroupKeyParam?): String {
        if (keyParam != null) {
            try {
                val dir = File(destPath).parentFile
                if (!dir.exists()) {
                    dir.mkdirs()
                }

                val signBytes = Base64.decode(sign)

                val oneTimePassword = EncryptUtils.encryptSHA512(GroupMessageEncryptUtils.byteMerger(keyParam.key, signBytes))
                val aesKey256 = ByteArray(32)
                val iv = ByteArray(16)
                System.arraycopy(oneTimePassword, 0, aesKey256, 0, 32)
                System.arraycopy(oneTimePassword, 48, iv, 0, 16)

                BcmFileUtils.createFile(AmeFileUploader.DECRYPT_DIRECTORY, destPath)  //
                //Cipher.ENCRYPT_MODE，Cipher.DECRYPT_MODE
                val isSuccess = decryptFile(path, destPath, aesKey256, iv)
                if (isSuccess) {
                    return destPath
                } else {
                    ALog.e(TAG, "decrypt file failed")
                }
                return ""
            } catch (e: IOException) {
                ALog.e(TAG, e)
            }

        } else {
            ALog.e(TAG, "decrypt file failed encryptSpec is null")
        }
        return ""
    }

    /**
     * 
     *
     * @param gid  id
     * @param path 
     * @return 
     */
    fun encodeFile(gid: Long?, path: String, keyParam: GroupKeyParam?, callback: Function2<String, String, Unit>) {
        if (keyParam != null) {
            try {
                val dir = File(AmeFileUploader.ENCRYPT_DIRECTORY)
                if (!dir.exists()) {
                    dir.mkdirs()
                }

                val extension = path.substring(path.lastIndexOf("/"))
                val destPath = AmeFileUploader.ENCRYPT_DIRECTORY + extension
                val messageDigest = EncryptUtils.encryptSHA512(FileInputStream(path))


                val oneTimePassword = EncryptUtils.encryptSHA512(GroupMessageEncryptUtils.byteMerger(keyParam.key, messageDigest))
                val aesKey256 = ByteArray(32)
                val iv = ByteArray(16)
                System.arraycopy(oneTimePassword, 0, aesKey256, 0, 32)
                System.arraycopy(oneTimePassword, 48, iv, 0, 16)

                BcmFileUtils.createFile(AmeFileUploader.ENCRYPT_DIRECTORY, extension)  //
                //Cipher.ENCRYPT_MODE，Cipher.DECRYPT_MODE
                //                    boolean isSuccess = AESCipher(Cipher.ENCRYPT_MODE, path, destPath, aesKey256, iv);
                val isSuccess = encryptFile(path, destPath, aesKey256, iv)
                if (isSuccess)
                    callback.invoke(Base64.encodeBytes(messageDigest), destPath)
                else {
                    ALog.e(TAG, "encrypt file failed" + gid!!)
                }

            } catch (e: IOException) {
                ALog.e(TAG, e)
            }

        } else {
            ALog.e(TAG, "encrypt file failed encryptSpec is null")
            callback.invoke("", "")
        }
    }

    /**
     * ECDHAES256
     * @param fromFilePath
     * @param toFilePath
     * @param keyBytes
     * @return
     */
    fun encryptFileByAES256(fromFilePath: String, toFilePath: String, keyBytes: ByteArray): Boolean {
        val buffer = EncryptUtils.encryptSHA512(keyBytes)
        val aesKey256 = ByteArray(32)
        val iv = ByteArray(16)
        System.arraycopy(buffer, 0, aesKey256, 0, 32)
        System.arraycopy(buffer, 48, iv, 0, 16)
        return encryptFile(fromFilePath, toFilePath, aesKey256, iv)
    }

    /**
     * ECDHAES256
     * @param fromFilePath
     * @param toFilePath
     * @param keyBytes
     * @return
     */
    fun decryptFileByAES256(fromFilePath: String, toFilePath: String, keyBytes: ByteArray): Boolean {
        val buffer = EncryptUtils.encryptSHA512(keyBytes)
        val aesKey256 = ByteArray(32)
        val iv = ByteArray(16)
        System.arraycopy(buffer, 0, aesKey256, 0, 32)
        System.arraycopy(buffer, 48, iv, 0, 16)

        return decryptFile(fromFilePath, toFilePath, aesKey256, iv)
    }


    /**
     * 
     *
     * @return 
     */
    fun encryptFile(originFile: File, oneTimePassword: ByteArray, destFile: File): Boolean {
        val aesKey256 = ByteArray(32)
        val iv = ByteArray(16)
        System.arraycopy(oneTimePassword, 0, aesKey256, 0, 32)
        System.arraycopy(oneTimePassword, 48, iv, 0, 16)
        return encryptFile(originFile.absolutePath, destFile.absolutePath, aesKey256, iv)
    }

    /**
     * 
     * @param path
     * @param psw
     * @return
     */
    fun decodeFile(path: String, destPath: String, psw: String): String {
        try {
            val dir = File(destPath).parentFile
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val oneTimePassword = Base64.decode(psw)
            val aesKey256 = ByteArray(32)
            val iv = ByteArray(16)
            System.arraycopy(oneTimePassword, 0, aesKey256, 0, 32)
            System.arraycopy(oneTimePassword, 48, iv, 0, 16)

            BcmFileUtils.createFile(AmeFileUploader.DECRYPT_DIRECTORY, destPath)  //
            //Cipher.ENCRYPT_MODE，Cipher.DECRYPT_MODE
            val isSuccess = decryptFile(path, destPath, aesKey256, iv)
            if (isSuccess) {
                return destPath
            } else {
                ALog.e(TAG, "decrypt file failed")
            }
            return ""
        } catch (e: Exception) {
            ALog.e(TAG, "decodeFile error", e)
            return ""
        }

    }

    fun getMessageDigest(context: Context, uri: Uri): ByteArray? {
        return getMessageDigest(BcmFileUtils.getFileAbsolutePath(context, uri))
    }

    fun getMessageDigest(path: String?): ByteArray? {
        try {
            return EncryptUtils.encryptSHA512(FileInputStream(path))
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            return null
        }

    }


    /**
     * 
     *
     * @param cipherMode     Cipher.ENCRYPT_MODE，Cipher.DECRYPT_MODE
     * @param sourceFilePath 
     * @param targetFilePath 
     * @param seed           
     * @param iv             
     * @return
     */
    fun AESCipher(cipherMode: Int, sourceFilePath: String,
                  targetFilePath: String, seed: ByteArray, iv: ByteArray): Boolean {
        var result = false
        var sourceFC: FileChannel? = null
        var targetFC: FileChannel? = null

        try {

            if (cipherMode != Cipher.ENCRYPT_MODE && cipherMode != Cipher.DECRYPT_MODE) {
                return false
            }

            val mCipher = Cipher.getInstance("AES/CBC/PKCS7Padding")

            val sourceFile = File(sourceFilePath)
            val targetFile = File(targetFilePath)


            sourceFC = RandomAccessFile(sourceFile, "r").channel
            targetFC = RandomAccessFile(targetFile, "rw").channel

            val secretKey = SecretKeySpec(seed, "AES")

            mCipher.init(cipherMode, secretKey, IvParameterSpec(iv))

            val byteData = ByteBuffer.allocate(1024)
            while (sourceFC!!.read(byteData) != -1) {
                // 。
                // 
                byteData.flip()

                val byteList = ByteArray(byteData.remaining())
                byteData.get(byteList, 0, byteList.size)
                //，，byteData1024，，。
                val bytes = mCipher.doFinal(byteList)
                targetFC!!.write(ByteBuffer.wrap(bytes))
                byteData.clear()
            }

            result = true
        } catch (e: IOException) {
            Log.d(TAG, e.message)

        } catch (e: NoSuchAlgorithmException) {
            Log.d(TAG, e.message)
        } catch (e: InvalidKeyException) {
            Log.d(TAG, e.message)
        } catch (e: InvalidAlgorithmParameterException) {
            Log.d(TAG, e.message)
        } catch (e: IllegalBlockSizeException) {
            Log.d(TAG, e.message)
        } catch (e: BadPaddingException) {
            Log.d(TAG, e.message)
        } catch (e: NoSuchPaddingException) {
            Log.d(TAG, e.message)
        } finally {
            try {
                sourceFC?.close()
                targetFC?.close()
            } catch (e: IOException) {
                Log.d(TAG, e.message)
            }

        }

        return result
    }

    /**
     *  AES Cipher
     *
     * @param
     * @param cipherMode
     * @return
     */
    fun initAESCipher(cipherMode: Int, seed: ByteArray, iv: ByteArray): Cipher? {

        var cipher: Cipher? = null
        try {
            val key = SecretKeySpec(seed, "AES")
            cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            //
            cipher!!.init(cipherMode, key, IvParameterSpec(iv))
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()  //To change body of catch statement use File | Settings | File Templates.
        } catch (e: InvalidAlgorithmParameterException) {
            e.printStackTrace()
        } catch (e: NoSuchPaddingException) {
            e.printStackTrace()
        } catch (e: InvalidKeyException) {
            e.printStackTrace()
        }

        return cipher
    }


    private fun encryptFile(sourceFilePath: String,
                            targetFilePath: String, seed: ByteArray, iv: ByteArray): Boolean {
        //
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {
            inputStream = FileInputStream(sourceFilePath)
            outputStream = FileOutputStream(targetFilePath)
            return encryptStream(inputStream, outputStream, seed, iv)
        } catch (e: IOException) {
            e.printStackTrace()  //To change body of catch statement use File | Settings | File Templates.
            return false
        } finally {
            try {
                inputStream?.close()
                outputStream?.close()
            } catch (e: IOException) {
                e.printStackTrace()  //To change body of catch statement use File | Settings | File Templates.
            }

        }
    }

    @Throws(IOException::class)
    fun encryptStream(inputStream: InputStream,
                      outputStream: OutputStream, seed: ByteArray, iv: ByteArray): Boolean {
        val cipher = initAESCipher(Cipher.ENCRYPT_MODE, seed, iv)
        //
        val cipherInputStream = CipherInputStream(inputStream, cipher)
        val cache = ByteArray(1024)
        var nRead = 0
        do {
            nRead = cipherInputStream.read(cache)
            if (nRead != -1) {
                outputStream.write(cache, 0, nRead)
                outputStream.flush()
            }
        }while (nRead != -1)
        cipherInputStream.close()
        return true
    }


    fun decryptFile(sourceFilePath: String,
                    targetFilePath: String, seed: ByteArray, iv: ByteArray): Boolean {
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {
            inputStream = FileInputStream(sourceFilePath)
            outputStream = FileOutputStream(targetFilePath)
            return decodeStreamAES256(inputStream, outputStream, seed, iv)
        } catch (e: IOException) {
            e.printStackTrace()  //To change body of catch statement use File | Settings | File Templates.
            return false
        } finally {
            try {
                inputStream?.close()

                outputStream?.close()
            } catch (e: IOException) {
                e.printStackTrace()  //To change body of catch statement use File | Settings | File Templates.
            }

        }
    }

    @Throws(IOException::class)
    fun decodeStreamAES256(inputStream: InputStream,
                           outputStream: OutputStream, seed: ByteArray, iv: ByteArray): Boolean {
        val cipher = initAESCipher(Cipher.DECRYPT_MODE, seed, iv)

        val cipherOutputStream = CipherOutputStream(outputStream, cipher)
        val buffer = ByteArray(1024)
        var r: Int = 0
        do {
            r = inputStream.read(buffer)
            if (r >= 0) {
                cipherOutputStream.write(buffer, 0, r)
            }
        }while (r >= 0)
        cipherOutputStream.close()
        return true
    }

    /**
     * ,
     *
     * @param seed （）
     * @return 
     * @throws NoSuchAlgorithmException
     */
    @Deprecated("Call must throw NoSuchAlgorithmException, Android 9 removed SHA1PRNG Crypto.")
    @Throws(NoSuchAlgorithmException::class, NoSuchProviderException::class)
    private fun getRawKey(seed: ByteArray): ByteArray {
        // ，。
        val sr = SecureRandom.getInstance("SHA1PRNG", "Crypto")
        //"Crypto" 
        // ,
        sr.setSeed(seed)
        // key（AES）
        val keyGen = KeyGenerator.getInstance("AES")
        // 256
        keyGen.init(256, sr)
        // 
        val key = keyGen.generateKey()
        // byte
        return key.encoded
    }


    /**
     * ， DH  plainPassword
     * ，， key  json
     * base64 
     *
     * @param plainPassword
     * @return
     */
    fun generateMyEncryptKeyString(plainPassword: ByteArray): String {
        val myPublicKeyString = encodeByteArray(getMyPublicKey(AppContextHolder.APP_CONTEXT))
        //
        val tempKeyPair = Curve25519.getInstance(Curve25519.BEST).generateKeyPair()
        val dhKey = calculateMySelfAgreementKey(AppContextHolder.APP_CONTEXT, tempKeyPair.privateKey)
        val encryptKey = encryptGroupPk(plainPassword, dhKey)
        val ownerKeySpec = EncryptKeySpec()
        ownerKeySpec.version = EncryptKeySpec.ENCRYPT_VERSION
        ownerKeySpec.inviter_public_key = encodeByteArray(tempKeyPair.publicKey)
        ownerKeySpec.invitee_public_key = myPublicKeyString
        ownerKeySpec.encrypt_key = encryptKey
        return encodeByteArray(Gson().toJson(ownerKeySpec).toByteArray())
    }


    fun generateMembersEncryptKeys(memberIdentityList: List<String>, plainPassword: ByteArray): LinkedList<String> {
        val memberKeySpecStrings = LinkedList<String>()

        val gson = Gson()
        for (memberIdentity in memberIdentityList) {
            try {
                val otherPublicKey = (IdentityKey(Base64.decode(memberIdentity), 0).publicKey as DjbECPublicKey).publicKey

                val sharePasswordKeyPair = Curve25519.getInstance(Curve25519.BEST).generateKeyPair()
                val dhPassword = Curve25519.getInstance(Curve25519.BEST).calculateAgreement(otherPublicKey, sharePasswordKeyPair.privateKey)

                val memberEncryptedKey = encryptGroupPk(plainPassword, dhPassword)
                val memberKeySpec = EncryptKeySpec()
                memberKeySpec.version = EncryptKeySpec.ENCRYPT_VERSION
                memberKeySpec.encrypt_key = memberEncryptedKey

                val sharePublicKey = encodeByteArray(sharePasswordKeyPair.publicKey)
                memberKeySpec.inviter_public_key = sharePublicKey
                memberKeySpec.invitee_public_key = Base64.encodeBytes(otherPublicKey)
                memberKeySpecStrings.add(encodeByteArray(gson.toJson(memberKeySpec).toByteArray()))
            } catch (e: org.whispersystems.libsignal.InvalidKeyException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }
        return memberKeySpecStrings

    }

    fun generateMemberEncryptKey(memberIdentity: String, plainPassword: ByteArray): String {
        try {
            val otherPublicKey = (IdentityKey(Base64.decode(memberIdentity), 0).publicKey as DjbECPublicKey).publicKey

            val sharePasswordKeyPair = Curve25519.getInstance(Curve25519.BEST).generateKeyPair()
            val dhPassword = Curve25519.getInstance(Curve25519.BEST).calculateAgreement(otherPublicKey, sharePasswordKeyPair.privateKey)

            val memberEncryptedKey = encryptGroupPk(plainPassword, dhPassword)
            val memberKeySpec = EncryptKeySpec()
            memberKeySpec.version = EncryptKeySpec.ENCRYPT_VERSION
            memberKeySpec.encrypt_key = memberEncryptedKey

            val sharePublicKey = encodeByteArray(sharePasswordKeyPair.publicKey)
            memberKeySpec.inviter_public_key = sharePublicKey
            memberKeySpec.invitee_public_key = Base64.encodeBytes(otherPublicKey)
            return encodeByteArray(GsonUtils.toJson(memberKeySpec).toByteArray())
        } catch (e: org.whispersystems.libsignal.InvalidKeyException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return ""
    }

    /**
     * 
     * @param encryptSource 
     * @return 
     * @throws DecryptSourceException 
     */
    @Throws(DecryptSourceException::class)
    fun decryptSource(encryptSource: ByteArray): String {
        // Version
        try {
            val decodeString = String(Base64.decode(encryptSource), StandardCharsets.UTF_8)
            val `object` = JSONObject(decodeString)
            val iv = Base64.decode(`object`.getString("iv"))
            val ephemeralPubKey = ByteArray(32)
            System.arraycopy(Base64.decode(`object`.getString("ephemeralPubkey")), 1, ephemeralPubKey, 0, 32)
            val source = Base64.decode(`object`.getString("source"))
            val psw = calculateAgreementKeyWithMe(AppContextHolder.APP_CONTEXT, ephemeralPubKey)
            val shaPsw = EncryptUtils.computeSHA256(psw)
            //            int version = object.optInt("version");
            return String(EncryptUtils.decryptAES(source, shaPsw, CBC_MODE, iv))
        } catch (e: Throwable) {
            throw DecryptSourceException("Decrypt source failed", e)
        }

    }

    fun verifySignature(publicKey: ByteArray?, source: ByteArray?, signature: ByteArray?): Boolean {
        if (publicKey == null || source == null || signature == null) {
            ALog.e(TAG, "Has empty data")
            return false
        }
        try {
            return Curve25519.getInstance(Curve25519.BEST).verifySignature(publicKey, source, signature)
        } catch (e: Throwable) {
            ALog.e(TAG, "Verify sign error", e)
            return false
        }

    }
}
