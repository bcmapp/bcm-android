package com.bcm.messenger.common.database.repositories

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.attachments.Attachment
import com.bcm.messenger.common.crypto.CtrStreamUtil
import com.bcm.messenger.common.crypto.DecryptingPartInputStream
import com.bcm.messenger.common.crypto.EncryptingPartOutputStream
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.database.model.AttachmentDbModel
import com.bcm.messenger.common.database.records.AttachmentRecord
import com.bcm.messenger.common.event.PartProgressEvent
import com.bcm.messenger.common.mms.MediaStream
import com.bcm.messenger.common.mms.PartAuthority
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.MediaUtil
import com.bcm.messenger.common.crypto.encrypt.ChatFileEncryptDecryptUtil
import com.bcm.messenger.common.crypto.encrypt.FileInfo
import com.bcm.messenger.common.database.dao.AttachmentDao
import com.bcm.messenger.common.video.exo.CtrDataSource
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.BitmapUtils
import com.bcm.messenger.utility.Util
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import java.io.*
import java.util.concurrent.Callable

/**
 * Created by Kin on 2019/9/19
 */
class AttachmentRepo(
        private val accountContext: AccountContext,
        private val attachmentDao: AttachmentDao
) {
    private val TAG = "AttachmentRepo"

    private val thumbnailExecutor = Util.newSingleThreadedLifoExecutor()

    inner class ThumbnailFetchCallable(private val masterSecret: MasterSecret, private val attachmentId: Long, private val uniqueId: Long) : Callable<InputStream?> {
        override fun call(): InputStream? {
            val attachment = attachmentDao.queryAttachment(attachmentId, uniqueId) ?: return null
            try {
                if (attachment.thumbnailUri != null) {
                    val thumbnailFile = File(attachment.thumbnailUri?.path)
                    val inputStream = DecryptingPartInputStream.createFor(masterSecret, thumbnailFile)
                    if (inputStream != null) return inputStream
                }

                val dataFile = File(attachment.dataUri?.path)
                val bitmap = if (attachment.isVideo()) {
                    generateVideoThumbnail(masterSecret, attachment.dataRandom!!, dataFile, attachment.dataSize)
                } else {
                    MediaUtil.generateThumbnail(AppContextHolder.APP_CONTEXT, masterSecret, attachment.contentType, attachment.getPartUri())?.bitmap
                }
                bitmap ?: return null

                val fileInfo = ChatFileEncryptDecryptUtil.encryptLocalFile(masterSecret, ByteArrayInputStream(BitmapUtils.toByteArray(bitmap)))
                attachment.thumbnailUri = Uri.fromFile(fileInfo.file)
                attachment.thumbnailAspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                attachment.thumbHash = fileInfo.hash
                attachment.thumbRandom = fileInfo.random
                attachmentDao.updateAttachmentThumbnail(attachmentId, Uri.fromFile(fileInfo.file).toString(),
                        bitmap.width.toFloat() / bitmap.height.toFloat(),
                        fileInfo.hash, fileInfo.random)
                notifyAttachmentUpdate(attachment.mid)

                return CtrStreamUtil.createForDecryptingInputStream(masterSecret, fileInfo.random!!, fileInfo.file, 0)
            } catch (tr: Throwable) {
                ALog.w(TAG, "Thumbnail fetch error. Reason is ${tr.message}")
                return null
            }
        }
    }

    fun getExistThumbnailData(hash: String) = attachmentDao.queryExistThumbnail(hash)

    fun getExistThumbnailData(id: Long, uniqueId: Long, hash: String) = attachmentDao.queryExistThumbnail(id, uniqueId, hash)

    fun getExistAttachmentData(hash: String) = attachmentDao.queryExistAttachment(hash)

    fun getExistAttachmentData(id: Long, uniqueId: Long, hash: String) = attachmentDao.queryExistAttachment(id, uniqueId, hash)

    fun generateVideoThumbnail(masterSecret: MasterSecret?, random: ByteArray?, file: File, length: Long): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null
        }

        val retriever = MediaMetadataRetriever()
        val dataSource = CtrDataSource(masterSecret, file, random, length)
        retriever.setDataSource(dataSource)
        val bitmap = retriever.getFrameAtTime(1000)
        retriever.release()
        return bitmap
    }

    fun getMediaType(contentType: String): AttachmentDbModel.AttachmentType {
        return when {
            contentType.startsWith("image/") -> AttachmentDbModel.AttachmentType.IMAGE
            contentType.startsWith("video/") -> AttachmentDbModel.AttachmentType.VIDEO
            contentType == "audio/aac" -> AttachmentDbModel.AttachmentType.AUDIO
            else -> AttachmentDbModel.AttachmentType.DOCUMENT
        }
    }

    fun getAttachmentStream(masterSecret: MasterSecret, id: Long, uniqueId: Long, offset: Long = 0): InputStream? {
        val record = attachmentDao.queryAttachment(id, uniqueId)
        if (record != null) {
            try {
                val file = File(record.dataUri?.path)
                return if (record.isDataEncryptWithNewMethod()) {
                    CtrStreamUtil.createForDecryptingInputStream(masterSecret, record.dataRandom!!, file, offset)
                } else {
                    val stream = DecryptingPartInputStream.createFor(masterSecret, file)
                    val skipped = stream.skip(offset)

                    if (skipped != offset) {
                        ALog.w(TAG, "Skip failed: $skipped vs $offset")
                        return null
                    }

                    return stream
                }
            } catch (tr: Throwable) {
                tr.printStackTrace()
            }
        }
        return null
    }

    fun getThumbnailStream(masterSecret: MasterSecret, id: Long, uniqueId: Long): InputStream? {
        val record = attachmentDao.queryAttachment(id, uniqueId)
        if (record != null) {
            try {
                val file = File(record.thumbnailUri?.path)
                return if (record.isThumbnailEncryptWithNewMethod()) {
                    CtrStreamUtil.createForDecryptingInputStream(masterSecret, record.thumbRandom!!, file, 0)
                } else {
                    DecryptingPartInputStream.createFor(masterSecret, file)
                }
            } catch (tr: Throwable) {
                tr.printStackTrace()
            }
        }
        return null
    }

    @Throws(IOException::class)
    private fun setAttachmentData(masterSecret: MasterSecret, destination: File, inputStream: InputStream): Long {
        val out = EncryptingPartOutputStream(destination, masterSecret)
        return BcmFileUtils.copy(inputStream, out)
    }

    private fun createTempFile(inputStream: InputStream): File? {
        var file: File? = null
        try {
            file = File.createTempFile("push-attachment", "tmp", AppContextHolder.APP_CONTEXT.cacheDir)
            val outputStream = FileOutputStream(file)
            Util.copy(inputStream, outputStream)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        file?.deleteOnExit()
        return file
    }

    // IO functions

    fun getAttachments(messageId: Long): List<AttachmentRecord> {
        return attachmentDao.queryAttachments(messageId)
    }

    fun getAttachment(id: Long, uniqueId: Long): AttachmentRecord? {
        return attachmentDao.queryAttachment(id, uniqueId)
    }

    fun setTransferStateAsync(record: AttachmentRecord, newState: AttachmentDbModel.TransferState) {
        Observable.create<Unit> {
            setTransferState(record, newState)
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .subscribe()
    }

    fun setTransferState(record: AttachmentRecord, newState: AttachmentDbModel.TransferState) {
        attachmentDao.updateAttachmentTransferState(record.id, newState.state)
        notifyAttachmentUpdate(record.mid)
    }

    fun setTransferState(id: Long, uniqueId: Long, newState: AttachmentDbModel.TransferState) {
        val record = attachmentDao.queryAttachment(id, uniqueId)
        if (record != null) {
            attachmentDao.updateAttachmentTransferState(record.id, newState.state)
            notifyAttachmentUpdate(record.mid)
        }
    }

    fun cleanUris(id: Long, uniqueId: Long) {
        val record = attachmentDao.queryAttachment(id, uniqueId)
        if (record != null) {
            attachmentDao.cleanAttachmentUris(id, uniqueId)
            notifyAttachmentUpdate(record.mid)
        }
    }

    fun insertForPlaceholder(masterSecret: MasterSecret, id: Long, uniqueId: Long, fileInfo: FileInfo): Long {
        val record = attachmentDao.queryAttachment(id, uniqueId)
        val encryptedFile = fileInfo.file
        if (record != null) {
            if (attachmentDao.updateAttachmentData(id, Uri.fromFile(encryptedFile).toString(), fileInfo.size,
                            fileInfo.hash, fileInfo.random, AttachmentDbModel.TransferState.DONE.state) == 0) {
                encryptedFile.delete()
            } else {
                val newRecord = attachmentDao.queryAttachment(id, uniqueId)
                if (newRecord != null) {
                    notifyAttachmentUpdate(newRecord.mid)
                    thumbnailExecutor.submit(ThumbnailFetchCallable(masterSecret, id, uniqueId))
                }
            }
            return encryptedFile.length()
        }

        return 0L
    }

    fun updateAttachmentThumbnail(masterSecret: MasterSecret, id: Long, uniqueId: Long, inputStream: InputStream, aspectRatio: Float) {
        val record = attachmentDao.queryAttachmentByUniqueId(uniqueId) ?: return
        val fileInfo = ChatFileEncryptDecryptUtil.encryptLocalFile(masterSecret, inputStream)
        attachmentDao.updateAttachmentThumbnail(id, Uri.fromFile(fileInfo.file).toString(), aspectRatio, fileInfo.hash, fileInfo.random)
        notifyAttachmentUpdate(record.mid)

        EventBus.getDefault().post(PartProgressEvent(record, fileInfo.size, fileInfo.size))
    }

    fun insertAttachments(attachments: List<Attachment>, masterSecret: MasterSecret, messageId: Long): List<AttachmentRecord> {
        val attachmentList = mutableListOf<AttachmentRecord>()
        attachments.forEach {
            val attachmentModel = AttachmentRecord()

            var fileInfo: FileInfo? = null
            if (it.dataUri != null) {
                try {
                    fileInfo = ChatFileEncryptDecryptUtil.encryptLocalFile(masterSecret, PartAuthority.getAttachmentStream(AppContextHolder.APP_CONTEXT, masterSecret, it.dataUri!!))
                } catch (tr: Throwable) {
                }
            }

            attachmentModel.mid = messageId
            attachmentModel.contentType = it.contentType
            attachmentModel.fileName = it.fileName
            attachmentModel.transferState = it.transferState
            attachmentModel.name = it.relay
            attachmentModel.digest = it.digest ?: byteArrayOf()
            attachmentModel.uniqueId = System.currentTimeMillis()
            attachmentModel.dataSize = it.size
            attachmentModel.fastPreflightId = it.fastPreflightId
            attachmentModel.url = it.url
            attachmentModel.contentLocation = it.location.orEmpty()
            attachmentModel.contentKey = it.key.orEmpty()
            attachmentModel.attachmentType = getMediaType(it.contentType).type
            attachmentModel.duration = it.duration
            if (it.isVoiceNote) {
                attachmentModel.attachmentType = AttachmentDbModel.AttachmentType.VOICE_NOTE.type
            }

            if (fileInfo != null) {
                attachmentModel.dataUri = Uri.fromFile(fileInfo.file)
                attachmentModel.dataSize = fileInfo.size
                attachmentModel.dataHash = fileInfo.hash
                attachmentModel.dataRandom = fileInfo.random
            }

            val id = attachmentDao.insertAttachment(attachmentModel)
            attachmentModel.id = id

            if (fileInfo != null) {
                if (attachmentModel.isVideo()) {
                    var bitmap = MediaUtil.getVideoThumbnail(AppContextHolder.APP_CONTEXT, it.dataUri)
                    if (bitmap == null) {
                        bitmap = BcmFileUtils.getVideoFrameBitmap(AppContextHolder.APP_CONTEXT, it.dataUri)
                    }
                    if (bitmap != null) {
                        updateAttachmentThumbnail(masterSecret, attachmentModel.id, attachmentModel.uniqueId, ByteArrayInputStream(BitmapUtils.toByteArray(bitmap)), bitmap.width.toFloat() / bitmap.height.toFloat())
                    } else {
                        thumbnailExecutor.submit(ThumbnailFetchCallable(masterSecret, id, attachmentModel.uniqueId))
                    }
                } else {
                    thumbnailExecutor.submit(ThumbnailFetchCallable(masterSecret, id, attachmentModel.uniqueId))
                }
            }

            attachmentList.add(attachmentModel)
        }

        return attachmentList
    }

    fun setAttachmentUploaded(attachments: List<AttachmentRecord>) {
        if (attachments.isNotEmpty()) {
            attachments.forEach {
                attachmentDao.updateAttachmentTransferState(it.id, AttachmentDbModel.TransferState.DONE.state)
            }
            notifyAttachmentUpdate(attachments[0].mid)
        }
    }

    @Throws(IOException::class)
    fun updateAttachmentData(masterSecret: MasterSecret, record: AttachmentRecord, mediaStream: MediaStream): AttachmentRecord {
        try {
            val dataFile = File(record.dataUri?.path)
            val dataSize = setAttachmentData(masterSecret, dataFile, mediaStream.stream)
            record.dataSize = dataSize
            record.contentType = mediaStream.mimeType
            attachmentDao.updateAttachmentDataSizeAndType(record.id, dataSize, mediaStream.mimeType)
            notifyAttachmentUpdate(record.mid)

            return record
        } catch (tr: Throwable) {
            tr.printStackTrace()
            throw IOException("Update attachment failed")
        }
    }

    fun updateDuration(id: Long, uniqueId: Long, duration: Long) {
        val record = attachmentDao.queryAttachment(id, uniqueId)
        if (record != null) {
            attachmentDao.updateAttachmentDuration(id, duration)
            notifyAttachmentUpdate(record.mid)
        }
    }

    fun updateDuration(id: Long, duration: Long) {
        val record = attachmentDao.queryAttachment(id)
        if (record != null) {
            attachmentDao.updateAttachmentDuration(id, duration)
            notifyAttachmentUpdate(record.mid)
        }
    }

    fun updateFileName(id: Long, uniqueId: Long, fileName: String) {
        val record = attachmentDao.queryAttachment(id, uniqueId)
        if (record != null) {
            attachmentDao.updateAttachmentFileName(id, fileName)
            notifyAttachmentUpdate(record.mid)
        }
    }

    private fun notifyAttachmentUpdate(mid: Long) {
        Repository.getChatRepo(accountContext)?.attachmentUpdate(mid)
    }
}