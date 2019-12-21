package com.bcm.messenger.common.grouprepository.model

import android.net.Uri
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.corebean.HistoryMessageDetail
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.MediaUtil
import java.io.File

/**
 * Created by Kin on 2018/10/25
 */
class AmeHistoryMessageDetail : AmeGroupMessageDetail() {
    var thumbPsw: HistoryMessageDetail.PswBean? = null
    var attachmentPsw: HistoryMessageDetail.PswBean? = null

    var mediaIndex = -1

    override fun isFileEncrypted() = false

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as AmeHistoryMessageDetail

        if (message != other.message) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (message?.hashCode() ?: 0)
        return result
    }

    override fun getFilePartUri(): Uri? {
        val resultUri = toAttachmentUri()
        if (resultUri != null) {
            return resultUri
        }
        val content = message.content as AmeGroupMessage.AttachmentContent
        val pathPair = content.getPath()

        val destPath = pathPair.second + File.separator + content.getExtension()
        if (content.isExist()) {
            return BcmFileUtils.getFileUri(destPath)
        }

        return null
    }

    override fun getThumbnailPartUri(): Uri? {
        if (message.isWithThumbnail()) {
            val content = message.content as AmeGroupMessage.ThumbnailContent

            val pathPair = content.getPath()
            val thumbnailPathPair = content.getThumbnailPath()
            var useThumbnail = true
            //gif，，，
            val isExist = if (content.thumbnail_url.isNullOrEmpty() || MediaUtil.isGif(content.mimeType)) {
                useThumbnail = false
                content.isExist()
            } else {
                content.isThumbnailExist()
            }
            val destPath = if (useThumbnail) {
                thumbnailPathPair.second + File.separator + content.getThumbnailExtension()
            } else {
                pathPair.second + File.separator + content.getExtension()
            }

            if (isExist) {
                return BcmFileUtils.getFileUri(destPath)
            }
        }

        return null
    }
}

