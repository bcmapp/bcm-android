package com.bcm.messenger.common.database.repositories

import android.net.Uri
import androidx.lifecycle.LiveData
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.database.MessagingDatabase
import com.bcm.messenger.common.database.db.UserDatabase
import com.bcm.messenger.common.database.model.ThreadDbModel
import com.bcm.messenger.common.database.records.AttachmentRecord
import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.database.records.ThreadRecord
import com.bcm.messenger.common.grouprepository.manager.GroupLiveInfoManager
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.grouprepository.modeltransform.GroupMessageTransform
import com.bcm.messenger.common.grouprepository.room.entity.GroupLiveInfo
import com.bcm.messenger.common.mms.PartAuthority
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.utils.ConversationUtils
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog

/**
 * Created by Kin on 2019/9/17
 */
class ThreadRepo {
    private val TAG = "ThreadRepo"

    object DistributionTypes {
        const val DEFAULT = 2
        const val BROADCAST = 1
        const val CONVERSATION = 2
        const val ARCHIVE = 3
        const val INBOX_ZERO = 4
        const val NEW_GROUP = 5
    }

    private val threadDao = UserDatabase.getDatabase().getThreadDao()
    private val chatDao = UserDatabase.getDatabase().getPrivateChatDao()

    private fun getAttachment(record: MessageRecord): AttachmentRecord? {
        if (!record.isMediaMessage()) {
            return null
        }
        var attachment = record.getImageAttachment()
        if (attachment == null) {
            attachment = record.getVideoAttachment()
        }
        if (attachment == null) {
            attachment = record.getDocumentAttachment()
        }
        if (attachment == null) {
            attachment = record.getAudioAttachment()
        }
        return attachment
    }

    fun updateSnippet(threadId: Long, body: String, dataUri: Uri?, date: Long, status: Long) {
        val model = threadDao.queryThread(threadId)
        if (model != null) {
            threadDao.updateSnippet(threadId, body, dataUri?.toString(), date - date % 1000, status)
        }
    }

    fun deleteThread(threadId: Long) {
        threadDao.deleteThread(threadId)
        Repository.getDraftRepo().clearDrafts(threadId)
    }

    fun setRead(threadId: Long, lastSeen: Boolean): List<MessagingDatabase.MarkedMessageInfo> {
        val model = threadDao.queryThread(threadId)
        if (model != null) {
            if (lastSeen) {
                threadDao.updateReadAndLastSeen(threadId, System.currentTimeMillis())
            } else {
                threadDao.updateRead(threadId)
            }
            return Repository.getChatRepo().setMessagesRead(threadId)
        }
        return emptyList()
    }

    fun setReadList(threads: List<ThreadDbModel>, lastSeen: Boolean) {
        val time = System.currentTimeMillis()
        threads.forEach {
            if (lastSeen) {
                threadDao.updateReadAndLastSeen(it.id, time)
            } else {
                threadDao.updateRead(it.id)
            }
            Repository.getChatRepo().setMessagesRead(it.id)
        }
    }

    fun setGroupRead(threadId: Long, gid: Long, lastSeenTime: Long) {
        MessageDataManager.setGroupMessageRead(gid)
        threadDao.updateReadAndLastSeen(threadId, lastSeenTime)
    }

    fun increaseUnreadCount(threadId: Long, amount: Int) {
        threadDao.updateUnreadCount(threadId, amount)
    }

    fun getDistributionType(threadId: Long): Int {
        return threadDao.queryThread(threadId)?.distributionType ?: DistributionTypes.DEFAULT
    }

    fun getPinTime(threadId: Long): Long {
        return threadDao.queryThread(threadId)?.pinTime ?: 0L
    }

    fun getAllThreadsLiveData(): LiveData<List<ThreadRecord>> {
        return threadDao.queryAllThreadsWithLiveData()
    }

    fun getAllThreads() = threadDao.queryAllThreads()

    fun getThreadIdFor(uid: String): Long {
        return getThreadIdFor(Recipient.from(AppContextHolder.APP_CONTEXT, Address.fromSerialized(uid), true))
    }

    @Synchronized
    fun getThreadIdFor(recipient: Recipient): Long {
        var threadId = threadDao.queryThreadId(recipient.address.serialize())
        if (threadId <= 0L) {
            val model = ThreadDbModel()

            val time = System.currentTimeMillis()
            model.timestamp = time - time % 1000
            model.uid = recipient.address.serialize()
            model.lastSeenTime = 0L
            model.distributionType = if (recipient.isGroupRecipient) DistributionTypes.NEW_GROUP else DistributionTypes.DEFAULT
            model.pinTime = 0L
            model.messageCount = 0

            threadId = threadDao.insertThread(model)
        }
        return threadId
    }

    fun getThreadIdIfExist(recipient: Recipient): Long {
        return threadDao.queryThreadId(recipient.address.serialize())
    }

    fun getThreadIdIfExist(uid: String): Long {
        if (uid.isEmpty()) return 0L
        return threadDao.queryThreadId(uid)
    }

    fun setPinTime(threadId: Long) {
        threadDao.updatePinTime(threadId, System.currentTimeMillis())

    }

    fun removePinTime(threadId: Long) {
        threadDao.updatePinTime(threadId, 0L)
    }

    fun setLastSeenTime(threadId: Long) {
        threadDao.updateLastSeenTime(threadId, System.currentTimeMillis())
    }

    fun setDecryptFailData(threadId: Long, dataJson: String) {
        threadDao.updateDecryptFailData(threadId, dataJson)
    }

    fun setDecryptFailData(uid: String, dataJson: String) {
        val model = threadDao.queryThread(uid)
        if (model != null) {
            threadDao.updateDecryptFailData(model.id, dataJson)
        }
    }

    fun getDecryptFailData(threadId: Long): String? {
        return threadDao.queryThread(threadId)?.decryptFailData
    }

    fun getDecryptFailData(uid: String): String? {
        return threadDao.queryThread(uid)?.decryptFailData
    }

    fun deleteConversationContent(threadId: Long) {
        Repository.getChatRepo().cleanChatMessages(threadId)
        Repository.getDraftRepo().clearDrafts(threadId)
        deleteThread(threadId)
    }

    fun clearConversationExcept(threadId: Long, messageIdList: List<Long>) {
        Repository.getChatRepo().cleanThreadExcept(threadId, messageIdList)
        val unreadCount = chatDao.queryMessageCount(threadId).toInt()
        threadDao.updateReadState(threadId, if (unreadCount <= 0) 1 else 0, unreadCount)
    }

    fun cleanConversationContentForGroup(threadId: Long, gid: Long) {
        Repository.getDraftRepo().clearDrafts(threadId)
        GroupLiveInfoManager.getInstance().deleteLiveInfoWhenLeaveGroup(gid)
        MessageDataManager.deleteMessagesByGid(gid)
        deleteThread(threadId)
    }

    fun deleteConversationForGroup(gid: Long, threadId: Long) {
        Repository.getDraftRepo().clearDrafts(threadId)
        GroupLiveInfoManager.getInstance().deleteLiveInfoWhenLeaveGroup(gid)
        MessageDataManager.deleteMessagesByGid(gid)
        updateByNewGroup(gid)
    }

    fun updateThread(threadId: Long, record: MessageRecord?, time: Long? = null) {
        UserDatabase.getDatabase().runInTransaction {
            val threadModel = threadDao.queryThread(threadId)
            if (threadModel != null) {
                val drafts = Repository.getDraftRepo().getDrafts(threadId)
                val count = chatDao.queryMessageCount(threadId)
                val date = time ?: record?.dateReceive ?: threadModel.timestamp

                if (drafts.isEmpty()) {
                    ALog.i(TAG, "replace snippet")
                    threadModel.snippetContent = record?.body.orEmpty()
                    threadModel.snippetType = record?.type ?: 0

                    val attachment = if (record == null) null else getAttachment(record)
                    if (attachment != null) {
                        if (attachment.dataUri != null) {
                            threadModel.snippetUri = attachment.dataUri
                        } else {
                            threadModel.snippetUri = PartAuthority.getAttachmentDataUri(attachment.id, attachment.uniqueId)
                        }
                        threadModel.snippetContent = attachment.contentType
                    } else {
                        threadModel.snippetUri = null
                    }

                }else {
                    threadModel.snippetType = BASE_DRAFT_TYPE
                    threadModel.snippetContent = drafts.getSnippet(AppContextHolder.APP_CONTEXT) ?: ""
                    threadModel.snippetUri = drafts.getUriSnippet(AppContextHolder.APP_CONTEXT)
                }

                threadModel.timestamp = date - date % 1000
                threadModel.expiresTime = record?.expiresTime ?: 0L
                threadModel.messageCount = count

                threadDao.updateThread(threadModel)

            }
        }
    }

    fun updateThread(threadId: Long, time: Long? = null) {
        val record = chatDao.queryLatestMessage(threadId)
        updateThread(threadId, record, time)
    }

    fun updateByNewGroup(gid: Long, time: Long? = null) {
        val message = GroupMessageTransform.transformToModel(MessageDataManager.fetchLastMessage(gid))
        var threadId = getThreadIdIfExist(Recipient.recipientFromNewGroupId(AppContextHolder.APP_CONTEXT, gid))
        if (threadId <= 0L && (null == message || null == message.message)) {
            //即不存在会话，也不存在消息，就不update了
            ALog.i(TAG, "updateByNewGroup gid: $gid, no thread, no message, return")
            return
        } else if (threadId <= 0L) {
            threadId = getThreadIdFor(Recipient.recipientFromNewGroupId(AppContextHolder.APP_CONTEXT, gid))
        }

        val drafts = Repository.getDraftRepo().getDrafts(threadId)
        val draftText = drafts.getSnippet(AppContextHolder.APP_CONTEXT)
        val draftUri = drafts.getUriSnippet(AppContextHolder.APP_CONTEXT)?.toString()

        if (null == message || null == message.message) {
            updateByNewGroup(threadId, 0, 0, "", "", draftText, draftUri,
                    if (drafts.isNotEmpty()) BASE_DRAFT_TYPE else AmeGroupMessageDetail.SendState.SEND_SUCCESS.value, time ?: AmeTimeUtil.serverTimeMillis())
        } else {
            val count = MessageDataManager.countMessageByGid(gid)
            val unreadCount = MessageDataManager.countUnreadCountFromLastSeen(gid, 0)

            val body = message.message.toString()
            val uriString = if (message.attachmentUri == null) "" else message.attachmentUri

            val messageContent = message.message.content

            // 逻辑是入群请求优先级大于@me大于草稿，优先设置@，如果有新消息过来，内容替换但是标识还是@，如果当前状态是草稿，来新消息的时候，除了@me的消息，其余情况标识和内容不变，都是草稿
            val state = if (messageContent is AmeGroupMessage.SystemContent && messageContent.tipType == AmeGroupMessage.SystemContent.TIP_JOIN_GROUP_REQUEST) {
                BASE_JOIN_REQUEST
            }
            else if (!message.isSendByMe && ConversationUtils.checkHasAtMe(message)) {
                BASE_AT_ME_TYPE
            }
            else if (drafts.isNotEmpty()) {
                BASE_DRAFT_TYPE
            }
            else {
                message.sendState.value
            }
            updateByNewGroup(threadId, count, unreadCount, body, uriString, draftText, draftUri, state, time ?: message.sendTime)

        }
    }

    private fun updateByNewGroup(threadId: Long, count: Long, unreadCount: Long, body: String, attachmentUri: String?, draftText: String?, draftUri: String?, state: Long, date: Long) {
        UserDatabase.getDatabase().runInTransaction {
            val record = threadDao.queryThread(threadId)
            if (record != null) {

                record.snippetContent = body
                record.snippetUri = if (attachmentUri == null) null else Uri.parse(attachmentUri)
                // 逻辑是入群请求优先级大于@me大于草稿，优先设置@，如果有新消息过来，内容替换但是标识还是@，如果当前状态是草稿，来新消息的时候，除了@me的消息，其余情况标识和内容不变，都是草稿
                if (state == BASE_JOIN_REQUEST && unreadCount > 0) {
                    record.snippetType = BASE_JOIN_REQUEST

                }else if (state == BASE_AT_ME_TYPE && !record.isJoinRequestMessage() && unreadCount > 0) {
                    record.snippetType = BASE_AT_ME_TYPE

                }
                else if ((record.isJoinRequestMessage() || record.isAtMeMessage()) && unreadCount > 0) {

                }
                else if (state == BASE_DRAFT_TYPE || !draftText.isNullOrEmpty() || !draftUri.isNullOrEmpty()) {
                    record.snippetType = BASE_DRAFT_TYPE
                    record.snippetContent = draftText ?: ""
                    record.snippetUri = if (draftUri == null) null else Uri.parse(draftUri)
                }else {
                    record.snippetType = AmeGroupMessageDetail.SendState.SEND_SUCCESS.value
                }

                record.messageCount = count
                record.unreadCount = unreadCount.toInt()
                record.timestamp = date - date % 1000

                threadDao.updateThread(record)
            }
        }

    }

    fun setHasSent(threadId: Long, hasSent: Boolean) {
        threadDao.updateHasSent(threadId, if (hasSent) 1 else 0)
    }

    fun getAllThreadsWithRecipientReady(): List<ThreadRecord> {
        val threadList = threadDao.queryAllThreads()
        val map = mutableMapOf<String, ThreadRecord>()
        val uidList = mutableListOf<String>()
        threadList.forEach {r ->
            uidList.add(r.uid)
            map[r.uid] = r
        }
        Repository.getRecipientRepo()?.getRecipients(uidList)?.forEach {settings ->
            map[settings.uid]?.setRecipient(Recipient.fromSnapshot(AppContextHolder.APP_CONTEXT, Address.fromSerialized(settings.uid), settings))
        }
        return threadList
    }

    fun hasProfileRequest(threadId: Long): Boolean {
        return threadDao.queryThread(threadId)?.profileRequest == 1
    }

    fun setProfileRequest(threadId: Long, hasRequest: Boolean) {
        threadDao.updateProfileRequest(threadId, if (hasRequest) 1 else 0)
    }

    fun updateLiveState(gid: Long, status: Int) {
        val threadId = if (status == GroupLiveInfo.LiveStatus.REMOVED.value || status == GroupLiveInfo.LiveStatus.STOPED.value || status == GroupLiveInfo.LiveStatus.EMPTY.value) {
            getThreadIdIfExist(Recipient.recipientFromNewGroupId(AppContextHolder.APP_CONTEXT, gid))
        } else {
            getThreadIdFor(Recipient.recipientFromNewGroupId(AppContextHolder.APP_CONTEXT, gid))
        }
        if (threadId <= 0) return
        threadDao.updateLiveStatus(threadId, status)

    }

    fun insertThread(record: ThreadDbModel) = threadDao.insertThread(record)
}