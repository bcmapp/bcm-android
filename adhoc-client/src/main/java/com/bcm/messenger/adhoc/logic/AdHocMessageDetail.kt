package com.bcm.messenger.adhoc.logic

import android.net.Uri
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.utility.logger.ALog
import java.io.File

/**
 * adhoc message class
 * Created by wjh on 2019/7/26
 */
data class AdHocMessageDetail(val indexId: Long, val sessionId: String, val fromId: String) {

    private val TAG = "AdHocMessageDetail"

    var mid: String = ""
    var nickname: String = ""
    var success: Boolean = false //receive success
    private var text: String = ""
    var time: Long = 0
    var sendByMe: Boolean = false
    var isRead: Boolean = false
    var extContent: String? = null
    var atList: Set<String>? = null

    var isSending: Boolean = false
    var isAtMe: Boolean = false

    private var messageType: Long = AmeGroupMessage.NONSUPPORT
    private var messageBody: AmeGroupMessage<*>? = null

    var thumbnailUri: String? = null
    var attachmentUri: String? = null
    var attachmentState: Boolean = false

    var isAttachmentDownloading: Boolean = false
    var attachmentProgress: Float = 0.0F

    var attachmentDigest: String? = null

    fun getContentType(): String? {
        val content = messageBody?.content as? AmeGroupMessage.AttachmentContent
        return content?.mimeType
    }

    fun toThumbnailUri(): Uri? {
        try {
            if (isAttachmentComplete(thumbnailUri)) {
                return if (thumbnailUri?.startsWith("/data") == true) {
                    Uri.fromFile(File(thumbnailUri))
                } else {
                    Uri.parse(thumbnailUri)
                }
            }
        }catch (ex: Exception) {
            ALog.e(TAG, "toThumbnailUri error", ex)
        }
        return null
    }

    fun toAttachmentUri(): Uri? {
        try {
            if (isAttachmentComplete(attachmentUri)) {
                return if (attachmentUri?.startsWith("/data") == true) {
                    Uri.fromFile(File(attachmentUri))
                } else {
                    Uri.parse(attachmentUri)
                }
            }
        }catch (ex: Exception) {
            ALog.e(TAG, "toAttachmentUri error", ex)
        }
        return null
    }

    fun setText(text: String) {
        setMessageBody(AmeGroupMessage(AmeGroupMessage.TEXT, AmeGroupMessage.TextContent(text)))
    }

    fun getMessageBodyJson(): String {
        return this.text
    }

    fun getMessageBodyType(): Long {
        return this.messageType
    }

    fun setMessageBodyJson(bodyJson: String) {
        setMessageBody(try {
            AmeGroupMessage.messageFromJson(bodyJson)
        }catch (ex: Exception) {
            AmeGroupMessage(AmeGroupMessage.TEXT, AmeGroupMessage.TextContent(bodyJson))
        })
    }

    fun setMessageBody(messageBody: AmeGroupMessage<*>) {
        this.messageBody = messageBody
        this.messageType = messageBody.type
        this.text = messageBody.toString()
    }

    fun getMessageBody(): AmeGroupMessage<*>? {
        return messageBody
    }

    fun getLastSessionState(): Int {
        return when {
            isSending -> AdHocSession.STATE_SENDING
            success -> AdHocSession.STATE_SUCCESS
            else -> AdHocSession.STATE_FAILURE
        }
    }

    fun isForwardable(): Boolean {
        return messageBody?.type == AmeGroupMessage.TEXT || messageBody?.type == AmeGroupMessage.ADHOC_INVITE || toAttachmentUri() != null
    }

    private fun isAttachmentComplete(uriString: String?): Boolean {
        var exist = false
        try {
            if (!uriString.isNullOrEmpty()) {
                val uri = Uri.parse(uriString)
                if (uri.scheme.equals("content", ignoreCase = true)) {
                    exist = true
                } else if (uri.scheme.equals("file", ignoreCase = true)) {
                    val path = uri.path
                    exist = BcmFileUtils.isExist(path)
                }
            }
        } catch (ex: Exception) {
            ALog.e(TAG, "isAttachmentComplete error", ex)
            exist = false
        }

        return exist
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AdHocMessageDetail

        if (indexId != other.indexId) return false
        if (sessionId != other.sessionId) return false
        if (fromId != other.fromId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = indexId.hashCode()
        result = 31 * result + sessionId.hashCode()
        result = 31 * result + fromId.hashCode()
        return result
    }


}