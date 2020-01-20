package com.bcm.messenger.common.crypto.encrypt

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeFileUploader
import com.bcm.messenger.common.core.corebean.GroupKeyParam
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.grouprepository.manager.GroupInfoDataManager
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.base64Decode
import com.bcm.messenger.utility.BitmapUtils
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.max

object EncryptMediaUtils {
    private val TAG = "EncryptMediaUtils"
    private val MAX_THUMBNAIL_SIZE = 300
    private val encodeDispatcher = AmeDispatcher.newDispatcher(1)
    private val decodeDispatcher = AmeDispatcher.newDispatcher(1)

    class EncryptResult(val localFileInfo: FileInfo?, val groupFileInfo: GroupFileInfo?, val width: Int, val height: Int) {
        fun isValid() = groupFileInfo != null && localFileInfo != null
    }

    class StreamEncryptResult(val localFileInfo: FileInfo?, val groupStreamInfo: GroupStreamInfo?, val width: Int, val height: Int) {
        fun isValid() = groupStreamInfo != null && localFileInfo != null
    }

    fun encryptVideo(masterSecret: MasterSecret, gid: Long, videoPath: String): Pair<EncryptResult?, EncryptResult?> {
        val triple = BcmFileUtils.getVideoFrameInfo(masterSecret.accountContext, videoPath)
        val groupInfo = GroupInfoDataManager.queryOneGroupInfo(masterSecret.accountContext, gid) ?: return Pair(null, null)
        val keyParam = GroupKeyParam(groupInfo.currentKey.base64Decode(), groupInfo.currentKeyVersion)
        try {
            if (triple.first != null) {
                val compressedData = compressBitmap(masterSecret.accountContext, triple.first!!, triple.second, triple.third, true)

                val thumbnailFile = ChatFileEncryptDecryptUtil.encryptGroupFile(masterSecret.accountContext, compressedData.first, keyParam)
                val thumbnailLocal = ChatFileEncryptDecryptUtil.encryptLocalFile(masterSecret, FileInputStream(compressedData.first))
                File(triple.first).delete()
                File(compressedData.first).delete()

                val videoFile = ChatFileEncryptDecryptUtil.encryptGroupFile(masterSecret.accountContext, videoPath, keyParam)
                val videoLocal = ChatFileEncryptDecryptUtil.encryptLocalFile(masterSecret, FileInputStream(videoPath))

                return Pair(EncryptResult(videoLocal, videoFile, triple.second, triple.third),
                        EncryptResult(thumbnailLocal, thumbnailFile, compressedData.second, compressedData.third))
            } else {
                val videoFile = ChatFileEncryptDecryptUtil.encryptGroupFile(masterSecret.accountContext, videoPath, keyParam)
                val videoLocal = ChatFileEncryptDecryptUtil.encryptLocalFile(masterSecret, FileInputStream(videoPath))
                return Pair(EncryptResult(videoLocal, videoFile, triple.second, triple.third),
                        EncryptResult(null, null, 0, 0))
            }
        } catch (tr: Throwable) {
            ALog.w(TAG, "Encrypt video failed. ${tr.message}")
        }

        return Pair(null, null)
    }

    // For next version to enable
//    fun encryptVideo(masterSecret: MasterSecret, gid: Long, videoPath: String): Pair<StreamEncryptResult?, StreamEncryptResult?> {
//        val triple = BcmFileUtils.getVideoFrameInfo(videoPath)
//        val groupInfo = GroupInfoDataManager.queryOneGroupInfo(gid) ?: return Pair(null, null)
//        val keyParam = GroupKeyParam(groupInfo.currentKey.base64Decode(), groupInfo.currentKeyVersion)
//        try {
//            if (triple.first != null) {
//                val compressedData = compressBitmap(triple.first!!, triple.second, triple.third, true)
//
//                val thumbnailFile = ChatFileEncryptDecryptUtil.encryptGroupFileStream(compressedData.first, keyParam)
//                val thumbnailLocal = ChatFileEncryptDecryptUtil.encryptLocalFile(masterSecret, FileInputStream(compressedData.first))
//                File(triple.first).delete()
//                File(compressedData.first).delete()
//
//                val videoFile = ChatFileEncryptDecryptUtil.encryptGroupFileStream(videoPath, keyParam)
//                val videoLocal = ChatFileEncryptDecryptUtil.encryptLocalFile(masterSecret, FileInputStream(videoPath))
//
//                return Pair(StreamEncryptResult(videoLocal, videoFile, triple.second, triple.third),
//                        StreamEncryptResult(thumbnailLocal, thumbnailFile, compressedData.second, compressedData.third))
//            } else {
//                val videoFile = ChatFileEncryptDecryptUtil.encryptGroupFileStream(videoPath, keyParam)
//                val videoLocal = ChatFileEncryptDecryptUtil.encryptLocalFile(masterSecret, FileInputStream(videoPath))
//                return Pair(StreamEncryptResult(videoLocal, videoFile, triple.second, triple.third),
//                        StreamEncryptResult(null, null, 0, 0))
//            }
//        } catch (tr: Throwable) {
//            ALog.w(TAG, "Encrypt video failed. ${tr.message}")
//        }
//
//        return Pair(null, null)
//    }

    fun encryptImage(masterSecret: MasterSecret, gid: Long, path: String): Pair<EncryptResult?, EncryptResult?> {
        val groupInfo = GroupInfoDataManager.queryOneGroupInfo(masterSecret.accountContext, gid) ?: return Pair(null, null)
        val keyParam = GroupKeyParam(groupInfo.currentKey.base64Decode(), groupInfo.currentKeyVersion)

        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, options)
        val width = options.outWidth
        val height = options.outHeight

        val compressedData = compressBitmap(masterSecret.accountContext, path, width, height, false)

        try {
            val imageFile = ChatFileEncryptDecryptUtil.encryptGroupFile(masterSecret.accountContext, path, keyParam)
            val imageLocal = ChatFileEncryptDecryptUtil.encryptLocalFile(masterSecret, FileInputStream(path))

            val thumbnailFile = ChatFileEncryptDecryptUtil.encryptGroupFile(masterSecret.accountContext, compressedData.first, keyParam)
            val thumbnailLocal = ChatFileEncryptDecryptUtil.encryptLocalFile(masterSecret, FileInputStream(compressedData.first))
            File(compressedData.first).delete()

            return Pair(EncryptResult(imageLocal, imageFile, width, height),
                    EncryptResult(thumbnailLocal, thumbnailFile, compressedData.second, compressedData.third))
        } catch (tr: Throwable) {
            ALog.w(TAG, "Encrypt image failed. ${tr.message}")
        }

        return Pair(null, null)
    }

    // For next version to enable
//    fun encryptImage(masterSecret: MasterSecret, gid: Long, path: String): Pair<StreamEncryptResult?, StreamEncryptResult?> {
//        val groupInfo = GroupInfoDataManager.queryOneGroupInfo(gid) ?: return Pair(null, null)
//        val keyParam = GroupKeyParam(groupInfo.currentKey.base64Decode(), groupInfo.currentKeyVersion)
//
//        val options = BitmapFactory.Options()
//        options.inJustDecodeBounds = true
//        BitmapFactory.decodeFile(path, options)
//        val width = options.outWidth
//        val height = options.outHeight
//
//        val compressedData = compressBitmap(path, width, height, false)
//
//        try {
//            val imageFile = ChatFileEncryptDecryptUtil.encryptGroupFileStream(path, keyParam)
//            val imageLocal = ChatFileEncryptDecryptUtil.encryptLocalFile(masterSecret, FileInputStream(path))
//
//            val thumbnailFile = ChatFileEncryptDecryptUtil.encryptGroupFileStream(compressedData.first, keyParam)
//            val thumbnailLocal = ChatFileEncryptDecryptUtil.encryptLocalFile(masterSecret, FileInputStream(compressedData.first))
//            File(compressedData.first).delete()
//
//            return Pair(StreamEncryptResult(imageLocal, imageFile, width, height),
//                    StreamEncryptResult(thumbnailLocal, thumbnailFile, compressedData.second, compressedData.third))
//        } catch (tr: Throwable) {
//            ALog.w(TAG, "Encrypt image failed. ${tr.message}")
//        }
//
//        return Pair(null, null)
//    }

    fun encryptFile(masterSecret: MasterSecret, gid: Long, filePath: String): EncryptResult? {
        val groupInfo = GroupInfoDataManager.queryOneGroupInfo(masterSecret.accountContext, gid) ?: return null
        val keyParam = GroupKeyParam(groupInfo.currentKey.base64Decode(), groupInfo.currentKeyVersion)

        try {
            val remoteFile = ChatFileEncryptDecryptUtil.encryptGroupFile(masterSecret.accountContext, filePath, keyParam)
            val localFile = ChatFileEncryptDecryptUtil.encryptLocalFile(masterSecret, FileInputStream(filePath))

            return EncryptResult(localFile, remoteFile, 0, 0)
        } catch (tr: Throwable) {
            ALog.w(TAG, "Encrypt file failed. ${tr.message}")
        }

        return null
    }

    // For next version to enable
//    fun encryptFile(masterSecret: MasterSecret, gid: Long, filePath: String): StreamEncryptResult? {
//        val groupInfo = GroupInfoDataManager.queryOneGroupInfo(gid) ?: return null
//        val keyParam = GroupKeyParam(groupInfo.currentKey.base64Decode(), groupInfo.currentKeyVersion)
//
//        try {
//            val remoteFile = ChatFileEncryptDecryptUtil.encryptGroupFileStream(filePath, keyParam)
//            val localFile = ChatFileEncryptDecryptUtil.encryptLocalFile(masterSecret, FileInputStream(filePath))
//
//            return StreamEncryptResult(localFile, remoteFile, 0, 0)
//        } catch (tr: Throwable) {
//            ALog.w(TAG, "Encrypt file failed. ${tr.message}")
//        }
//
//        return null
//    }

    private fun compressBitmap(accountContext: AccountContext, path: String, currentWidth: Int, currentHeight: Int, isVideo: Boolean): Triple<String, Int, Int> {
        val destWidth: Int
        val destHeight: Int

        val maxSize = max(currentWidth, currentHeight)
        if (maxSize > MAX_THUMBNAIL_SIZE) {
            if (maxSize == currentWidth) {
                destHeight = MAX_THUMBNAIL_SIZE * currentHeight / maxSize
                destWidth = MAX_THUMBNAIL_SIZE
            } else {
                destHeight = MAX_THUMBNAIL_SIZE
                destWidth = MAX_THUMBNAIL_SIZE * currentWidth / maxSize
            }

            var bitmap = BitmapUtils.getImageThumbnail(path, destWidth, destHeight)
            if (!isVideo) {
                val degree = BitmapUtils.readPictureDegree(path)
                bitmap = BitmapUtils.rotateBitmapByDegree(bitmap, degree)
            }
            var outputStream: OutputStream? = null

            try {
                val localPath = "${AmeFileUploader.get(accountContext).THUMBNAIL_DIRECTORY}${File.separatorChar}${System.currentTimeMillis()}.thumb"
                val file = if (isVideo) File(path).apply { delete() } else File(localPath).apply { createNewFile() }
                outputStream = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)

                return Triple(file.path, destWidth, destHeight)
            } catch (tr: Throwable) {
                ALog.w(TAG, "Compress bitmap failed. ${tr.message}")
            } finally {
                try {
                    outputStream?.close()
                } catch (tr: Throwable) {}
            }
        }

        return Triple(path, currentWidth, currentHeight)
    }
}