package com.bcm.messenger.common.crypto.encrypt

import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.annotation.WorkerThread
import com.bcm.messenger.common.core.AmeFileUploader
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.corebean.GroupKeyParam
import com.bcm.messenger.common.crypto.CtrStreamUtil
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.manager.GroupInfoDataManager
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.grouprepository.model.AmeHistoryMessageDetail
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.MediaUtil
import com.bcm.messenger.utility.Base64
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.logger.ALog
import org.whispersystems.signalservice.api.crypto.AttachmentCipherInputStream
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer
import java.io.*
import java.security.DigestInputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.CipherInputStream

data class FileInfo(
        val file: File,
        val size: Long,
        val random: ByteArray?,
        val hash: String?
)

data class GroupFileInfo(
        val file: File,
        val size: Long,
        val name: String,
        val mimeType: String,
        val sign: String)

object ChatFileEncryptDecryptUtil {
    private const val TAG = "ChatFileEncryptUtil"

    enum class FileType(val type: Int) {
        PRIVATE(1),
        GROUP(2),
        GROUP_THUMB(3)
    }

    @Throws(InvalidObjectException::class, RuntimeException::class)
    @WorkerThread
    @JvmStatic
    fun decryptAndSaveFile(masterSecret: MasterSecret?, sourceFile: File, data: Any, fileType: FileType): FileInfo {
        if (masterSecret == null) throw InvalidObjectException("MasterSecret is null!")

        return when (fileType) {
            FileType.PRIVATE -> {
                if (data !is SignalServiceAttachmentPointer) {
                    throw InvalidObjectException("Data is not a SignalServiceAttachmentPointer.")
                }
                decryptAndSavePrivateFile(masterSecret, sourceFile, data)
            }
            FileType.GROUP, FileType.GROUP_THUMB -> {
                if (data !is AmeGroupMessageDetail) {
                    throw InvalidObjectException("Data is not an AmeGroupMessageDetail.")
                }
                if (data is AmeHistoryMessageDetail) {
                    decryptHistoryFile(masterSecret, sourceFile, data, fileType == FileType.GROUP_THUMB)
                } else {
                    decryptAndSaveGroupFile(masterSecret, sourceFile, data, fileType == FileType.GROUP_THUMB)
                }
            }
        }
    }

    @Throws(AssertionError::class, RuntimeException::class)
    @WorkerThread
    @JvmStatic
    fun encryptGroupFile(path: String, keyParam: GroupKeyParam?): GroupFileInfo {
        keyParam ?: throw AssertionError("Key param is null")
        try {
            val originFile = File(path)
            val digest = EncryptUtils.encryptSHA512(FileInputStream(originFile))

            val oneTimePsw = EncryptUtils.encryptSHA512(GroupMessageEncryptUtils.byteMerger(keyParam.key, digest))

            val uri = BcmFileUtils.createFile(AmeFileUploader.ENCRYPT_DIRECTORY, "${AmeFileUploader.ENCRYPT_DIRECTORY}/${originFile.name}")
            val destFile = File(uri.path)
            realEncryptGroupFile(originFile, destFile, oneTimePsw)
            return GroupFileInfo(destFile, destFile.length(), originFile.name,
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(originFile.extension)
                            ?: "application/octet-stream", Base64.encodeBytes(digest))
        } catch (tr: Throwable) {
            throw RuntimeException("Encrypt file failed", tr)
        }
    }

    private fun decryptAndSavePrivateFile(masterSecret: MasterSecret, sourceFile: File, pointer: SignalServiceAttachmentPointer): FileInfo {
        try {
            val plainTextSize = pointer.size.or(0).toLong()
            val key = pointer.key
            val digest = pointer.digest.get()

            val decryptedInputStream = AttachmentCipherInputStream.createFor(sourceFile, plainTextSize, key, digest)
            val fileInfo = encryptLocalFile(masterSecret, decryptedInputStream)

//            val existFileInfo = checkFileExist(fileInfo.hash!!, false)
//            if (existFileInfo != null) {
//                return existFileInfo
//            }

            return fileInfo
        } catch (tr: Throwable) {
            throw RuntimeException("Decrypt private file failed", tr)
        }
    }

    private fun decryptAndSaveGroupFile(masterSecret: MasterSecret, sourceFile: File, detail: AmeGroupMessageDetail, isThumbnail: Boolean): FileInfo {
        try {
            val sign = when {
                detail.message.isWithThumbnail() -> {
                    val content = detail.message.content as AmeGroupMessage.ThumbnailContent
                    if (isThumbnail) content.sign_thumbnail else content.sign
                }
                detail.message.isAudio() -> (detail.message.content as AmeGroupMessage.AudioContent).sign
                detail.message.isFile() -> (detail.message.content as AmeGroupMessage.FileContent).sign
                else -> throw AssertionError("MessageDetail is not a media message.")
            }
            val decryptedInputStream = decryptGroupFile(detail.gid, detail.keyVersion, sourceFile, sign)
            val fileInfo = encryptLocalFile(masterSecret, decryptedInputStream)
//            val existFileInfo = checkFileExist(fileInfo.hash!!, isThumbnail)
//            if (existFileInfo != null) {
//                return existFileInfo
//            }

            return fileInfo
        } catch (tr: Throwable) {
            throw RuntimeException("Decrypt group file failed.", tr)
        }
    }

    private fun decryptHistoryFile(masterSecret: MasterSecret, sourceFile: File, detail: AmeHistoryMessageDetail, isThumbnail: Boolean): FileInfo {
        val pswType: Int
        val psw: String
        val sign: String
        val size: Long
        val destPath: String
        when {
            detail.message.isWithThumbnail() -> {
                val content = detail.message.content as AmeGroupMessage.ThumbnailContent
                if (!isThumbnail || MediaUtil.isGif((detail.message.content as AmeGroupMessage.ThumbnailContent).mimeType)) {
                    pswType = detail.attachmentPsw?.type ?: -1
                    psw = detail.attachmentPsw?.psw ?: ""
                    sign = content.sign
                    size = content.size
                    destPath = "${content.getPath().second}${File.separator}${content.getExtension()}"
                } else {
                    pswType = detail.thumbPsw?.type ?: -1
                    psw = detail.thumbPsw?.psw ?: ""
                    sign = content.sign_thumbnail
                    size = content.size
                    destPath = "${content.getThumbnailPath().second}${File.separator}${content.getThumbnailExtension()}"
                }
            }
            detail.message.isAudio() -> {
                val content = detail.message.content as AmeGroupMessage.AudioContent
                pswType = detail.attachmentPsw?.type ?: -1
                psw = detail.attachmentPsw?.psw ?: ""
                sign = (detail.message.content as AmeGroupMessage.AudioContent).sign
                size = (detail.message.content as AmeGroupMessage.AudioContent).size
                destPath = "${content.getPath().second}${File.separator}${content.getExtension()}"
            }
            detail.message.isFile() -> {
                val content = detail.message.content as AmeGroupMessage.FileContent
                pswType = detail.attachmentPsw?.type ?: -1
                psw = detail.attachmentPsw?.psw ?: ""
                sign = (detail.message.content as AmeGroupMessage.FileContent).sign
                size = (detail.message.content as AmeGroupMessage.FileContent).size
                destPath = "${content.getPath().second}${File.separator}${content.getExtension()}"
            }
            else -> throw RuntimeException("MessageDetail is not a media message.")
        }

        val oneTimePassword = Base64.decode(psw)
        val inputStream = when (pswType) {
            0 -> realDecryptGroupFile(sourceFile, oneTimePassword)
            1 -> AttachmentCipherInputStream.createFor(sourceFile, size, Base64.decode(psw), Base64.decode(sign))
            else -> FileInputStream(sourceFile)
        }

        // Notice: Encrypt history file after GroupMessage entity update!!
        // Author: Kin
        val destFile = File(destPath)
        BcmFileUtils.copy(inputStream, FileOutputStream(destFile))

        return FileInfo(destFile, destFile.length(), null, null)

//        return encryptLocalFile(masterSecret, inputStream)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun encryptLocalFile(masterSecret: MasterSecret, toEncryptFile: File): FileInfo {
        return encryptLocalFile(masterSecret, FileInputStream(toEncryptFile))
    }

    @JvmStatic
    @Throws(IOException::class)
    fun encryptLocalFile(masterSecret: MasterSecret, toEncryptFileStream: InputStream): FileInfo {
        val time = System.currentTimeMillis()
        val partDirectory = File(AmeFileUploader.CHAT_FILE_DIRECTORY)
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val digestInputStream = DigestInputStream(toEncryptFileStream, messageDigest)

        if (!partDirectory.exists()) {
            partDirectory.mkdirs()
        }
        val dataTempFile = File.createTempFile("bcm_", ".mms", partDirectory)
        val out = CtrStreamUtil.createForEncryptingOutputStream(masterSecret, dataTempFile, false)
        val length = BcmFileUtils.copy(digestInputStream, out.second)
        val hash = Base64.encodeBytes(digestInputStream.messageDigest.digest())

        ALog.i(TAG, "Total time = ${System.currentTimeMillis() - time}")
        return FileInfo(dataTempFile, length, out.first, hash)
    }

    private fun decryptGroupFile(gid: Long, keyVersion: Long, sourceFile: File, sign: String): InputStream {
        val encryptSpec = GroupInfoDataManager.queryGroupKeyParam(gid, keyVersion)
                ?: throw AssertionError("EncryptSpec is null.")

        if (sign.isEmpty()) {
            throw AssertionError("Signature is empty.")
        }
        val signBytes = Base64.decode(sign)
        val key = encryptSpec.key

        val oneTimePassword = EncryptUtils.encryptSHA512(GroupMessageEncryptUtils.byteMerger(key, signBytes))
        return realDecryptGroupFile(sourceFile, oneTimePassword)
    }

    private fun realDecryptGroupFile(sourceFile: File, oneTimePassword: ByteArray): InputStream {
        val aesKey256 = ByteArray(32)
        val iv = ByteArray(16)
        System.arraycopy(oneTimePassword, 0, aesKey256, 0, 32)
        System.arraycopy(oneTimePassword, 48, iv, 0, 16)

        val cipher = BCMEncryptUtils.initAESCipher(Cipher.DECRYPT_MODE, aesKey256, iv)
        val fileInputStream = FileInputStream(sourceFile)
        return CipherInputStream(fileInputStream, cipher)
    }

    private fun getEncryptGroupFileStream(sourceStream: InputStream, oneTimePassword: ByteArray): InputStream {
        val aesKey256 = ByteArray(32)
        val iv = ByteArray(16)
        System.arraycopy(oneTimePassword, 0, aesKey256, 0, 32)
        System.arraycopy(oneTimePassword, 48, iv, 0, 16)

        val cipher = BCMEncryptUtils.initAESCipher(Cipher.ENCRYPT_MODE, aesKey256, iv)
        return CipherInputStream(sourceStream, cipher)
    }

    private fun realEncryptGroupFile(sourceFile: File, encryptedFile: File, oneTimePassword: ByteArray) {
        val aesKey256 = ByteArray(32)
        val iv = ByteArray(16)
        System.arraycopy(oneTimePassword, 0, aesKey256, 0, 32)
        System.arraycopy(oneTimePassword, 48, iv, 0, 16)

        val cipher = BCMEncryptUtils.initAESCipher(Cipher.ENCRYPT_MODE, aesKey256, iv)
        val cipherInputStream = CipherInputStream(FileInputStream(sourceFile), cipher)
        val outputStream = FileOutputStream(encryptedFile)

        val cache = ByteArray(1024)
        var readSize: Int
        try {
            do {
                readSize = cipherInputStream.read(cache)
                if (readSize != -1) {
                    outputStream.write(cache, 0, readSize)
                    outputStream.flush()
                }
            } while (readSize != -1)
        } catch (tr: Throwable) {
            throw RuntimeException("Encrypt file failed", tr)
        } finally {
            cipherInputStream.close()
            outputStream.close()
        }
    }

    private fun checkFileExist(hash: String, isThumbnail: Boolean): FileInfo? {
        if (isThumbnail) {
            val privateAttachment = Repository.getAttachmentRepo().getExistThumbnailData(hash)
            if (privateAttachment?.thumbHash == hash) {
                return FileInfo(File(privateAttachment.thumbnailUri!!.path), 0L, privateAttachment.thumbRandom!!, privateAttachment.thumbHash!!)
            }

            val groupMessage = MessageDataManager.getExistThumbnailData(hash)
            if (groupMessage?.thumbHash == hash) {
                return FileInfo(File(Uri.parse(groupMessage.thumbnailUri).path), 0L, groupMessage.thumbRandom!!, groupMessage.thumbHash!!)
            }
        } else {
            val privateAttachment = Repository.getAttachmentRepo().getExistAttachmentData(hash)
            if (privateAttachment?.dataHash == hash) {
                return FileInfo(File(privateAttachment.dataUri!!.path), privateAttachment.dataSize, privateAttachment.dataRandom!!, privateAttachment.dataHash!!)
            }

            val groupMessage = MessageDataManager.getExistAttachmentData(hash)
            if (groupMessage?.dataHash == hash) {
                return FileInfo(File(Uri.parse(groupMessage.attachment_uri).path), groupMessage.attachmentSize, groupMessage.dataRandom!!, groupMessage.dataHash!!)
            }
        }
        return null
    }
}