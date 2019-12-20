package com.bcm.messenger.common.grouprepository.modeltransform

import android.net.Uri
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.grouprepository.room.entity.GroupMessage

/**
 * 群消息数据库与上层展示层的数据结构转换帮助类
 */
object GroupMessageTransform {

    fun transformToModel(message: GroupMessage?): AmeGroupMessageDetail? {
        if (null == message) {
            return null
        }

        val ameMessage = AmeGroupMessageDetail()
        ameMessage.gid = message.gid
        ameMessage.indexId = message.id
        ameMessage.senderId = message.from_uid
        ameMessage.message = AmeGroupMessage.messageFromJson(message.text)
        ameMessage.attachmentUri = message.attachment_uri
        ameMessage.sendTime = message.create_time
        ameMessage.serverIndex = message.mid
        ameMessage.type = message.type //chat 消息或 public 消息
        ameMessage.isFileEncrypted = message.isFileEncrypted
        ameMessage.dataRandom = message.dataRandom
        ameMessage.dataHash = message.dataHash
        ameMessage.thumbRandom = message.thumbRandom
        ameMessage.thumbHash = message.thumbHash
        ameMessage.attachmentSize = message.attachmentSize

        // 解析extContent
        ameMessage.extContentString = message.extContent

        // 读取身份向量
        ameMessage.identityIvString = message.identityIvString

       ameMessage.keyVersion = message.key_version

        when (message.send_or_receive) {
            GroupMessage.SEND -> ameMessage.isSendByMe = true
            GroupMessage.RECEIVE -> ameMessage.isSendByMe = false
        }
        when (message.send_state) {
            GroupMessage.SENDING -> ameMessage.sendState = AmeGroupMessageDetail.SendState.SENDING
            GroupMessage.SEND_FAILURE -> ameMessage.sendState = AmeGroupMessageDetail.SendState.SEND_FAILED
            GroupMessage.SEND_SUCCESS -> ameMessage.sendState = AmeGroupMessageDetail.SendState.SEND_SUCCESS
            GroupMessage.RECEIVE_SUCCESS -> ameMessage.sendState = AmeGroupMessageDetail.SendState.RECEIVE_SUCCESS
        }

        if (message.thumbnailUri != null) {
            ameMessage.thumbnailUri = Uri.parse(message.thumbnailUri)
        }

        return ameMessage
    }

    /**
     * 将上层的群组详情信息转化成底层的群消息实体
     */
    fun transformToEntity(messageDetail: AmeGroupMessageDetail): GroupMessage {

        val msg = GroupMessage()
        msg.gid = messageDetail.gid
        msg.send_or_receive = when (messageDetail.isSendByMe) {
            true -> GroupMessage.SEND
            false -> GroupMessage.RECEIVE
        }

        msg.mid = messageDetail.serverIndex
        //TODO :判断是否是加密群，解密消息
        msg.text = messageDetail.message.toString()
        msg.type = messageDetail.type
        msg.attachment_uri = messageDetail.attachmentUri
        msg.create_time = messageDetail.sendTime
        msg.from_uid = messageDetail.senderId
        msg.read_state = when (messageDetail.isRead) {
            true -> GroupMessage.READ_STATE_READ
            false -> GroupMessage.READ_STATE_UNREAD
        }

        msg.key_version = messageDetail.keyVersion

        msg.send_state = when (messageDetail.sendState) {
            AmeGroupMessageDetail.SendState.SEND_FAILED -> GroupMessage.SEND_FAILURE
            AmeGroupMessageDetail.SendState.SEND_SUCCESS -> GroupMessage.SEND_SUCCESS
            AmeGroupMessageDetail.SendState.SENDING -> GroupMessage.SENDING
            AmeGroupMessageDetail.SendState.RECEIVE_SUCCESS -> GroupMessage.RECEIVE_SUCCESS
            else -> GroupMessage.SEND_FAILURE
        }

        msg.content_type = messageDetail.message.type.toInt()
        msg.is_confirm = GroupMessage.CONFIRM_MESSAGE
        msg.isFileEncrypted = messageDetail.isFileEncrypted
        if (messageDetail.message.type == AmeGroupMessage.DECRYPT_FAIL) {
            msg.is_confirm = GroupMessage.CONFIRM_BUT_NOT_SHOW
        }
        if (messageDetail.message.isLiveMessage()) {
            val content = messageDetail.message.content as AmeGroupMessage.LiveContent
            if (content.isPauseLive() || content.isRestartLive()) {
                msg.is_confirm = GroupMessage.CONFIRM_BUT_NOT_SHOW
            }
        }
        if (messageDetail.thumbnailUri != null) {
            msg.thumbnailUri = messageDetail.thumbnailUri.toString()
        }

        // 转化@列表字段
        msg.extContent = messageDetail.extContentString

        // 存储身份向量
        msg.identityIvString = messageDetail.identityIvString

        msg.dataRandom = messageDetail.dataRandom
        msg.dataHash = messageDetail.dataHash
        msg.thumbRandom = messageDetail.thumbRandom
        msg.thumbHash = messageDetail.thumbHash
        msg.attachmentSize = messageDetail.attachmentSize

        return msg

    }

    fun transformToModelList(messages: List<GroupMessage>): List<AmeGroupMessageDetail> {
        val ameGroupMessages = ArrayList<AmeGroupMessageDetail>()
        for (groupMessage in messages) {
            val message = transformToModel(groupMessage)
            if (message != null) {
                ameGroupMessages.add(message)
            }
        }
        return ameGroupMessages
    }

    fun transformToEntityList(messages: List<AmeGroupMessageDetail>): List<GroupMessage> {
        val groupMessages = mutableListOf<GroupMessage>()
        messages.forEach {
            groupMessages.add(transformToEntity(it))
        }
        return groupMessages
    }

}
