package com.bcm.messenger.common.database.records

import android.net.Uri
import com.bcm.messenger.common.database.model.AttachmentDbModel
import com.bcm.messenger.common.mms.PartAuthority

/**
 * Created by Kin on 2019/9/18
 */
class AttachmentRecord : AttachmentDbModel() {
    fun isImage() = attachmentType == AttachmentType.IMAGE.type

    fun isGif() = attachmentType == AttachmentType.IMAGE.type && contentType == "image/gif"

    fun isVideo() = attachmentType == AttachmentType.VIDEO.type

    fun isAudio() = attachmentType == AttachmentType.AUDIO.type

    fun isDocument() = attachmentType == AttachmentType.DOCUMENT.type

    fun isVoiceNote() = attachmentType == AttachmentType.VOICE_NOTE.type

    fun isInProgress() = transferState != TransferState.DONE.state && transferState != TransferState.FAILED.state

    fun isPendingDownload() = transferState == TransferState.FAILED.state || transferState == TransferState.PENDING.state

    fun getPartUri() = if (dataUri == null) null else PartAuthority.getAttachmentDataUri(id, uniqueId)

    fun getThumbnailPartUri(): Uri? {
        return when {
            isGif() -> getPartUri()
            thumbnailUri == null -> null
            else -> PartAuthority.getAttachmentThumbnailUri(id, uniqueId)
        }
    }

    fun isDataEncryptWithNewMethod() = dataHash != null && dataRandom != null

    fun isThumbnailEncryptWithNewMethod() = thumbHash != null && thumbRandom != null
}