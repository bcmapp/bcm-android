package com.bcm.messenger.common.database.records

import androidx.room.Relation
import com.bcm.messenger.common.database.model.AttachmentDbModel
import com.bcm.messenger.common.database.model.PrivateChatDbModel

/**
 * Created by Kin on 2019/9/18
 */
open class ChatMessageModel : PrivateChatDbModel() {
    @Relation(parentColumn = "id", entityColumn = "mid", entity = AttachmentDbModel::class)
    var attachments = listOf<AttachmentRecord>()

    fun hasImages(): Boolean {
        attachments.forEach {
            if (it.attachmentType == AttachmentDbModel.AttachmentType.IMAGE.type) {
                return true
            }
        }
        return false
    }

    fun hasAudios(): Boolean {
        attachments.forEach {
            if (it.attachmentType == AttachmentDbModel.AttachmentType.AUDIO.type ||
                    it.attachmentType == AttachmentDbModel.AttachmentType.VOICE_NOTE.type) {
                return true
            }
        }
        return false
    }

    fun hasVideos(): Boolean {
        attachments.forEach {
            if (it.attachmentType == AttachmentDbModel.AttachmentType.VIDEO.type) {
                return true
            }
        }
        return false
    }

    fun hasDocuments(): Boolean {
        attachments.forEach {
            if (it.attachmentType == AttachmentDbModel.AttachmentType.DOCUMENT.type) {
                return true
            }
        }
        return false
    }

    fun getImageAttachment(): AttachmentRecord? {
        attachments.forEach {
            if (it.attachmentType == AttachmentDbModel.AttachmentType.IMAGE.type) {
                return it
            }
        }
        return null
    }

    fun getVideoAttachment(): AttachmentRecord? {
        attachments.forEach {
            if (it.attachmentType == AttachmentDbModel.AttachmentType.VIDEO.type) {
                return it
            }
        }
        return null
    }

    fun getAudioAttachment(): AttachmentRecord? {
        attachments.forEach {
            if (it.attachmentType == AttachmentDbModel.AttachmentType.AUDIO.type ||
                    it.attachmentType == AttachmentDbModel.AttachmentType.VOICE_NOTE.type) {
                return it
            }
        }
        return null
    }

    fun getDocumentAttachment(): AttachmentRecord? {
        attachments.forEach {
            if (it.attachmentType == AttachmentDbModel.AttachmentType.DOCUMENT.type) {
                return it
            }
        }
        return null
    }

    fun getMediaAttachment(): AttachmentRecord? {
        attachments.forEach {
            if (it.attachmentType == AttachmentDbModel.AttachmentType.IMAGE.type ||
                    it.attachmentType == AttachmentDbModel.AttachmentType.VIDEO.type) {
                return it
            }
        }
        return null
    }

    fun hasAttachments() = attachments.isNotEmpty()
}