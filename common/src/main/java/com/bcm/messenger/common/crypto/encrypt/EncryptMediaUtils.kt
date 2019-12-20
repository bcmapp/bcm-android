package com.bcm.messenger.common.crypto.encrypt

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    fun encryptVideo(masterSecret: MasterSecret, gid: Long, videoPath: String): Pair<EncryptResult?, EncryptResult?> {
        val triple = BcmFileUtils.getVideoFrameInfo(videoPath)
        val groupInfo = GroupInfoDataManager.queryOneGroupInfo(gid) ?: return Pair(null, null)
        val keyParam = GroupKeyParam(groupInfo.currentKey.base64Decode(), groupInfo.currentKeyVersion)
        try {
            if (triple.first != null) {
                val compressedData = compressBitmap(triple.first!!, triple.second, triple.third, true)

                val thumbnailFile = ChatFileEncryptDecryptUtil.encryptGroupFile(compressedData.first, keyParam)
                val thumbnailLocal = ChatFileEncryptDecryptUtil.encryptLocalFile(masterSecret, FileInputStream(compressedData.first))
                File(triple.first).delete()
                File(compressedData.first).delete()

                val videoFile = ChatFileEncryptDecryptUtil.encryptGroupFile(videoPath, keyParam)
                val videoLocal = ChatFileEncryptDecryptUtil.encryptLocalFile(masterSecret, FileInputStream(videoPath))

                return Pair(EncryptResult(videoLocal, videoFile, triple.second, triple.third),
                        EncryptResult(thumbnailLocal, thumbnailFile, compressedData.second, compressedData.third))
            } else {
                val videoFile = ChatFileEncryptDecryptUtil.encryptGroupFile(videoPath, keyParam)
                val videoLocal = ChatFileEncryptDecryptUtil.encryptLocalFile(masterSecret, FileInputStream(videoPath))
                return Pair(EncryptResult(videoLocal, videoFile, triple.second, triple.third),
                        EncryptResult(null, null, 0, 0))
            }
        } catch (tr: Throwable) {
            ALog.w(TAG, "Encrypt video failed. ${tr.message}")
        }

        return Pair(null, null)
    }

    fun encryptImage(masterSecret: MasterSecret, gid: Long, path: String): Pair<EncryptResult?, EncryptResult?> {
        val groupInfo = GroupInfoDataManager.queryOneGroupInfo(gid) ?: return Pair(null, null)
        val keyParam = GroupKeyParam(groupInfo.currentKey.base64Decode(), groupInfo.currentKeyVersion)

        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, options)
        val width = options.outWidth
        val height = options.outHeight

        val compressedData = compressBitmap(path, width, height, false)

        try {
            val imageFile = ChatFileEncryptDecryptUtil.encryptGroupFile(path, keyParam)
            val imageLocal = ChatFileEncryptDecryptUtil.encryptLocalFile(masterSecret, FileInputStream(path))

            val thumbnailFile = ChatFileEncryptDecryptUtil.encryptGroupFile(compressedData.first, keyParam)
            val thumbnailLocal = ChatFileEncryptDecryptUtil.encryptLocalFile(masterSecret, FileInputStream(compressedData.first))
            File(compressedData.first).delete()

            return Pair(EncryptResult(imageLocal, imageFile, width, height),
                    EncryptResult(thumbnailLocal, thumbnailFile, compressedData.second, compressedData.third))
        } catch (tr: Throwable) {
            ALog.w(TAG, "Encrypt image failed. ${tr.message}")
        }

        return Pair(null, null)
    }

    fun encryptFile(masterSecret: MasterSecret, gid: Long, filePath: String): EncryptResult? {
        val groupInfo = GroupInfoDataManager.queryOneGroupInfo(gid) ?: return null
        val keyParam = GroupKeyParam(groupInfo.currentKey.base64Decode(), groupInfo.currentKeyVersion)

        try {
            val remoteFile = ChatFileEncryptDecryptUtil.encryptGroupFile(filePath, keyParam)
            val localFile = ChatFileEncryptDecryptUtil.encryptLocalFile(masterSecret, FileInputStream(filePath))

            return EncryptResult(localFile, remoteFile, 0, 0)
        } catch (tr: Throwable) {
            ALog.w(TAG, "Encrypt file failed. ${tr.message}")
        }

        return null
    }

    private fun compressBitmap(path: String, currentWidth: Int, currentHeight: Int, isVideo: Boolean): Triple<String, Int, Int> {
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
                val localPath = "${AmeFileUploader.THUMBNAIL_DIRECTORY}${File.separatorChar}${System.currentTimeMillis()}.thumb"
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