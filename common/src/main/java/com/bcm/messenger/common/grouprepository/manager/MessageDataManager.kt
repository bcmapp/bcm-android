package com.bcm.messenger.common.grouprepository.manager

import android.net.Uri
import android.text.TextUtils
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.crypto.CtrStreamUtil
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.crypto.encrypt.FileInfo
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.events.GroupMessageMissedEvent
import com.bcm.messenger.common.grouprepository.events.MessageEvent
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager.InsertMessageResult.Companion.INSERT_SUCCESS
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager.InsertMessageResult.Companion.REPLAY_MESSAGE
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager.InsertMessageResult.Companion.UPDATE_SUCCESS
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.grouprepository.modeltransform.GroupMessageTransform
import com.bcm.messenger.common.grouprepository.room.dao.GroupMessageDao
import com.bcm.messenger.common.grouprepository.room.entity.GroupMessage
import com.bcm.messenger.common.provider.bean.ConversationStorage
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.GroupUtil
import com.bcm.messenger.common.utils.split
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * 
 */
object MessageDataManager {
    private const val TAG = "MessageDataManager"

    fun setGroupMessageRead(accountContext: AccountContext, gid: Long) {
        getDao(accountContext)?.setMessageRead(gid)
    }

    fun updateMessageAttachmentUri(accountContext: AccountContext, gid: Long, indexId: Long, fileInfo: FileInfo) {
        if (gid <= 0) {
            //notice ，gid，
            return
        }
        val message = getDao(accountContext)?.queryOneMessageByIndex(gid, indexId) ?: return
        message.attachment_uri = Uri.fromFile(fileInfo.file).toString()
        message.dataHash = fileInfo.hash
        message.dataRandom = fileInfo.random
        message.attachmentSize = fileInfo.size
        message.isFileEncrypted = fileInfo.random != null
        getDao(accountContext)?.updateMessage(message)

        EventBus.getDefault().post(MessageEvent(accountContext, message.gid, MessageEvent.EventType.ATTACHMENT_DOWNLOAD_SUCCESS,
                listOf(GroupMessageTransform.transformToModel(message)
                        ?: return)))
    }

    fun updateMessageThumbnailUri(accountContext: AccountContext, gid: Long, indexId: Long, fileInfo: FileInfo) {
        if (gid <= 0) return

        val message = getDao(accountContext)?.queryOneMessageByIndex(gid, indexId) ?: return
        message.thumbnailUri = Uri.fromFile(fileInfo.file).toString()
        message.thumbHash = fileInfo.hash
        message.thumbRandom = fileInfo.random
        message.isFileEncrypted = fileInfo.random != null
        getDao(accountContext)?.updateMessage(message)

        EventBus.getDefault().post(MessageEvent(accountContext, message.gid, MessageEvent.EventType.THUMBNAIL_DOWNLOAD_SUCCESS,
                listOf(GroupMessageTransform.transformToModel(message))))
    }

    fun updateMessageContent(accountContext: AccountContext, gid: Long, indexId: Long, content: String) {
        val message = getDao(accountContext)?.queryOneMessageByIndex(gid, indexId) ?: return
        message.text = content
        getDao(accountContext)?.updateMessage(message)
    }


    fun deleteOneMessageByIndexId(accountContext: AccountContext, gid: Long, indexId: Long) {
        val message = getDao(accountContext)?.queryOneMessageByIndex(gid, indexId) ?: return
        message.is_confirm = GroupMessage.DELETED_MESSAGE
        message.text = ""

        if (!TextUtils.isEmpty(message.attachment_uri)) {
            BcmFileUtils.delete(accountContext, message.attachment_uri)
        }

        getDao(accountContext)?.updateMessage(message)
        notifyThreadUpdate(accountContext, message.gid)
        EventBus.getDefault().post(MessageEvent(accountContext, message.gid, indexId, MessageEvent.EventType.DELETE_ONE_MESSAGE))
    }


    /*
    * 
    * */
    fun deleteMessagesByGid(accountContext: AccountContext, gid: Long) {
        val list = getDao(accountContext)?.loadGroupMessageByGid(gid)
        if (list != null && list.isNotEmpty()) {
            for (m in list) {
                m.is_confirm = GroupMessage.DELETED_MESSAGE
                if (!TextUtils.isEmpty(m.attachment_uri)) {
                    BcmFileUtils.delete(accountContext, m.attachment_uri)
                }
            }
            getDao(accountContext)?.updateMessages(list)

            if ((Repository.getThreadRepo(accountContext)?.getThreadIdIfExist(Recipient.recipientFromNewGroupId(accountContext, gid)) ?: 0) > 0) {
                notifyThreadUpdate(accountContext, gid)
            }
        }
    }

    /**
     * 
     *
     * @param messageList 
     */
    fun deleteMessages(accountContext: AccountContext, gid: Long, messageList: List<GroupMessage>) {
        for (msg in messageList) {
            msg.is_confirm = GroupMessage.DELETED_MESSAGE
            if (!TextUtils.isEmpty(msg.attachment_uri)) {
                BcmFileUtils.delete(accountContext, msg.attachment_uri)
            }
        }
        getDao(accountContext)?.updateMessages(messageList)
        notifyThreadUpdate(accountContext, gid)
    }

    /**
     * 
     * @param gid
     * @param indexId
     * @param send_state   SEND_SUCCESS = 1; SENDING = 2; SEND_FAILURE = 10000;
     * @return -1  , 0:
     */
    fun updateMessageSendStateByIndex(accountContext: AccountContext, gid: Long, indexId: Long, send_state: Int): Int {
        val message = queryOneMessage(accountContext, gid, indexId, false)
        return if (message != null) {
            message.send_state = send_state
            getDao(accountContext)?.updateMessage(message)

            val groupMessageDetail = GroupMessageTransform.transformToModel(message)
            groupMessageDetail?.let {
                EventBus.getDefault().post(MessageEvent(accountContext, message.gid, MessageEvent.EventType.SEND_MESSAGE_UPDATE, listOf(it)))
            }
            0
        } else {
            -1
        }
    }

    /**
     * 
     * @param gid
     * @param indexId
     * @param mode Mode.CHAT_MODE , Mode.SUB_MODE channel
     * @return -1  , 0:
     */
    fun updateMessageEncryptMode(accountContext: AccountContext, gid: Long, indexId: Long, keyVersion:Long): Int {
        val message = queryOneMessage(accountContext, gid, indexId, false)
        if (message != null) {
            message.key_version = keyVersion

            getDao(accountContext)?.updateMessage(message)
            return 0
        } else {
            return -1
        }

    }

    /**
     * 
     */
    fun updateMessageSendResult(accountContext: AccountContext, gid: Long, indexId: Long, mid: Long, createTime: Long, iv: String, text: String, sendState: Int) {
        val message = queryOneMessage(accountContext, gid, indexId, false)
        if (message != null) {
            message.mid = mid
            message.create_time = createTime
            // 
            message.identityIvString = iv
            message.text = text
            message.send_state = sendState
            getDao(accountContext)?.updateMessage(message)

            val groupMessageDetail = GroupMessageTransform.transformToModel(message)
            groupMessageDetail?.let {
                EventBus.getDefault().post(MessageEvent(accountContext, gid, MessageEvent.EventType.SEND_MESSAGE_UPDATE, listOf(it)))
            }
        }

    }


    /**
     *  indexId 
     * 
     * @param gid
     * @param indexId
     */
    fun fetchOneMessageByGidAndIndexId(accountContext: AccountContext, gid: Long, indexId: Long): AmeGroupMessageDetail? {
        return GroupMessageTransform.transformToModel(getDao(accountContext)?.queryOneMessageByIndex(gid, indexId))
    }

    fun fetchOneMessageByGidAndMid(accountContext: AccountContext, gid: Long, mid: Long): AmeGroupMessageDetail? {
        return GroupMessageTransform.transformToModel(getDao(accountContext)?.queryOneMessageByMid(gid, mid))
    }

    fun fetchMessageByGidAndIndexId(accountContext: AccountContext, gid: Long, indexId: Long, count: Int): List<AmeGroupMessageDetail> {
        val dao = getDao(accountContext)?:return listOf()
        if (indexId == -1L) {
            val lastId = dao.queryMaxIndexId(gid)
            val list = dao.loadMessagesByGidAndIndexId(gid, lastId + 1, count)
            return GroupMessageTransform.transformToModelList(list)
        }
        return GroupMessageTransform.transformToModelList(dao.loadMessagesByGidAndIndexId(gid, indexId, count))
    }

    fun fetchTextMessage(accountContext: AccountContext, gid: Long, formIndexId: Long, count: Int, isBackWord: Boolean): List<AmeGroupMessageDetail> {
        val dao = getDao(accountContext)?:return listOf()
        return if (isBackWord) {
            GroupMessageTransform.transformToModelList(dao.loadTextMessagesAfterIndexId(gid, formIndexId, count))
        } else {
            GroupMessageTransform.transformToModelList(dao.loadTextMessagesBeforeIndexId(gid, formIndexId, count))
        }
    }


    /**
     * 
     *
     * @param gid ID
     * @return 
     */
    fun fetchFileMessages(accountContext: AccountContext, gid: Long): List<AmeGroupMessageDetail> {
        val dao = getDao(accountContext)?:return listOf()
        return GroupMessageTransform.transformToModelList(dao.loadAllFileMessages(gid))
    }


    /**
     * 
     *
     * @param gid ID
     * @return 
     */
    fun fetchLinkMessages(accountContext: AccountContext, gid: Long): List<AmeGroupMessageDetail> {
        val dao = getDao(accountContext)?:return listOf()
        return GroupMessageTransform.transformToModelList(dao.loadAllLinkMessages(gid))
    }

    /**
     * 
     *
     * @param gid ID
     * @return 
     */
    fun fetchMediaMessages(accountContext: AccountContext, gid: Long): List<AmeGroupMessageDetail> {
        val dao = getDao(accountContext)?:return listOf()
        return GroupMessageTransform.transformToModelList(dao.loadAllImageOrVideoMessages(gid))
    }


    /**
     * 
     */
    fun fetchMediaMessageStorageSize(accountContext: AccountContext, gid: Long): ConversationStorage {
        val dao = getDao(accountContext)?:return ConversationStorage(0, 0, 0)
        val list = GroupMessageTransform.transformToModelList(dao.loadAllMediaMessages(gid))

        var videoSize = 0L
        var imageSize = 0L
        var fileSize = 0L

        for (m in list) {
            if (!TextUtils.isEmpty(m.attachmentUri)) {
                val content = m.message.content
                when (content) {
                    is AmeGroupMessage.VideoContent -> videoSize += content.size
                    is AmeGroupMessage.ImageContent -> imageSize += content.size
                    is AmeGroupMessage.FileContent -> fileSize += content.size
                }
            }
        }
        return ConversationStorage(videoSize, imageSize, fileSize)
    }

    /**
     * 
     */
    fun deleteAllMediaMessage(accountContext: AccountContext, gid: Long, type: Int) {
        val dao = getDao(accountContext)?:return

        val list = dao.loadAllMediaMessages(gid)

        val deleted = ArrayList<GroupMessage>()
        if (list.isNotEmpty()) {
            for (m in list) {
                if ((m.content_type.toLong() == AmeGroupMessage.FILE && ConversationStorage.testFlag(type, ConversationStorage.TYPE_FILE))
                        || (m.content_type.toLong() == AmeGroupMessage.IMAGE && ConversationStorage.testFlag(type, ConversationStorage.TYPE_IMAGE))
                        || (m.content_type.toLong() == AmeGroupMessage.VIDEO && ConversationStorage.testFlag(type, ConversationStorage.TYPE_VIDEO))) {
                    m.is_confirm = GroupMessage.DELETED_MESSAGE
                    if (!TextUtils.isEmpty(m.attachment_uri)) {
                        BcmFileUtils.delete(accountContext, m.attachment_uri)
                    }
                    deleted.add(m)
                }
            }
            dao.updateMessages(deleted)
            notifyThreadUpdate(accountContext, gid)
        }
    }

    /**
     * fromMid（）toMid，
     */
    fun fetchMessageFromToMid(accountContext: AccountContext, gid: Long, fromMid: Long, toMid: Long): List<AmeGroupMessageDetail> {
        val dao = getDao(accountContext)?:return listOf()
        return GroupMessageTransform.transformToModelList(dao.loadMessageFromTo(gid, fromMid, toMid))
    }

    /**
     * ，
     * @param gid
     * @return 
     */
    fun fetchLastMessage(accountContext: AccountContext, gid: Long): GroupMessage? {
        return getDao(accountContext)?.queryLastMessageByGid(gid)
    }


    fun countUnreadCount(accountContext: AccountContext, gid: Long): Long {
        return getDao(accountContext)?.countUnread(gid)?:0
    }

    fun countUnreadCountFromLastSeen(accountContext: AccountContext, gid: Long, lastSeen:Long): Long {
        return getDao(accountContext)?.countUnreadFromLastSeen(gid, lastSeen)?:0
    }

    fun countMessageByGid(accountContext: AccountContext, gid: Long): Long {
        return getDao(accountContext)?.countMessagesByGid(gid)?:0
    }

    class InsertMessageResult(resultCode: Int, gid: Long, firstMid: Long, lastMid: Long, indexId: Long) {
        companion object {
            const val INSERT_SUCCESS = 0
            const val UPDATE_SUCCESS = 1
            const val REPLAY_MESSAGE = -3

        }

        val resultCode = resultCode
        val gid = gid
        val indexId = indexId
    }

    //
    fun insertSendMessage(accountContext: AccountContext, sendMessage: GroupMessage, visible: Boolean = true): Long {
        if (!visible) {
            sendMessage.is_confirm = GroupMessage.CONFIRM_BUT_NOT_SHOW
        }

        val indexId = getDao(accountContext)?.insertMessage(sendMessage)?:return 0
        sendMessage.id = indexId
        notifyThreadUpdate(accountContext, sendMessage.gid)

        //UI
        if (sendMessage.is_confirm == GroupMessage.CONFIRM_MESSAGE) {
            val messageDetail = GroupMessageTransform.transformToModel(sendMessage)
            messageDetail?.let {
                EventBus.getDefault().post(MessageEvent(accountContext, sendMessage.gid, MessageEvent.EventType.SEND_MESSAGE_INSERT, listOf(it)))
            }
        }
        return indexId
    }

    /**
     * ，
     * @param message
     * @return 0：，-2:, -3: 
     */
    fun insertReceiveMessage(accountContext: AccountContext, message: GroupMessage): InsertMessageResult? {
        checkMessageVisibleState(accountContext, message)
        return insertReceiveMessage(accountContext, message, true)
    }

    private fun checkMessageVisibleState(accountContext: AccountContext, message: GroupMessage) {
        if (message.content_type.toLong() == AmeGroupMessage.LIVE_MESSAGE) {
            message.read_state = GroupMessage.READ_STATE_READ
            val msg = AmeGroupMessage.messageFromJson(message.text)
            val content = msg.content as AmeGroupMessage.LiveContent
            if (content.isPauseLive() || content.isRestartLive())
                message.is_confirm = GroupMessage.CONFIRM_BUT_NOT_SHOW
        } else if (message.content_type.toLong() == AmeGroupMessage.SYSTEM_INFO) {
            message.read_state = GroupMessage.READ_STATE_READ

            //
            val msg = AmeGroupMessage.messageFromJson(message.text)
            val content = msg.content as AmeGroupMessage.SystemContent
            if (content.tipType == AmeGroupMessage.SystemContent.TIP_KICK) {
                val groupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, message.gid)
                //
                if (accountContext.uid != groupInfo?.owner && !content.theOperator.any { accountContext.uid == it }) {
                    message.is_confirm = GroupMessage.CONFIRM_BUT_NOT_SHOW
                }
            } else if(content.tipType == AmeGroupMessage.SystemContent.TIP_SUBSCRIBE
                    || content.tipType == AmeGroupMessage.SystemContent.TIP_UNSUBSCRIBE) {
                //
                message.is_confirm = GroupMessage.CONFIRM_BUT_NOT_SHOW
            }
        } else if (message.content_type.toLong() == AmeGroupMessage.GROUP_SHARE_SETTING_REFRESH) {
            message.read_state = GroupMessage.READ_STATE_READ
            message.is_confirm = GroupMessage.CONFIRM_BUT_NOT_SHOW
        } else if(message.content_type.toLong() == AmeGroupMessage.SHARE_CHANNEL
                || message.content_type.toLong() == AmeGroupMessage.NEWSHARE_CHANNEL) {
            message.read_state = GroupMessage.READ_STATE_READ
            message.is_confirm = GroupMessage.CONFIRM_BUT_NOT_SHOW
        }

        if (message.is_confirm == GroupMessage.CONFIRM_MESSAGE) {
            // ThreadId
            val groupRecipient = Recipient.from(accountContext,
                    GroupUtil.addressFromGid(accountContext, message.gid).serialize(),
                    false)

            Repository.getThreadRepo(accountContext)
                    ?.getThreadIdFor(groupRecipient)

        }
    }

    private fun insertReceiveMessage(accountContext: AccountContext, message: GroupMessage, isNotify: Boolean): InsertMessageResult? {
        val dao = getDao(accountContext)?:return null
        val savedMaxMid = dao.queryMaxMid(message.gid)
        if (message.mid > savedMaxMid) {
            if (message.mid - savedMaxMid == 1L || dao.countMessagesByGid(message.gid) == 0L) {
                val indexId = dao.insertMessage(message)
                message.id = indexId
                if (message.is_confirm != GroupMessage.CONFIRM_BUT_NOT_SHOW && isNotify)
                    notifyThreadUpdate(accountContext, message.gid)
                return InsertMessageResult(INSERT_SUCCESS, message.gid, -1, -1, indexId)
            } else {
                //，
                var i = 1
                do {
                    val unconfirmedMessage = GroupMessage()
                    unconfirmedMessage.is_confirm = GroupMessage.UNCONFIRM_MESSAGE
                    unconfirmedMessage.mid = savedMaxMid + i
                    unconfirmedMessage.gid = message.gid
                    dao.insertMessage(unconfirmedMessage)
                    i++
                } while (i < message.mid - savedMaxMid)
                if (message.is_confirm != GroupMessage.CONFIRM_BUT_NOT_SHOW)
                    message.is_confirm = GroupMessage.CONFIRM_MESSAGE
                val indexId = dao.insertMessage(message)
                message.id = indexId
                if (isNotify) {
                    notifyThreadUpdate(accountContext, message.gid)
                }
                EventBus.getDefault().post(GroupMessageMissedEvent(accountContext, message.gid, savedMaxMid, message.mid-1))
                //,
                return InsertMessageResult(INSERT_SUCCESS, message.gid, savedMaxMid, message.mid, indexId)
            }
        } else {
            val databaseMessage: GroupMessage = dao.queryOneMessageByMid(message.gid, message.mid)
            if (databaseMessage.is_confirm == GroupMessage.UNCONFIRM_MESSAGE || databaseMessage.is_confirm == GroupMessage.FETCHING_MESSAGE) {
                message.id = databaseMessage.id
                message.read_state = databaseMessage.read_state
                if (message.is_confirm != GroupMessage.CONFIRM_BUT_NOT_SHOW) {
                    message.is_confirm = GroupMessage.CONFIRM_MESSAGE
                    dao.updateMessage(message)
                    if (isNotify) {
                        notifyThreadUpdate(accountContext, message.gid)
                    }
                    //UI
                    GroupMessageTransform.transformToModel(message)?.let {
                        EventBus.getDefault().post(MessageEvent(accountContext, message.gid, message.id, MessageEvent.EventType.RECEIVE_MESSAGE_INSERT, listOf(it)))
                    }

                } else {
                    dao.updateMessage(message)
                }
                return InsertMessageResult(UPDATE_SUCCESS, message.gid, -1, -1, message.id)
            }
            //
            return InsertMessageResult(REPLAY_MESSAGE, message.gid, -1, -1, -1)

        }

    }


    /**
     * 
     * @param message GroupMessage
     */
    fun insertFetchedMessages(accountContext: AccountContext, message: GroupMessage) {
        val dao = getDao(accountContext)?:return
        val dbMessage = dao.queryOneMessageByMid(message.gid, message.mid) ?: return
        if (dbMessage.is_confirm == GroupMessage.UNCONFIRM_MESSAGE || dbMessage.is_confirm == GroupMessage.FETCHING_MESSAGE) {
            message.read_state = dbMessage.read_state
            checkMessageVisibleState(accountContext, message)
            message.id = dbMessage.id
            dao.updateMessage(message)
        }
    }

    /**
     * 
     * @param gid Long
     * @param fromMid Long
     * @param toMid Long
     */
    fun insertFetchingMessages(accountContext: AccountContext, gid: Long, fromMid: Long, toMid: Long) {
        val idList = ArrayList<Long>()
        for (i in fromMid..toMid) {
            idList.add(i)
        }

        val dao = getDao(accountContext)?:return

        idList.split(500)
                .forEach { list ->
                    val listExist = dao.queryMessageByMidList(gid, list.toLongArray()).map { it.mid }
                    for (j in list) {
                        //，
                        if (listExist.contains(j)) {
                            continue
                        }

                        val unconfirmedMessage = GroupMessage()
                        unconfirmedMessage.is_confirm = GroupMessage.FETCHING_MESSAGE
                        unconfirmedMessage.mid = j
                        unconfirmedMessage.gid = gid
                        dao.insertMessage(unconfirmedMessage)
                    }
                }

    }

    /**
     * mid
     */
    fun queryMixMid(accountContext: AccountContext, gid: Long): Long {
        return getDao(accountContext)?.queryMaxMid(gid)?:0
    }

    //，
    fun queryOneMessage(accountContext: AccountContext, gid: Long, id: Long, hasMid: Boolean): GroupMessage? {
        val dao = getDao(accountContext)?:return null
        if (hasMid) {
            val message = dao.queryOneMessageByMid(gid, id)
            if (null != message) {
                if (message.gid == gid && message.mid == id && message.is_confirm != GroupMessage.DELETED_MESSAGE) {
                    return message
                }
            }
            return null
        } else {
            val message = dao.queryOneMessageByIndex(gid, id)
            if (message.gid == gid && message.id == id && message.is_confirm != GroupMessage.DELETED_MESSAGE) {
                return message
            }
            return null
        }
    }

    fun getMessageByMid(accountContext: AccountContext, gid: Long, mid: Long): AmeGroupMessageDetail? {
        val message = queryOneMessage(accountContext, gid, mid, true) ?: return null
        return GroupMessageTransform.transformToModel(message)
    }

    private fun getDao(accountContext: AccountContext): GroupMessageDao? {
        return Repository.getGroupMessageRepo(accountContext)
    }

    fun getExistMessageByMids(accountContext: AccountContext, gid: Long, minMid:Long, maxMid:Long): List<Long> {
        return getDao(accountContext)?.queryExistMessageByMids(gid, minMid, maxMid)?.toList()?: listOf()
    }

    private fun notifyThreadUpdate(accountContext: AccountContext, gid: Long) {
        Repository.getThreadRepo(accountContext)?.updateByNewGroup(gid)
    }

    fun recallMessage(accountContext: AccountContext, fromUid: String, gid: Long, recallMid: Long) {
        val dao = getDao(accountContext)?:return
        val databaseMessage: GroupMessage = dao.queryOneMessageByMid(gid, recallMid) ?: return

        val content = AmeGroupMessage.SystemContent(AmeGroupMessage.SystemContent.TIP_RECALL, fromUid, ArrayList(), "")
        databaseMessage.text = AmeGroupMessage(AmeGroupMessage.SYSTEM_INFO, content).toString()
        databaseMessage.content_type = AmeGroupMessage.SYSTEM_INFO.toInt()

        dao.insertMessage(databaseMessage)
        notifyThreadUpdate(accountContext, databaseMessage.gid)

        GroupMessageTransform.transformToModel(databaseMessage)?.let {
            EventBus.getDefault().post(MessageEvent(accountContext, databaseMessage.gid, MessageEvent.EventType.SEND_MESSAGE_UPDATE, listOf(it)))
        }

    }

    /**
     * 
     */
    fun systemNotice(accountContext: AccountContext, groupId: Long, content: AmeGroupMessage.SystemContent, read: Boolean = true, visible:Boolean = true): Long {
        ALog.d(TAG, "systemNotice groupId: $groupId, read: $read, type: ${content.tipType}")
        val messageDetail = AmeGroupMessageDetail().apply {
            gid = groupId
            sendTime = AmeTimeUtil.serverTimeMillis()
            sendState = AmeGroupMessageDetail.SendState.SEND_FAILED //，，
            senderId = accountContext.uid
            isSendByMe = true
            attachmentUri = ""
            extContent = null
            isRead = read
            message = AmeGroupMessage(AmeGroupMessage.SYSTEM_INFO, content)
        }

        return insertSendMessage(accountContext, GroupMessageTransform.transformToEntity(messageDetail), visible)
    }

    fun getFileStream(accountContext: AccountContext, masterSecret: MasterSecret, gid: Long, id: Long, offset: Long): InputStream? {
        val message = fetchOneMessageByGidAndIndexId(accountContext, gid, id)
        if (message != null) {
            val uri = message.toAttachmentUri()
            if (uri != null) {
                return when {
                    message.isFileEncrypted -> CtrStreamUtil.createForDecryptingInputStream(masterSecret, message.dataRandom, File(uri.path), offset)
                    uri.scheme == "file" -> FileInputStream(uri.path)
                    else -> AppContextHolder.APP_CONTEXT.contentResolver.openInputStream(uri)
                }
            }
        }

        return null
    }

    fun getThumbnailStream(accountContext: AccountContext, masterSecret: MasterSecret, gid: Long, id: Long): InputStream? {
        val message = fetchOneMessageByGidAndIndexId(accountContext, gid, id)
        if (message != null) {
            val uri = message.thumbnailUri
            if (uri != null) {
                return if (message.isFileEncrypted) {
                    CtrStreamUtil.createForDecryptingInputStream(masterSecret, message.thumbRandom, File(uri.path), 0)
                } else {
                    FileInputStream(uri.path)
                }
            }
        }

        return null
    }

    fun getExistThumbnailData(accountContext: AccountContext, hash: String) = getDao(accountContext)?.getExistMessageThumbnail(hash)

    fun getExistThumbnailData(accountContext: AccountContext, indexId: Long, hash: String) = getDao(accountContext)?.getExistMessageThumbnail(indexId, hash)

    fun getExistAttachmentData(accountContext: AccountContext, hash: String) = getDao(accountContext)?.getExistMessageAttachment(hash)

    fun getExistAttachmentData(accountContext: AccountContext, indexId: Long, hash: String) = getDao(accountContext)?.getExistMessageAttachment(indexId, hash)
}
