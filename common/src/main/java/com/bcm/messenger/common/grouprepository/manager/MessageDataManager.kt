package com.bcm.messenger.common.grouprepository.manager

import android.net.Uri
import android.text.TextUtils
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.crypto.CtrStreamUtil
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.database.db.UserDatabase
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
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.bean.ConversationStorage
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.GroupUtil
import com.bcm.messenger.common.crypto.encrypt.FileInfo
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

    fun isMe(uid: String, mid: Long, gid: Long): Boolean {
        return uid == AMELogin.uid
    }

    fun isLogined(): Boolean {
        return AMELogin.isLogin
    }

    // 
    fun setGroupMessageRead(gid: Long) {
        getDao().setMessageRead(gid)
    }

    // uri，
    fun updateMessageAttachmentUri(gid: Long, indexId: Long, fileInfo: FileInfo) {
        if (gid <= 0) {
            //notice ，gid，
            return
        }
        val message = getDao().queryOneMessageByIndex(gid, indexId) ?: return
        message.attachment_uri = Uri.fromFile(fileInfo.file).toString()
        message.dataHash = fileInfo.hash
        message.dataRandom = fileInfo.random
        message.attachmentSize = fileInfo.size
        message.isFileEncrypted = fileInfo.random != null
        getDao().updateMessage(message)

        EventBus.getDefault().post(MessageEvent(message.gid, MessageEvent.EventType.ATTACHMENT_DOWNLOAD_SUCCESS,
                listOf(GroupMessageTransform.transformToModel(message)
                        ?: return)))
    }

    fun updateMessageThumbnailUri(gid: Long, indexId: Long, fileInfo: FileInfo) {
        if (gid <= 0) return

        val message = getDao().queryOneMessageByIndex(gid, indexId) ?: return
        message.thumbnailUri = Uri.fromFile(fileInfo.file).toString()
        message.thumbHash = fileInfo.hash
        message.thumbRandom = fileInfo.random
        message.isFileEncrypted = fileInfo.random != null
        getDao().updateMessage(message)

        EventBus.getDefault().post(MessageEvent(message.gid, MessageEvent.EventType.THUMBNAIL_DOWNLOAD_SUCCESS,
                listOf(GroupMessageTransform.transformToModel(message))))
    }

    fun updateMessageContent(gid: Long, indexId: Long, content: String) {
        val message = getDao().queryOneMessageByIndex(gid, indexId) ?: return
        message.text = content
        getDao().updateMessage(message)
    }


    fun deleteOneMessageByIndexId(gid: Long, indexId: Long) {
        val message = getDao().queryOneMessageByIndex(gid, indexId) ?: return
        message.is_confirm = GroupMessage.DELETED_MESSAGE
        message.text = ""

        if (!TextUtils.isEmpty(message.attachment_uri)) {
            BcmFileUtils.delete(message.attachment_uri)
        }

        getDao().updateMessage(message)
        notifyThreadUpdate(message.gid)
        EventBus.getDefault().post(MessageEvent(message.gid, indexId, MessageEvent.EventType.DELETE_ONE_MESSAGE))
    }


    /*
    * 
    * */
    fun deleteMessagesByGid(gid: Long) {
        val list = getDao().loadGroupMessageByGid(gid)
        if (list != null && list.isNotEmpty()) {
            for (m in list) {
                m.is_confirm = GroupMessage.DELETED_MESSAGE
                if (!TextUtils.isEmpty(m.attachment_uri)) {
                    BcmFileUtils.delete(m.attachment_uri)
                }
            }
            getDao().updateMessages(list)

            //，
            if (Repository.getThreadRepo().getThreadIdIfExist(Recipient.recipientFromNewGroupId(AppContextHolder.APP_CONTEXT, gid)) > 0) {
                notifyThreadUpdate(gid)
            }
        }
    }

    /**
     * 
     *
     * @param messageList 
     */
    fun deleteMessages(gid: Long, messageList: List<GroupMessage>) {
        for (msg in messageList) {
            msg.is_confirm = GroupMessage.DELETED_MESSAGE
            if (!TextUtils.isEmpty(msg.attachment_uri)) {
                BcmFileUtils.delete(msg.attachment_uri)
            }
        }
        getDao().updateMessages(messageList)
        notifyThreadUpdate(gid)
    }

    /**
     * 
     * @param gid
     * @param indexId
     * @param send_state   SEND_SUCCESS = 1; SENDING = 2; SEND_FAILURE = 10000;
     * @return -1  , 0:
     */
    fun updateMessageSendStateByIndex(gid: Long, indexId: Long, send_state: Int): Int {
        val message = queryOneMessage(gid, indexId, false)
        return if (message != null) {
            message.send_state = send_state
            getDao().updateMessage(message)

            val groupMessageDetail = GroupMessageTransform.transformToModel(message)
            groupMessageDetail?.let {
                EventBus.getDefault().post(MessageEvent(message.gid, MessageEvent.EventType.SEND_MESSAGE_UPDATE, listOf(it)))
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
    fun updateMessageEncryptMode(gid: Long, indexId: Long, keyVersion:Long): Int {
        val message = queryOneMessage(gid, indexId, false)
        if (message != null) {
            message.key_version = keyVersion

            getDao().updateMessage(message)
            return 0
        } else {
            return -1
        }

    }

    /**
     * 
     */
    fun updateMessageSendResult(gid: Long, indexId: Long, mid: Long, createTime: Long, iv: String, text: String, sendState: Int) {
        val message = queryOneMessage(gid, indexId, false)
        if (message != null) {
            message.mid = mid
            message.create_time = createTime
            // 
            message.identityIvString = iv
            message.text = text
            message.send_state = sendState
            getDao().updateMessage(message)

            val groupMessageDetail = GroupMessageTransform.transformToModel(message)
            groupMessageDetail?.let {
                EventBus.getDefault().post(MessageEvent(gid, MessageEvent.EventType.SEND_MESSAGE_UPDATE, listOf(it)))
            }
        }

    }


    /**
     *  indexId 
     * 
     * @param gid
     * @param indexId
     */
    fun fetchOneMessageByGidAndIndexId(gid: Long, indexId: Long): AmeGroupMessageDetail? {
        return GroupMessageTransform.transformToModel(getDao().queryOneMessageByIndex(gid, indexId))
    }

    /**
     *  indexId 
     * 
     * @param gid
     * @param indexId
     */
    fun fetchOneVisiableMessageByGidAndIndexId(gid: Long, indexId: Long): AmeGroupMessageDetail? {
        return GroupMessageTransform.transformToModel(getDao().queryOneVisiableMessageByIndex(gid, indexId))
    }

    /**
     * mid 
     * 
     */
    fun fetchOneMessageByGidAndMid(gid: Long, mid: Long): AmeGroupMessageDetail? {
        return GroupMessageTransform.transformToModel(getDao().queryOneMessageByMid(gid, mid))
    }

    /**
     * mid 
     * 
     */
    fun fetchOneMessageByMidConfirm(gid: Long, mid: Long): AmeGroupMessageDetail? {
        return GroupMessageTransform.transformToModel(getDao().queryOneMessageByMidConfirm(gid, mid))
    }

    /**
     * 
     * @param gid
     * @param indexId ，-1
     * @param count 
     * @return 
     */
    fun fetchMessageByGidAndIndexId(gid: Long, indexId: Long, count: Int): List<AmeGroupMessageDetail> {
        if (indexId == -1L) {//
            val lastId = getDao().queryMaxIndexId(gid)
            return GroupMessageTransform.transformToModelList(getDao().loadMessagesByGidAndIndexId(gid, lastId + 1, count))
        }
        return GroupMessageTransform.transformToModelList(getDao().loadMessagesByGidAndIndexId(gid, indexId, count))
    }


    /**
     * 
     * @param gid
     * @param formIndexId ，-1
     * @param count 
     * @param isBackWord 
     * @return 
     */
    fun fetchImageOrVideoMessage(gid: Long, formIndexId: Long, count: Int, isBackWord: Boolean): List<AmeGroupMessageDetail> {
        if (isBackWord) {
            return GroupMessageTransform.transformToModelList(getDao().loadImageOrVideoMessagesAfterIndexId(gid, formIndexId, count))
        } else {
            return GroupMessageTransform.transformToModelList(getDao().loadImageOrVideoMessagesBeforeIndexId(gid, formIndexId, count))
        }
    }

    /**
     * 
     * @param gid
     * @param formIndexId ，-1
     * @param count 
     * @param isBackWord 
     * @return 
     */
    fun fetchTextMessage(gid: Long, formIndexId: Long, count: Int, isBackWord: Boolean): List<AmeGroupMessageDetail> {
        return if (isBackWord) {
            GroupMessageTransform.transformToModelList(getDao().loadTextMessagesAfterIndexId(gid, formIndexId, count))
        } else {
            GroupMessageTransform.transformToModelList(getDao().loadTextMessagesBeforeIndexId(gid, formIndexId, count))
        }
    }


    /**
     * 
     *
     * @param gid ID
     * @return 
     */
    fun fetchFileMessages(gid: Long): List<AmeGroupMessageDetail> {
        return GroupMessageTransform.transformToModelList(getDao().loadAllFileMessages(gid))
    }


    /**
     * 
     *
     * @param gid ID
     * @return 
     */
    fun fetchLinkMessages(gid: Long): List<AmeGroupMessageDetail> {
        return GroupMessageTransform.transformToModelList(getDao().loadAllLinkMessages(gid))
    }

    /**
     * 
     *
     * @param gid ID
     * @return 
     */
    fun fetchMediaMessages(gid: Long): List<AmeGroupMessageDetail> {
        return GroupMessageTransform.transformToModelList(getDao().loadAllImageOrVideoMessages(gid))
    }


    /**
     * 
     */
    fun fetchMediaMessageStorageSize(gid: Long): ConversationStorage {
        val list = GroupMessageTransform.transformToModelList(getDao().loadAllMediaMessages(gid))

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
    fun deleteAllMediaMessage(gid: Long, type: Int) {
        val list = getDao().loadAllMediaMessages(gid)

        val deleted = ArrayList<GroupMessage>()
        if (list.isNotEmpty()) {
            for (m in list) {
                if ((m.content_type.toLong() == AmeGroupMessage.FILE && ConversationStorage.testFlag(type, ConversationStorage.TYPE_FILE))
                        || (m.content_type.toLong() == AmeGroupMessage.IMAGE && ConversationStorage.testFlag(type, ConversationStorage.TYPE_IMAGE))
                        || (m.content_type.toLong() == AmeGroupMessage.VIDEO && ConversationStorage.testFlag(type, ConversationStorage.TYPE_VIDEO))) {
                    m.is_confirm = GroupMessage.DELETED_MESSAGE
                    if (!TextUtils.isEmpty(m.attachment_uri)) {
                        BcmFileUtils.delete(m.attachment_uri)
                    }
                    deleted.add(m)
                }
            }
            getDao().updateMessages(deleted)
            notifyThreadUpdate(gid)
        }
    }

    /**
     * fromMid（）toMid，
     */
    fun fetchMessageFromToMid(gid: Long, fromMid: Long, toMid: Long): List<AmeGroupMessageDetail> {
        return GroupMessageTransform.transformToModelList(getDao().loadMessageFromTo(gid, fromMid, toMid))
    }

    /**
     * ，
     * @param gid
     * @return 
     */
    fun fetchLastMessage(gid: Long): GroupMessage? {
        return getDao().queryLastMessageByGid(gid)
    }


    fun countUnreadCount(gid: Long): Long {
        return getDao().countUnread(gid)
    }

    fun countUnreadCountFromLastSeen(gid: Long, lastSeen:Long): Long {
        return getDao().countUnreadFromLastSeen(gid, lastSeen)
    }

    fun getAllUnreadThreadCount(): Int {
        return getDao().allUnreadThreadCount
    }

    fun countMessageByGid(gid: Long): Long {
        return getDao().countMessagesByGid(gid)
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
    fun insertSendMessage(sendMessage: GroupMessage, visible: Boolean = true): Long {
        if (!visible) {
            sendMessage.is_confirm = GroupMessage.CONFIRM_BUT_NOT_SHOW
        }

        val indexId = getDao().insertMessage(sendMessage)
        sendMessage.id = indexId
        notifyThreadUpdate(sendMessage.gid)

        //UI
        if (sendMessage.is_confirm == GroupMessage.CONFIRM_MESSAGE) {
            val messageDetail = GroupMessageTransform.transformToModel(sendMessage)
            messageDetail?.let {
                EventBus.getDefault().post(MessageEvent(sendMessage.gid, MessageEvent.EventType.SEND_MESSAGE_INSERT, listOf(it)))
            }
        }
        return indexId
    }

    /**
     * ，
     * @param message
     * @return 0：，-2:, -3: 
     */
    fun insertReceiveMessage(message: GroupMessage): InsertMessageResult {
        checkMessageVisibleState(message)
        return insertReceiveMessage(message, true)
    }

    private fun checkMessageVisibleState(message: GroupMessage) {
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
                val groupInfo = GroupInfoDataManager.queryOneGroupInfo(message.gid)
                //
                if (AMELogin.uid != groupInfo?.owner && !content.theOperator.any { AMELogin.uid == it }) {
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
            val groupRecipient = Recipient.from(AppContextHolder.APP_CONTEXT,
                    GroupUtil.addressFromGid(message.gid),
                    false)

            Repository.getThreadRepo()
                    .getThreadIdFor(groupRecipient)

        }
    }

    private fun insertReceiveMessage(message: GroupMessage, isNotify: Boolean): InsertMessageResult {
        val savedMaxMid = getDao().queryMaxMid(message.gid)
        if (message.mid > savedMaxMid) {
            if (message.mid - savedMaxMid == 1L || getDao().countMessagesByGid(message.gid) == 0L) {
                val indexId = getDao().insertMessage(message)
                message.id = indexId
                if (message.is_confirm != GroupMessage.CONFIRM_BUT_NOT_SHOW && isNotify)
                    notifyThreadUpdate(message.gid)
                return InsertMessageResult(INSERT_SUCCESS, message.gid, -1, -1, indexId)
            } else {
                //，
                var i = 1
                do {
                    val unconfirmedMessage = GroupMessage()
                    unconfirmedMessage.is_confirm = GroupMessage.UNCONFIRM_MESSAGE
                    unconfirmedMessage.mid = savedMaxMid + i
                    unconfirmedMessage.gid = message.gid
                    getDao().insertMessage(unconfirmedMessage)
                    i++
                } while (i < message.mid - savedMaxMid)
                if (message.is_confirm != GroupMessage.CONFIRM_BUT_NOT_SHOW)
                    message.is_confirm = GroupMessage.CONFIRM_MESSAGE
                val indexId = getDao().insertMessage(message)
                message.id = indexId
                if (isNotify) {
                    notifyThreadUpdate(message.gid)
                }
                EventBus.getDefault().post(GroupMessageMissedEvent(message.gid, savedMaxMid, message.mid-1))
                //,
                return InsertMessageResult(INSERT_SUCCESS, message.gid, savedMaxMid, message.mid, indexId)
            }
        } else {
            val databaseMessage: GroupMessage = getDao().queryOneMessageByMid(message.gid, message.mid)
            if (databaseMessage.is_confirm == GroupMessage.UNCONFIRM_MESSAGE || databaseMessage.is_confirm == GroupMessage.FETCHING_MESSAGE) {
                message.id = databaseMessage.id
                message.read_state = databaseMessage.read_state
                if (message.is_confirm != GroupMessage.CONFIRM_BUT_NOT_SHOW) {
                    message.is_confirm = GroupMessage.CONFIRM_MESSAGE
                    getDao().updateMessage(message)
                    if (isNotify) {
                        notifyThreadUpdate(message.gid)
                    }
                    //UI
                    GroupMessageTransform.transformToModel(message)?.let {
                        EventBus.getDefault().post(MessageEvent(message.gid, message.id, MessageEvent.EventType.RECEIVE_MESSAGE_INSERT, listOf(it)))
                    }

                } else {
                    getDao().updateMessage(message)
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
    fun insertFetchedMessages(message: GroupMessage) {
        val dbMessage = getDao().queryOneMessageByMid(message.gid, message.mid) ?: return
        if (dbMessage.is_confirm == GroupMessage.UNCONFIRM_MESSAGE || dbMessage.is_confirm == GroupMessage.FETCHING_MESSAGE) {
            message.read_state = dbMessage.read_state
            checkMessageVisibleState(message)
            message.id = dbMessage.id
            getDao().updateMessage(message)
        }
    }

    /**
     * 
     * @param gid Long
     * @param fromMid Long
     * @param toMid Long
     */
    fun insertFetchingMessages(gid: Long, fromMid: Long, toMid: Long) {
        val idList = ArrayList<Long>()
        for (i in fromMid..toMid) {
            idList.add(i)
        }

        idList.split(500)
                .forEach { list ->
                    val listExist = getDao().queryMessageByMidList(gid, list.toLongArray()).map { it.mid }
                    for (j in list) {
                        //，
                        if (listExist.contains(j)) {
                            continue
                        }

                        val unconfirmedMessage = GroupMessage()
                        unconfirmedMessage.is_confirm = GroupMessage.FETCHING_MESSAGE
                        unconfirmedMessage.mid = j
                        unconfirmedMessage.gid = gid
                        getDao().insertMessage(unconfirmedMessage)
                    }
                }

    }

    /**
     * mid
     */
    fun queryMixMid(gid: Long): Long {
        return getDao().queryMaxMid(gid)
    }

    //，
    fun queryOneMessage(gid: Long, id: Long, hasMid: Boolean): GroupMessage? {
        if (hasMid) {
            val message = getDao().queryOneMessageByMid(gid, id)
            if (null != message) {
                if (message.gid == gid && message.mid == id && message.is_confirm != GroupMessage.DELETED_MESSAGE) {
                    return message
                }
            }
            return null
        } else {
            val message = getDao().queryOneMessageByIndex(gid, id)
            if (message.gid == gid && message.id == id && message.is_confirm != GroupMessage.DELETED_MESSAGE) {
                return message
            }
            return null
        }
    }

    fun getMessageByMid(gid: Long, mid: Long): AmeGroupMessageDetail? {
        val message = queryOneMessage(gid, mid, true) ?: return null
        return GroupMessageTransform.transformToModel(message)
    }

    fun getExistMessageByMids(gid: Long, minMid:Long, maxMid:Long): List<Long> {
        return getDao().queryExistMessageByMids(gid, minMid, maxMid).toList()
    }

    //
    fun queryMinReadMessage(gid: Long): GroupMessage? {
        val groupList = getDao().queryMinReadMessage(gid, 1)
        if (groupList != null && groupList.size > 0) {
            return groupList[0]
        }
        return null
    }


    //
    fun loadMessagesByGidAfter(gid: Long, indexId: Long, count: Int): List<AmeGroupMessageDetail> {
        return if (count == -1) {
            GroupMessageTransform.transformToModelList(getDao().loadMessagesByGidAndIndexIdAfter(gid, indexId))
        } else {
            GroupMessageTransform.transformToModelList(getDao().loadMessagesByGidAndIndexIdAfter(gid, indexId, count))
        }
    }

    //， text 
    fun loadMessagesBySenderAndPeriod(gid: Long, uid: String, startTime: Long, endTime: Long):List<GroupMessage>{
        return getDao().fetchMessagesBySenderAndPeriod(gid,uid,startTime,endTime)
    }

    private fun getDao(): GroupMessageDao {
        return UserDatabase.getDatabase().groupMessageDao()
    }

    private fun notifyThreadUpdate(gid: Long) {
        Repository.getThreadRepo().updateByNewGroup(gid)
    }

    fun recallMessage(fromUid: String, gid: Long, recallMid: Long) {
        val databaseMessage: GroupMessage = getDao().queryOneMessageByMid(gid, recallMid) ?: return

        val content = AmeGroupMessage.SystemContent(AmeGroupMessage.SystemContent.TIP_RECALL, fromUid, ArrayList(), "")
        databaseMessage.text = AmeGroupMessage(AmeGroupMessage.SYSTEM_INFO, content).toString()
        databaseMessage.content_type = AmeGroupMessage.SYSTEM_INFO.toInt()

        getDao().insertMessage(databaseMessage)
        notifyThreadUpdate(databaseMessage.gid)

        GroupMessageTransform.transformToModel(databaseMessage)?.let {
            EventBus.getDefault().post(MessageEvent(databaseMessage.gid, MessageEvent.EventType.SEND_MESSAGE_UPDATE, listOf(it)))
        }

    }

    /**
     * 
     */
    fun systemNotice(groupId: Long, content: AmeGroupMessage.SystemContent, read: Boolean = true, visible:Boolean = true): Long {
        ALog.d(TAG, "systemNotice groupId: $groupId, read: $read, type: ${content.tipType}")
        val messageDetail = AmeGroupMessageDetail().apply {
            gid = groupId
            sendTime = AmeTimeUtil.getMessageSendTime()
            sendState = AmeGroupMessageDetail.SendState.SEND_FAILED //，，
            senderId = AMELogin.uid
            isSendByMe = true
            attachmentUri = ""
            extContent = null
            isRead = read
            message = AmeGroupMessage(AmeGroupMessage.SYSTEM_INFO, content)
        }

        return insertSendMessage(GroupMessageTransform.transformToEntity(messageDetail), visible)
    }

    fun getFileStream(masterSecret: MasterSecret, gid: Long, id: Long, offset: Long): InputStream? {
        val message = fetchOneMessageByGidAndIndexId(gid, id)
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

    fun getThumbnailStream(masterSecret: MasterSecret, gid: Long, id: Long): InputStream? {
        val message = fetchOneMessageByGidAndIndexId(gid, id)
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

    fun getExistThumbnailData(hash: String) = getDao().getExistMessageThumbnail(hash)

    fun getExistThumbnailData(indexId: Long, hash: String) = getDao().getExistMessageThumbnail(indexId, hash)

    fun getExistAttachmentData(hash: String) = getDao().getExistMessageAttachment(hash)

    fun getExistAttachmentData(indexId: Long, hash: String) = getDao().getExistMessageAttachment(indexId, hash)
}
