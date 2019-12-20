package com.bcm.messenger.common.crypto

import com.bcm.messenger.utility.Conversions
import java.io.*
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CtrStreamUtil {
    @JvmStatic
    @Throws(AssertionError::class)
    fun createForEncryptingOutputStream(masterSecret: MasterSecret, file: File, inline: Boolean): Pair<ByteArray, OutputStream> {
        val random = ByteArray(32)
        SecureRandom().nextBytes(random)

        try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(masterSecret.encryptionKey.encoded, "HmacSHA256"))

            val fileOutputStream = FileOutputStream(file)
            val iv = ByteArray(16)
            val key = mac.doFinal(random)

            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))

            if (inline) {
                fileOutputStream.write(random)
            }

            return Pair(random, CipherOutputStream(fileOutputStream, cipher))
        } catch (tr: Throwable) {
            throw AssertionError(tr)
        }
    }

    @JvmStatic
    @Throws(AssertionError::class)
    fun createForDecryptingInputStream(masterSecret: MasterSecret, random: ByteArray, file: File, offset: Long): InputStream {
        return createForDecryptingInputStream(masterSecret, random, FileInputStream(file), offset)
    }

    @JvmStatic
    @Throws(AssertionError::class)
    fun createForDecryptingInputStream(masterSecret: MasterSecret, random: ByteArray, inputStream: InputStream, offset: Long): InputStream {
        try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(masterSecret.encryptionKey.encoded, "HmacSHA256"))

            val iv = ByteArray(16)
            val remainder = (offset % 16).toInt()
            Conversions.longTo4ByteArray(iv, 12, offset / 16)

            val key = mac.doFinal(random)
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))

            val skipped = inputStream.skip(offset - remainder)

            if (skipped != offset - remainder) {
                throw IOException("Skip failed: $skipped vs ${offset - remainder}")
            }

            val cipherInputStream = CipherInputStream(inputStream, cipher)
            val remainderBuffer = ByteArray(remainder)

            readFully(cipherInputStream, remainderBuffer)

            return cipherInputStream
        } catch (tr: Throwable) {
            throw AssertionError(tr)
        }
    }

    @Throws(IOException::class)
    private fun readFully(inputStream: InputStream, buffer: ByteArray) {
        var offset = 0

        while (true) {
            val read = inputStream.read(buffer, offset, buffer.size - offset)

            when {
                read == -1 -> throw IOException("Prematurely reached end of stream!")
                read + offset < buffer.size -> offset += read
                else -> return
            }
        }
    }
}