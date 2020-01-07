package com.bcm.messenger.adhoc.logic

import android.net.Uri
import android.util.ArrayMap
import com.bcm.messenger.adhoc.util.AdHocUtil
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.room.dao.AdHocMessageDao
import com.bcm.messenger.common.grouprepository.room.entity.AdHocMessageDBEntity
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import java.io.File

/**
 * adhoc cache
 */
class AdHocMessageCache {

    companion object {
        private val TAG = "AdHocMessageCache"
        private var mHandingMap = ArrayMap<String, AdHocMessageDetail>()
        private var mFinishMap = ArrayMap<String, Uri?>()
    }

    @Throws(Exception::class)
    private fun getDao(): AdHocMessageDao {
        return Repository.getAdHocMessageRepo(AMELogin.majorContext) ?: throw Exception("getDao fail")
    }

    private fun getKey(sessionId: String, mid: String): String {
        return sessionId + "_" + mid
    }

    fun getMessageDetail(sessionId: String, indexId: Long): AdHocMessageDetail? {
        return getDao().findMessage(sessionId, indexId)?.let {
            transform(it)
        }
    }

    fun getMessageDetail(sessionId: String, mid: String): AdHocMessageDetail? {
        var result: AdHocMessageDetail? = null
        getDao().findMessage(sessionId, mid)?.let {
            result = transform(it)
        }
        return result
    }

    @Synchronized
    fun mergeCacheForAttachment(sessionId: String, mid: String, detail: AdHocMessageDetail): AdHocMessageDetail {
        val key = getKey(sessionId, mid)
        if (mFinishMap.containsKey(key)) {
            detail.isSending = false
            detail.isAttachmentDownloading = false
            val result = mFinishMap[key]
            if (result == null) {
                detail.attachmentState = false
                detail.attachmentUri = null
                detail.thumbnailUri = null

            } else {
                detail.attachmentState = checkAttachmentLegal(result, detail.attachmentDigest)
                if (detail.attachmentState) {
                    detail.attachmentUri = result.toString()
                    if (detail.getMessageBody()?.isVideo() == true) { //video need first frame
                        detail.thumbnailUri = Uri.fromFile(File(BcmFileUtils.getVideoFramePath(AppContextHolder.APP_CONTEXT, result))).toString()
                    } else {
                        detail.thumbnailUri = detail.attachmentUri
                    }
                } else {
                    detail.attachmentUri = null
                    detail.thumbnailUri = null
                }
            }
            mHandingMap.remove(key)
            mFinishMap.remove(key)
            return updateMessage(detail)
        } else {
            ALog.i(TAG, "mergeCacheFroAttachment sessionId: $sessionId, mid: $mid, add handlingMap")
            mHandingMap[key] = detail
            return detail
        }
    }

    @Synchronized
    fun mergeCacheForAttachment(sessionId: String, mid: String, progress: Float, uri: Uri?): AdHocMessageDetail? {
        val key = getKey(sessionId, mid)
        var m = mHandingMap[key]
        if (m == null) {
            ALog.i(TAG, "mergeCacheFroAttachment sessionId: $sessionId, mid: $mid, add FinishMap")
            if (progress >= 1.0f) {
                // maybe attachment progress come first, so need put to finish map and wait for handle
                mFinishMap[key] = uri
            }
            return null
        }
        m.attachmentProgress = progress
        if (progress < 1.0f) { //just update cache, not database
            if (m.sendByMe) {
                m.isSending = true
                m.isAttachmentDownloading = false

            } else {
                m.isAttachmentDownloading = true
                m.isSending = false
            }

        } else {
            m.isSending = false
            m.isAttachmentDownloading = false
            if (uri != null) {
                m.attachmentState = checkAttachmentLegal(uri, m.attachmentDigest)
                if (m.attachmentState) {
                    var localUri = uri.toString()
                    if (localUri.endsWith("=\n")) {
                        localUri = localUri.replace("=\n", "")
                        File(uri.path).renameTo(File(Uri.parse(localUri).path))
                    }
                    if (localUri.endsWith("%3D%0A")) {
                        localUri = localUri.replace("%3D%0A", "")
                        File(uri.path).renameTo(File(Uri.parse(localUri).path))
                    }
                    m.attachmentUri = localUri
                    if (m.getMessageBody()?.isVideo() == true) { //for video, need first frame
                        m.thumbnailUri = Uri.fromFile(File(BcmFileUtils.getVideoFramePath(AppContextHolder.APP_CONTEXT, Uri.parse(localUri)))).toString()
                    } else {
                        m.thumbnailUri = m.attachmentUri
                    }
                } else {
                    m.attachmentUri = null
                    m.thumbnailUri = null
                }
            } else {
                m.attachmentState = false
                m.attachmentUri = null
                m.thumbnailUri = null
            }
            m = updateMessage(m)
            mHandingMap.remove(key)
            mFinishMap.remove(key)
        }
        return m
    }

    private fun checkAttachmentLegal(uri: Uri, digest: String?): Boolean {
        if (digest == null) {
            return true
        }
        return try {
            val file = File(BcmFileUtils.getFileAbsolutePath(AppContextHolder.APP_CONTEXT, uri)
                    ?: throw Exception("getAttachmentFilePath fail"))
            val result = AdHocUtil.digest(file)
            ALog.i(TAG, "checkAttachmentLegal result: $result, remote: $digest")
            result == digest
        } catch (ex: Exception) {
            ALog.e(TAG, "checkAttachmentLegal fail", ex)
            false
        }
    }

    fun clearHistory(sessionId: String) {
        getDao().deleteAllMessage(sessionId)
        AdHocSessionLogic.updateLastMessage(sessionId, "", AdHocSession.STATE_SUCCESS)

    }

    fun findUnreadCount(sessionId: String): Int {
        return getDao().queryUnreadCount(sessionId)
    }

    fun findLastSeen(sessionId: String): AdHocMessageDetail? {
        val entity = getDao().findLastSeen(sessionId)
        return if (entity != null) {
            transform(entity)
        } else {
            null
        }
    }

    fun readAll(sessionId: String) {
        getDao().readAllMessage(sessionId)
    }

    fun readMessage(messageList: List<AdHocMessageDetail>) {
        messageList.forEach { it.isRead = true }
        getDao().updateMessage(messageList.map { transform(it) })
    }

    /**
     * update to database
     */
    fun updateMessage(message: AdHocMessageDetail): AdHocMessageDetail {
        var returnMessage: AdHocMessageDetail = message
        val entity = transform(message)
        val indexId = getDao().insertMessage(entity)
        ALog.i(TAG, "updateMessage message: ${message.indexId} sessionId: ${message.sessionId} result: $indexId")
        if (indexId > 0 && message.indexId == 0L) {
            entity.id = indexId
            returnMessage = transform(entity)
            returnMessage.isAtMe = message.isAtMe
            returnMessage.atList = message.atList
            returnMessage.isAttachmentDownloading = message.isAttachmentDownloading
            returnMessage.attachmentDigest = message.attachmentDigest
            AdHocSessionLogic.updateLastMessage(message.sessionId, returnMessage.getMessageBody()?.content?.getDescribe(0L, AMELogin.majorContext).toString(), returnMessage.getLastSessionState())

        } else {
            val maxIndex = getDao().queryMaxIndexId(message.sessionId)
            ALog.i(TAG, "updateMessage maxIndex: $maxIndex")
            if (maxIndex == indexId) {
                AdHocSessionLogic.updateLastMessage(message.sessionId, returnMessage.getMessageBody()?.content?.getDescribe(0L, AMELogin.majorContext).toString(), returnMessage.getLastSessionState())

            }
        }
        return returnMessage
    }

    /**
     * fetch message list after indexId
     */
    fun fetchMessageList(sessionId: String, indexId: Long, limit: Int): List<AdHocMessageDetail> {
        val newIndex = if (indexId == -1L) {
            getDao().queryMaxIndexId(sessionId) + 1
        } else {
            indexId
        }
        val list = getDao().loadMessageBefore(sessionId, newIndex, limit)
        return list.map { transform(it) }
    }


    fun deleteMessage(sessionId: String, message: List<AdHocMessageDetail>) {
        getDao().deleteMessage(message.map { transform(it) })
        getDao().findLastMessage(sessionId)?.let {
            transform(it).let {
                AdHocSessionLogic.updateLastMessage(sessionId, it.getMessageBody()?.content?.getDescribe(0L, AMELogin.majorContext).toString(), it.getLastSessionState())
            }
        }
    }

    fun deleteAllMessage(sessionId: String) {
        getDao().deleteAllMessage(sessionId)
    }

    private fun transform(DBEntity: AdHocMessageDBEntity): AdHocMessageDetail {
        val msg = AdHocMessageDetail(DBEntity.id, DBEntity.sessionId, DBEntity.fromId)
        msg.mid = DBEntity.messageId
        msg.nickname = DBEntity.nickname
        msg.success = DBEntity.state == AdHocMessageDBEntity.STATE_SUCCESS
        msg.isSending = DBEntity.state == AdHocMessageDBEntity.STATE_SENDING
        msg.isRead = DBEntity.read == AdHocMessageDBEntity.STATE_READ
        msg.sendByMe = DBEntity.sentByMe == AdHocMessageDBEntity.ACTION_SEND
        msg.time = DBEntity.time
        msg.extContent = DBEntity.extContent
        msg.setMessageBodyJson(DBEntity.text)
        msg.thumbnailUri = DBEntity.thumbnailUri
        msg.attachmentUri = DBEntity.attachmentUri
        msg.attachmentState = DBEntity.attachmentState == AdHocMessageDBEntity.STATE_SUCCESS
        return msg
    }

    private fun transform(message: AdHocMessageDetail): AdHocMessageDBEntity {
        return AdHocMessageDBEntity().apply {
            id = message.indexId
            sessionId = message.sessionId
            messageId = message.mid
            fromId = message.fromId
            nickname = message.nickname
            sentByMe = if (message.sendByMe) AdHocMessageDBEntity.ACTION_SEND else AdHocMessageDBEntity.ACTION_RECEIVE
            state = when {
                message.isSending -> AdHocMessageDBEntity.STATE_SENDING
                message.success -> AdHocMessageDBEntity.STATE_SUCCESS
                else -> AdHocMessageDBEntity.STATE_FAILURE
            }
            time = message.time
            extContent = message.extContent
            read = if (message.isRead) AdHocMessageDBEntity.STATE_READ else AdHocMessageDBEntity.STATE_UNREAD
            text = message.getMessageBodyJson()
            thumbnailUri = message.thumbnailUri
            attachmentUri = message.attachmentUri
            attachmentState = if (message.attachmentState) AdHocMessageDBEntity.STATE_SUCCESS else AdHocMessageDBEntity.STATE_FAILURE
        }
    }
}