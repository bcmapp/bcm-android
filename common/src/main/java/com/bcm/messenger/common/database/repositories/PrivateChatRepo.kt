package com.bcm.messenger.common.database.repositories

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.attachments.Attachment
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.database.MessagingDatabase
import com.bcm.messenger.common.database.db.UserDatabase
import com.bcm.messenger.common.database.model.PrivateChatDbModel
import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.mms.IncomingMediaMessage
import com.bcm.messenger.common.mms.OutgoingMediaMessage
import com.bcm.messenger.common.provider.bean.ConversationStorage
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.sms.IncomingLocationMessage
import com.bcm.messenger.common.sms.IncomingTextMessage
import com.bcm.messenger.common.sms.OutgoingTextMessage
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.RxBus
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import java.util.*

/**
 * Created by Kin on 2019/9/16
 */
class PrivateChatRepo(private val accountContext: AccountContext) {
    companion object {
        const val CHANGED_TAG = "MessageChanged"
    }

    private val TAG = "PrivateChatRepo"

    private val chatDao = UserDatabase.getDatabase(accountContext).getPrivateChatDao()

    private fun notifyMessageChanged(event: PrivateChatEvent) {
        RxBus.post(event)
    }

    /*
     * Util functions start
     */

    private fun generatePduCompatTimestamp(): Long {
        val time = System.currentTimeMillis()
        return time - time % 1000
    }

    private fun getMessageType(attachments: List<Attachment>): PrivateChatDbModel.MessageType {
        if (attachments.isEmpty()) {
            return PrivateChatDbModel.MessageType.DOCUMENT
        }
        val contentType = attachments[0].contentType
        return when {
            contentType.startsWith("image/") || contentType.startsWith("video/") -> PrivateChatDbModel.MessageType.MEDIA
            else -> PrivateChatDbModel.MessageType.DOCUMENT
        }
    }

    /*
     * Util functions end
     */


    /*
     * DB functions start
     */
    fun queryMessagesByPage(threadId: Long, lastId: Long, pageSize: Int): List<MessageRecord> {
        return chatDao.queryChatMessagesByPage(threadId, lastId, pageSize)
    }

    fun getMessage(messageId: Long) = chatDao.queryChatMessage(messageId)

    fun getMessages(ids: List<Long>) = chatDao.queryChatMessages(ids)

    fun getExpirationStartedMessages() = chatDao.queryExpirationStartedMessages()

    fun getThreadIdForMessage(messageId: Long) = chatDao.queryChatMessage(messageId)?.threadId ?: -1

    fun getMediaMessages(threadId: Long) = chatDao.queryMediaMessages(threadId)

    fun getFileMessages(threadId: Long) = chatDao.queryFileMessages(threadId)

    fun getLinkMessages(threadId: Long) = chatDao.queryLinkMessages(threadId)

    fun getAllUnreadMessages() = chatDao.queryUnreadMessages()

    fun getUnreadCount(threadId: Long) = chatDao.queryUnreadMessageCount(threadId)

    fun getLastCannotDecryptMessage(threadId: Long): Long = chatDao.queryLastDecryptFail(threadId)

    fun getBackwardMessages(threadId: Long, lastId: Long, size: Int) = chatDao.queryBackwardTextMessages(threadId, lastId, size)

    fun getForwardMessages(threadId: Long, lastId: Long, size: Int) = chatDao.queryForwardTextMessages(threadId, lastId, size)

    fun insertOutgoingTextMessage(threadId: Long, message: OutgoingTextMessage,
                                  sendTime: Long, insertCallback: ((messageId: Long) -> Unit)?): Long {
        val model = MessageRecord()
        val threadRepo = Repository.getThreadRepo(accountContext)

        val realThreadId = if (threadId <= 0) {
            threadRepo.getThreadIdFor(message.recipient)
        } else {
            threadId
        }

        var status = BASE_SENDING_TYPE
        when {
            message.isKeyExchange -> status = status or KEY_EXCHANGE_BIT
            message.isSecureMessage -> {
                status = status or (SECURE_MESSAGE_BIT or PUSH_MESSAGE_BIT)
                if (message.isLocation) {
                    status = status or KEY_LOCATION_BIT
                }
            }
            message.isEndSession -> status = status or END_SESSION_BIT
        }
        when {
            message.isIdentityVerified -> status = status or KEY_EXCHANGE_IDENTITY_VERIFIED_BIT
            message.isIdentityDefault -> status = status or KEY_EXCHANGE_IDENTITY_DEFAULT_BIT
        }

        model.uid = message.recipient.address.serialize()
        model.threadId = realThreadId
        model.body = message.messageBody.orEmpty()
        model.dateReceive = System.currentTimeMillis()
        model.dateSent = sendTime
        model.read = 1
        model.type = status
        model.expiresTime = message.expiresIn
        model.readRecipientCount = 0
        model.messageType = if (message.isLocation) PrivateChatDbModel.MessageType.LOCATION.type else PrivateChatDbModel.MessageType.TEXT.type
        model.payloadType = message.payloadType

        val messageId = chatDao.insertChatMessage(model)
        model.id = messageId
        insertCallback?.invoke(messageId)

        notifyMessageChanged(PrivateChatEvent(realThreadId, PrivateChatEvent.EventType.INSERT, listOf(model)))

        threadRepo.updateThread(realThreadId, model)
        threadRepo.setLastSeenTime(realThreadId)
        threadRepo.setHasSent(realThreadId, true)

        return messageId
    }

    fun insertOutgoingMediaMessage(threadId: Long, masterSecret: MasterSecret,
                                   message: OutgoingMediaMessage, insertCallback: ((messageId: Long) -> Unit)?): Long {
        val model = MessageRecord()
        val threadRepo = Repository.getThreadRepo(accountContext)

        var status = BASE_SENDING_TYPE or ENCRYPTION_SYMMETRIC_BIT
        if (message.isSecure) status = status or (SECURE_MESSAGE_BIT or PUSH_MESSAGE_BIT)
        if (message.isExpirationUpdate) status = status or EXPIRATION_TIMER_UPDATE_BIT
        if (message.isLocation) status = status or KEY_LOCATION_BIT

        model.dateSent = message.sentTimeMillis
        model.type = status
        model.threadId = threadId
        model.read = 1
        model.dateReceive = System.currentTimeMillis()
        model.expiresTime = message.expiresIn
        model.uid = message.recipient.address.serialize()
        model.readRecipientCount = 0
        model.attachmentCount = message.attachments.size
        model.messageType = getMessageType(message.attachments).type

        val messageId = chatDao.insertChatMessage(model)
        model.id = messageId

        insertCallback?.invoke(messageId)

        model.attachments = Repository.getAttachmentRepo(accountContext).insertAttachments(message.attachments, masterSecret, messageId)

        notifyMessageChanged(PrivateChatEvent(threadId, PrivateChatEvent.EventType.INSERT, listOf(model)))

        threadRepo.updateThread(threadId, model)
        threadRepo.setLastSeenTime(threadId)
        threadRepo.setHasSent(threadId, true)

        return messageId
    }

    fun insertIncomingTextMessage(message: IncomingTextMessage): Pair<Long, Long> {
        val model = MessageRecord()
        val threadRepo = Repository.getThreadRepo(accountContext)

        var status = BASE_INBOX_TYPE or ENCRYPTION_SYMMETRIC_BIT
        when {
            message.isJoined -> status = (status and (TOTAL_MASK - BASE_TYPE_MASK)) or JOINED_TYPE
            message.isPreKeyBundle && !message.isLocation -> status = status or (KEY_EXCHANGE_BIT or KEY_EXCHANGE_BUNDLE_BIT)
            message.isSecureMessage -> {
                status = status or SECURE_MESSAGE_BIT
                if (message.isLocation) status = status or KEY_LOCATION_BIT
            }
            message.isEndSession -> status = status or SECURE_MESSAGE_BIT or END_SESSION_BIT
        }
        if (message.isPush) status = status or PUSH_MESSAGE_BIT
        if (message.isIdentityUpdate) status = status or KEY_EXCHANGE_IDENTITY_UPDATE_BIT
        if (message.isContentPreKeyBundle) status = status or KEY_EXCHANGE_CONTENT_FORMAT
        when {
            message.isIdentityVerified -> status = status or KEY_EXCHANGE_IDENTITY_VERIFIED_BIT
            message.isIdentityDefault -> status = status or KEY_EXCHANGE_IDENTITY_DEFAULT_BIT
        }

        val recipient = Recipient.from(AppContextHolder.APP_CONTEXT, message.sender, true)
        var unread = message.isSecureMessage || message.isPreKeyBundle && !message.isIdentityUpdate && !message.isIdentityDefault && !message.isIdentityVerified
        if (message is IncomingLocationMessage) {
            unread = when (message.payloadType.toLong()) {
                // MARK: Override unread flag
                AmeGroupMessage.CONTROL_MESSAGE,
                AmeGroupMessage.SCREEN_SHOT_MESSAGE -> false
                else -> true
            }
        }

        val threadId = threadRepo.getThreadIdFor(recipient)

        model.uid = message.sender.serialize()
        model.addressDevice = message.senderDeviceId
        model.dateReceive = message.receivedTimestamp
        model.dateSent = message.sentTimestampMillis
        model.read = if (unread) 0 else 1
        model.expiresTime = message.expiresIn
        model.body = message.messageBody.orEmpty()
        model.type = status
        model.threadId = threadId
        model.messageType = if (message.isLocation) PrivateChatDbModel.MessageType.LOCATION.type else PrivateChatDbModel.MessageType.TEXT.type
        model.payloadType = message.payloadType

        val messageId = chatDao.insertChatMessage(model)
        model.id = messageId

        notifyMessageChanged(PrivateChatEvent(threadId, PrivateChatEvent.EventType.INSERT, listOf(model)))

        threadRepo.updateThread(threadId, model)
        if (unread) threadRepo.increaseUnreadCount(threadId, 1)

        if (!AppUtil.isReleaseBuild()) {
            ALog.d(TAG, "insertIncomingTextMessage text: $messageId")
        }

        return Pair(threadId, messageId)
    }

    fun insertIncomingMediaMessage(masterSecret: MasterSecret, message: IncomingMediaMessage): Pair<Long, Long> {
        val model = MessageRecord()
        val threadRepo = Repository.getThreadRepo(accountContext)

        var status = BASE_INBOX_TYPE or SECURE_MESSAGE_BIT or ENCRYPTION_SYMMETRIC_BIT
        if (message.isPushMessage) status = status or PUSH_MESSAGE_BIT
        if (message.isExpirationUpdate) status = status or EXPIRATION_TIMER_UPDATE_BIT

        val recipient = Recipient.from(AppContextHolder.APP_CONTEXT, message.from, true)
        val threadId = threadRepo.getThreadIdFor(recipient)

        model.body = message.body.orEmpty()
        model.dateSent = message.sentTimeMillis
        model.uid = message.from.serialize()
        model.type = status
        model.threadId = threadId
        model.dateReceive = generatePduCompatTimestamp()
        model.expiresTime = message.expiresIn
        model.read = if (message.isExpirationUpdate) 1 else 0
        model.attachmentCount = message.attachments.size
        model.messageType = getMessageType(message.attachments).type
        if (model.dateSent == 0L) {
            model.dateSent = model.dateReceive
        }

        val messageId = chatDao.insertChatMessage(model)

        model.attachments = Repository.getAttachmentRepo(accountContext).insertAttachments(message.attachments, masterSecret, messageId)
        model.id = messageId

        notifyMessageChanged(PrivateChatEvent(threadId, PrivateChatEvent.EventType.INSERT, listOf(model)))
        threadRepo.updateThread(threadId, model)

        if (!message.isExpirationUpdate) {
            threadRepo.increaseUnreadCount(threadId, 1)
        }

        if (!AppUtil.isReleaseBuild()) {
            ALog.d(TAG, "insertIncomingMediaMessage text: $messageId")
        }

        return Pair(threadId, messageId)
    }

    private fun insertCallLog(uid: String, type: Long, isUnread: Boolean, duration: Long, callType: PrivateChatDbModel.CallType): Pair<Long, Long> {
        val threadRepo = Repository.getThreadRepo(accountContext)
        val threadId = threadRepo.getThreadIdFor(uid)

        val model = MessageRecord()
        model.uid = uid
        model.addressDevice = 1
        model.dateReceive = System.currentTimeMillis()
        model.dateSent = System.currentTimeMillis()
        model.read = if (isUnread) 0 else 1
        model.type = type
        model.messageType = PrivateChatDbModel.MessageType.CALL.type
        model.callDuration = duration
        model.callType = callType.type
        model.threadId = threadId

        val messageId = chatDao.insertChatMessage(model)
        model.id = messageId

        notifyMessageChanged(PrivateChatEvent(threadId, PrivateChatEvent.EventType.INSERT, listOf(model)))

        threadRepo.updateThread(threadId, model)
        if (isUnread) {
            threadRepo.increaseUnreadCount(threadId, 1)
        }

        return Pair(messageId, threadId)
    }

    fun insertReceivedCall(uid: String, duration: Long, callType: PrivateChatDbModel.CallType): Pair<Long, Long> {
        return insertCallLog(uid, INCOMING_CALL_TYPE, false, duration, callType)
    }

    fun insertOutgoingCall(uid: String, duration: Long, callType: PrivateChatDbModel.CallType): Pair<Long, Long> {
        return insertCallLog(uid, OUTGOING_CALL_TYPE, false, duration, callType)
    }

    fun insertIncomingMissedCall(uid: String): Pair<Long, Long> {
        return insertCallLog(uid, MISSED_INCOMING_CALL_TYPE, true, 0, PrivateChatDbModel.CallType.AUDIO)
    }

    fun insertOutgoingMissedCall(uid: String): Pair<Long, Long> {
        return insertCallLog(uid, MISSED_OUTGOING_CALL_TYPE, true, 0, PrivateChatDbModel.CallType.AUDIO)
    }

    fun setMessagesRead(threadId: Long): List<MessagingDatabase.MarkedMessageInfo> {
        val messages = chatDao.queryUnreadMessages(threadId)
        val result = LinkedList<MessagingDatabase.MarkedMessageInfo>()

        messages.forEach {
            val syncMessageId = MessagingDatabase.SyncMessageId(Address.fromSerialized(it.uid), it.dateSent)
            val expirationInfo = MessagingDatabase.ExpirationInfo(it.id, it.expiresTime, it.expiresStartTime, it.isMediaMessage())
            result.add(MessagingDatabase.MarkedMessageInfo(syncMessageId, expirationInfo))

            chatDao.updateMessageRead(it.id)
        }

        notifyMessageChanged(PrivateChatEvent(threadId, PrivateChatEvent.EventType.UPDATE, messages))

        return result
    }

    fun updateDateSentForResending(messageId: Long, dateSent: Long) {
        chatDao.updateDataSent(messageId, dateSent)
        updateMessage(messageId)
    }

    fun updateBundleMessage(messageId: Long): Long {
        val model = chatDao.queryChatMessage(messageId)
        if (model != null) {
            val type = BASE_INBOX_TYPE or SECURE_MESSAGE_BIT or PUSH_MESSAGE_BIT
            val status = model.type and (TOTAL_MASK - TOTAL_MASK) or type
            chatDao.updateType(messageId, status)
            return model.threadId
        }
        return -1
    }

    fun getDecryptFailedData(threadId: Long, lastShowTime: Long): List<MessageRecord> {
        return chatDao.queryDecryptFailedMessages(threadId, lastShowTime)
    }

    fun incrementReadReceiptCount(uid: String, sendTime: Long) {
        val record = chatDao.queryChatMessageBySendTime(sendTime)
        if (record != null && record.uid == uid) {
            chatDao.updateReadRecipientCount(record.id)
            updateMessage(record.id)
        }
    }

    fun incrementDeliveryReceiptCount(uid: String, sendTime: Long) {
        val record = chatDao.queryChatMessageBySendTime(sendTime)
        if (record != null && record.uid == uid) {
            chatDao.updateDeliveryRecipientCount(record.id)
            updateMessage(record.id)
        }
    }

    /*
     * DB functions end
     */

    /*
     * mark status start
     */
    fun setMessageSendFail(messageId: Long): Boolean {
        return updateStatus(messageId, BASE_TYPE_MASK, BASE_SENT_FAILED_TYPE)
    }

    fun setMessageExpiresStart(messageId: Long) {
        chatDao.updateExpireStart(messageId, System.currentTimeMillis())
        updateMessage(messageId)
    }

    fun setMessagePendingInsecureSmsFallback(messageId: Long) {
        updateStatus(messageId, BASE_TYPE_MASK, BASE_PENDING_INSECURE_SMS_FALLBACK)
    }

    fun setMessageSendSuccess(messageId: Long) {
        updateStatus(messageId, BASE_TYPE_MASK, (BASE_SENT_TYPE or (PUSH_MESSAGE_BIT or SECURE_MESSAGE_BIT)))
    }

    fun setMessageCannotDecrypt(uid: String, sendTime: Long): Boolean {
        val model = chatDao.queryChatMessageBySendTime(sendTime)
        if (model != null && model.uid == uid) {
            val status = model.type and (TOTAL_MASK - BASE_TYPE_MASK) or BASE_FAIL_CANNOT_DECRYPT_TYPE
            chatDao.updateType(model.id, status)
            updateMessage(model.id)
            return true
        }
        return false
    }

    fun setMessageMissedCall(messageId: Long) {
        updateStatus(messageId, TOTAL_MASK, MISSED_CALL_TYPE)
    }

    fun setMessageEndSession(messageId: Long) {
        updateStatus(messageId, KEY_EXCHANGE_MASK, END_SESSION_BIT)
    }

    fun setMessageRead(messageId: Long) {
        chatDao.updateMessageRead(messageId)
        updateMessage(messageId)
    }

    fun setMessageUnread(messageId: Long) {
        chatDao.updateMessageUnread(messageId)
        updateMessage(messageId)
    }

    fun setMessageInvalidVersionKeyExchange(messageId: Long) {
        updateStatus(messageId, 0, KEY_EXCHANGE_INVALID_VERSION_BIT)
    }

    fun setMessageNoSession(messageId: Long) {
        updateStatus(messageId, ENCRYPTION_MASK, ENCRYPTION_REMOTE_NO_SESSION_BIT)
    }

    fun setMessageLegacyVersion(messageId: Long) {
        updateStatus(messageId, ENCRYPTION_MASK, ENCRYPTION_REMOTE_LEGACY_BIT)
    }

    fun setMessagePreKeyBundle(messageId: Long) {
        updateStatus(messageId, KEY_EXCHANGE_MASK, KEY_EXCHANGE_BIT or KEY_EXCHANGE_BUNDLE_BIT)
    }

    fun setTimestampRead(uid: String, sendTime: Long, expireStart: Long): List<Pair<Long, Long>> {
        val records = chatDao.queryChatMessagesBySendTime(sendTime)
        val expiring = mutableListOf<Pair<Long, Long>>()
        records.forEach {
            if (it.uid == uid) {
                it.read = 1
                if (it.expiresTime > 0L) {
                    it.expiresStartTime = expireStart
                    expiring.add(Pair(it.id, it.expiresTime))
                }
            }
        }
        updateMessages(records)
        return expiring
    }

    fun deleteIncomingMessageByDateSent(threadId: Long, dateSent: Long): Boolean {
        val records = chatDao.queryChatMessagesBySendTime(threadId, dateSent)
        val willDeleteList = mutableListOf<Long>()
        records.forEach {
            if (!it.isOutgoing()) {
                willDeleteList.add(it.id)
            }
        }
        deleteMessages(threadId, willDeleteList)
        return true
    }

    fun getConversationStorageSize(threadId: Long): ConversationStorage {
        var videoSize = 0L
        var imageSize = 0L
        var fileSize = 0L

        val records = getMediaMessages(threadId)
        records.forEach { record ->
            record.attachments.forEach { attachment ->
                if (attachment.dataUri != null) {
                    when {
                        attachment.isImage() -> imageSize += attachment.dataSize
                        attachment.isVideo() -> videoSize += attachment.dataSize
                        attachment.isDocument() -> fileSize += attachment.dataSize
                    }
                }
            }
        }

        return ConversationStorage(videoSize, imageSize, fileSize)
    }

    fun deleteConversationMediaMessages(threadId: Long, type: Int) {
        val records = chatDao.queryMediaMessages(threadId)
        val deleteIds = mutableListOf<Long>()
        records.forEach { record ->
            record.attachments.forEach { attachment ->
                if ((attachment.isDocument() && ConversationStorage.testFlag(type, ConversationStorage.TYPE_FILE)) ||
                        (attachment.isImage() && ConversationStorage.testFlag(type, ConversationStorage.TYPE_IMAGE)) ||
                        (attachment.isVideo() && ConversationStorage.testFlag(type, ConversationStorage.TYPE_VIDEO))) {
                    deleteIds.add(record.id)
                }
            }
        }
        deleteMessages(threadId, deleteIds)
    }

    private fun updateStatus(messageId: Long, maskOff: Long, maskOn: Long): Boolean {
        val model = chatDao.queryChatMessage(messageId)
        if (model != null) {
            val status = model.type and (TOTAL_MASK - maskOff) or maskOn
            chatDao.updateType(messageId, status)
            updateMessage(messageId)
            return true
        }
        return false
    }

    /*
     * mark status end
     */

    private fun updateMessage(id: Long) {
        val record = chatDao.queryChatMessage(id)
        if (record != null) {
            notifyMessageChanged(PrivateChatEvent(record.threadId, PrivateChatEvent.EventType.UPDATE, listOf(record)))
            Repository.getThreadRepo(accountContext).updateThread(record.threadId, record)
        }
    }

    private fun updateMessages(records: List<MessageRecord>) {
        chatDao.updateChatMessages(records)
        records.forEach {
            notifyMessageChanged(PrivateChatEvent(it.threadId, PrivateChatEvent.EventType.UPDATE, listOf(it)))
            Repository.getThreadRepo(accountContext).updateThread(it.threadId, it)
        }
    }

    fun deleteMessage(messageId: Long) {
        val record = chatDao.queryChatMessage(messageId)
        if (record != null) {
            deleteMessage(record)
        }
    }

    fun deleteMessage(record: MessageRecord) {
        chatDao.deleteChatMessage(record)
        notifyMessageChanged(PrivateChatEvent(record.threadId, PrivateChatEvent.EventType.DELETE, emptyList(), listOf(record.id)))
        Repository.getThreadRepo(accountContext).updateThread(record.threadId)
    }

    fun deleteMessage(threadId: Long, messageId: Long) {
        chatDao.deleteChatMessage(messageId)
        notifyMessageChanged(PrivateChatEvent(threadId, PrivateChatEvent.EventType.DELETE, emptyList(), listOf(messageId)))
        Repository.getThreadRepo(accountContext).updateThread(threadId)
    }

    fun deleteMessages(threadId: Long, ids: List<Long>) {
        chatDao.deleteChatMessages(ids)
        notifyMessageChanged(PrivateChatEvent(threadId, PrivateChatEvent.EventType.DELETE, emptyList(), ids))
        Repository.getThreadRepo(accountContext).updateThread(threadId)
    }

    fun cleanChatMessages(threadId: Long) {
        chatDao.deleteChatMessages(threadId)
        notifyMessageChanged(PrivateChatEvent(threadId, PrivateChatEvent.EventType.DELETE_ALL, emptyList(), emptyList()))
        Repository.getThreadRepo(accountContext).updateThread(threadId)
    }

    fun cleanThreadExcept(threadId: Long, ids: List<Long>) {
        chatDao.deleteChatMessages(threadId, ids)
        notifyMessageChanged(PrivateChatEvent(threadId, PrivateChatEvent.EventType.DELETE_EXCEPT, emptyList(), ids))
        Repository.getThreadRepo(accountContext).updateThread(threadId)
    }

    fun attachmentUpdate(messageId: Long) {
        val record = chatDao.queryChatMessage(messageId)
        if (record != null) {
            notifyMessageChanged(PrivateChatEvent(record.threadId, PrivateChatEvent.EventType.UPDATE, listOf(record)))
            Repository.getThreadRepo(accountContext).updateThread(record.threadId, record)
        }
    }
}