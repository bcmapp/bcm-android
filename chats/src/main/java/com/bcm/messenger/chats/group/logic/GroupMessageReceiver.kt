package com.bcm.messenger.chats.group.logic

import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.crypto.GroupProfileDecryption
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.events.*
import com.bcm.messenger.common.grouprepository.manager.GroupInfoDataManager
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager
import com.bcm.messenger.common.grouprepository.manager.UserDataManager
import com.bcm.messenger.common.grouprepository.model.AmeGroupMemberChanged
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.grouprepository.modeltransform.GroupInfoTransform
import com.bcm.messenger.common.grouprepository.room.entity.GroupMessage
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.server.IServerDataListener
import com.bcm.messenger.common.utils.AmePushProcess
import com.bcm.messenger.common.utils.base64Decode
import com.bcm.messenger.common.crypto.encrypt.GroupMessageEncryptUtils
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.AmeURLUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.logger.ALog
import com.google.protobuf.AbstractMessage
import com.orhanobut.logger.Logger
import org.greenrobot.eventbus.EventBus
import org.json.JSONObject
import org.spongycastle.pqc.math.linearalgebra.ByteUtils
import org.whispersystems.curve25519.Curve25519
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.libsignal.ecc.DjbECPublicKey
import org.whispersystems.signalservice.internal.websocket.GroupMessageProtos
import java.nio.charset.StandardCharsets
import java.util.*

class GroupMessageReceiver : IServerDataListener {
    private val TAG = "GroupMessageReceiver"

    override fun onReceiveData(proto: AbstractMessage): Boolean {
        try {
            if (proto !is GroupMessageProtos.GroupMsg) {
                return false
            }

            when (proto.type) {
                GroupMessageProtos.GroupMsg.Type.TYPE_CHAT -> {
                    val groupChatMessage = GroupMessageProtos.GroupChatMsg.parseFrom(proto.body)
                    val fromUid = getRealUid(groupChatMessage.gid, groupChatMessage.fromUid, groupChatMessage.sourceExtra)
                    if (null != fromUid) {
                        handleGroupChatMessage(groupChatMessage, fromUid)
                    } else {
                        ALog.e(TAG, "who are u?")
                    }
                }
                GroupMessageProtos.GroupMsg.Type.TYPE_INFO_UPDATE -> {
                    val groupInfoUpdate = GroupMessageProtos.GroupInfoUpdate.parseFrom(proto.body)
                    handleGroupInfoUpdateMessage(groupInfoUpdate)
                }
                GroupMessageProtos.GroupMsg.Type.TYPE_MEMBER_UPDATE -> {
                    val groupMemberUpdate = GroupMessageProtos.GroupMemberUpdate.parseFrom(proto.body)
                    handleGroupMemberUpdate(groupMemberUpdate)
                }
                GroupMessageProtos.GroupMsg.Type.TYPE_RECALL -> {
                    val groupRecallMessage = GroupMessageProtos.GroupRecallMsg.parseFrom(proto.body)
                    val fromUid = getRealUid(groupRecallMessage.gid, groupRecallMessage.fromUid, groupRecallMessage.sourceExtra)
                    if (null != fromUid) {
                        handleGroupRecallMessage(groupRecallMessage, fromUid)
                    } else {
                        ALog.e(TAG, "who are u?")
                    }
                }
                GroupMessageProtos.GroupMsg.Type.TYPE_KEY_REFRESH -> {
                    val groupSecretRefresh = GroupMessageProtos.GroupSecretRefresh.parseFrom(proto.body)

                    EventBus.getDefault().post(GroupRefreshKeyEvent(groupSecretRefresh.gid,
                            groupSecretRefresh.uid, groupSecretRefresh.messageSecret, groupSecretRefresh.groupInfoSecret))
                }
                GroupMessageProtos.GroupMsg.Type.TYPE_JOIN_REVIEW -> {
                    val groupJoinReviewRequest = GroupMessageProtos.GroupJoinReviewRequest.parseFrom(proto.body)
                    EventBus.getDefault().post(GroupJoinReviewRequestEvent(groupJoinReviewRequest.gid))
                }
                GroupMessageProtos.GroupMsg.Type.TYPE_SWITCH_GROUP_KEYS -> {
                    val switchKey = GroupMessageProtos.GroupSwitchGroupKeys.parseFrom(proto.body)
                    EventBus.getDefault().post(GroupKeyRefreshCompleteEvent(switchKey.gid,
                            switchKey.mid, switchKey.fromUid, switchKey.version))
                }
                GroupMessageProtos.GroupMsg.Type.TYPE_UPDATE_GROUP_KEYS_REQUEST -> {
                    val updateKeyRequest = GroupMessageProtos.GroupUpdateGroupKeysRequest.parseFrom(proto.body)
                    EventBus.getDefault().post(GroupKeyRefreshStartEvent(updateKeyRequest.gid,
                            updateKeyRequest.mid, updateKeyRequest.fromUid, updateKeyRequest.keysMode))
                }
                else -> ALog.w(TAG, "unsupport group message type: ${proto.type}")
            }
        } catch (e: Throwable) {
            ALog.e(TAG, "onReceiveData", e)
        }

        return true
    }

    private fun handleGroupChatMessage(serverMessage: GroupMessageProtos.GroupChatMsg, fromUid:String) {
        if (MessageDataManager.isMe(fromUid, serverMessage.mid, serverMessage.gid)) {
            return
        }

        val decryptBean = GroupMessageEncryptUtils.decapsulateMessage(serverMessage.text)
        val decryptText = if (decryptBean == null) {
            serverMessage.text
        } else {
            val keyParam = GroupInfoDataManager.queryGroupKeyParam(serverMessage.gid, decryptBean.keyVersion)
            val result = GroupMessageEncryptUtils.decryptMessageProcess(keyParam, decryptBean)
            if (result == null) {
                GroupMessageLogic.syncOfflineMessage(serverMessage.gid, serverMessage.mid, Math.max(serverMessage.mid-1, 0))
                //Received a message that could not be decrypted and lost it directly, and subsequently pulled it down through the offline channel
                ALog.i(TAG, "decrypt group message failed ${serverMessage.mid}")
            }
            result
        }

        var message = AmeGroupMessage.messageFromJson(decryptText)

        if (message.isText() && AmeURLUtil.isLegitimateUrl((message.content as AmeGroupMessage.TextContent).text)) {
            val content = AmeGroupMessage.LinkContent((message.content as AmeGroupMessage.TextContent).text, "")
            message = AmeGroupMessage(AmeGroupMessage.LINK, content)
        }

        if (!MessageDataManager.isLogined()) {
            return
        }

        val detail = AmeGroupMessageDetail()
        detail.gid = serverMessage.gid


        detail.serverIndex = serverMessage.mid
        detail.senderId = fromUid
        detail.sendTime = serverMessage.createTime
        detail.message = message
        detail.type = 1
        detail.keyVersion = decryptBean.keyVersion

        detail.isSendByMe = MessageDataManager.isMe(fromUid, serverMessage.mid, serverMessage.gid)
        if (!detail.isSendByMe) {
            detail.sendState = AmeGroupMessageDetail.SendState.RECEIVE_SUCCESS
        } else {
            detail.sendState = AmeGroupMessageDetail.SendState.SEND_SUCCESS
        }

        detail.extContent = AmeGroupMessageDetail.ExtensionContent(serverMessage.content)

        EventBus.getDefault().post(GroupMessageEvent(detail))
    }

    private fun handleGroupInfoUpdateMessage(message: GroupMessageProtos.GroupInfoUpdate) {
        val gInfo = GroupInfoDataManager.queryOneGroupInfo(message.gid) ?: return

        var newName:String? = null
        var newIcon:String? = null

        if (gInfo.role != AmeGroupMemberInfo.OWNER) {
            val name = if (message.encryptedName != null) {
                GroupProfileDecryption.decryptProfile(message.encryptedName, gInfo.groupPrivateKey) ?: message.name
            } else {
                message.name
            }

            if (name != gInfo.name) {
                newName = name
            }

            val iconUrl = if (message.encryptedIcon != null) {
                GroupProfileDecryption.decryptProfile(message.encryptedIcon, gInfo.groupPrivateKey) ?: message.icon
            } else {
                message.icon
            }

            if (iconUrl != gInfo.iconUrl) {
                newIcon = iconUrl
                gInfo.iconUrl = iconUrl
            }

            //Focus only on group avatars, nicknames, announcement changes
            val groupMessage = GroupMessage()
            groupMessage.gid = message.gid
            groupMessage.mid = message.mid
            groupMessage.read_state = 1
            if (newName?.isNotEmpty() == true) {
                groupMessage.is_confirm = GroupMessage.CONFIRM_MESSAGE
                gInfo.name = newName
            } else {
                groupMessage.is_confirm = GroupMessage.CONFIRM_BUT_NOT_SHOW
            }
            groupMessage.create_time = message.createTime
            groupMessage.from_uid = message.fromUid
            groupMessage.content_type = AmeGroupMessage.SYSTEM_INFO.toInt()
            val content = AmeGroupMessage.SystemContent(AmeGroupMessage.SystemContent.TIP_GROUP_NAME_UPDATE, "", listOf(newName?:""))
            groupMessage.text = AmeGroupMessage(AmeGroupMessage.SYSTEM_INFO, content).toString()
            MessageDataManager.insertReceiveMessage(groupMessage)

            GroupLogic.updateGroupNameAndIcon(message.gid, newName?:gInfo.name, newIcon?:gInfo.iconUrl)

            val e = GroupInfoUpdateNotify()
            e.groupInfo = GroupInfoTransform.transformToModel(gInfo)
            EventBus.getDefault().post(e)
        }
    }

    private fun handleGroupMemberUpdate(message: GroupMessageProtos.GroupMemberUpdate) {
        if (isMyQuitGroupEvent(message)) {
            ALog.i(TAG, "leave group" + message.gid)

            val db = Repository.getThreadRepo()
            val groupRecipient = Recipient.recipientFromNewGroupId(AppContextHolder.APP_CONTEXT, message.gid)
            val threadId = db.getThreadIdIfExist(groupRecipient)
            if (threadId > 0) {
                db.cleanConversationContentForGroup(threadId, message.gid)
            }
            return
        }

        val memberChangedMessage = AmeGroupMemberChanged(message.gid, message.mid)
        memberChangedMessage.action = message.action
        memberChangedMessage.fromUid = message.fromUid

        memberChangedMessage.createTime = AmeTimeUtil.serverTimeMillis()
        val list = ArrayList<AmeGroupMemberInfo>()
        for (member in message.membersList) {
            val info = AmeGroupMemberInfo()
            info.uid = Address.fromSerialized(member.uid)
            info.role = member.role
            info.gid = message.gid
            if (message.action == AmeGroupMemberChanged.LEAVE) {
                info.role = UserDataManager.queryGroupMemberRole(info.gid, member.uid)
            }
            list.add(info)
        }
        memberChangedMessage.memberList = list

        val bcmData = AmePushProcess.BcmData(AmePushProcess.BcmNotify(AmePushProcess.GROUP_NOTIFY, null, AmePushProcess.GroupNotifyData(message.mid, message.gid, false), null, null, null))
        AmePushProcess.processPush(bcmData, false)

        Logger.i("GroupMemberChangedNotify ${memberChangedMessage.groupId} event:${memberChangedMessage.action} count:${memberChangedMessage.memberList.size}")
        val detail = memberChangedMessage.toDetail()
        if (null != detail.message) {
            EventBus.getDefault().post(GroupMessageEvent(detail))
        }

        EventBus.getDefault().post(GroupMemberChangedNotify(memberChangedMessage))
    }

    private fun isMyQuitGroupEvent(message: GroupMessageProtos.GroupMemberUpdate): Boolean {
        val loginUid = AMELogin.uid
        if (message.fromUid == loginUid && message.action == AmeGroupMemberChanged.LEAVE) {
            if (message.membersCount == 1) {
                return message.getMembers(0).uid == loginUid
            }
        }
        return false
    }


    private fun handleGroupRecallMessage(message: GroupMessageProtos.GroupRecallMsg, fromUid: String) {
        val groupMessage = GroupMessage()
        groupMessage.gid = message.gid
        groupMessage.mid = message.mid
        groupMessage.is_confirm = GroupMessage.CONFIRM_BUT_NOT_SHOW
        groupMessage.create_time = AmeTimeUtil.serverTimeMillis() / 1000
        groupMessage.from_uid = fromUid
        MessageDataManager.insertReceiveMessage(groupMessage)

        MessageDataManager.recallMessage(fromUid, message.gid, message.recalledMid)
    }

    private fun getRealUid(gid:Long, defaultUid:String?, encryptedUid:String?): String? {
        if (null == encryptedUid || encryptedUid.isEmpty()) {
            return defaultUid
        }
        try {
            val sourceExtra = String(encryptedUid.base64Decode(), StandardCharsets.UTF_8)
            ALog.d(TAG, "$gid getFinalSource source_extra: $sourceExtra")
            val json = JSONObject(sourceExtra)
            val encryptSource = json.optString("source")
            val ephemeralPubKey = json.optString("ephemeralPubkey")
            val groupMsgPubKey = json.optString("groupMsgPubkey")
            val iv = json.optString("iv")
            val version = json.optInt("version")

            val groupInfo = GroupInfoDataManager.queryOneGroupInfo(gid)
                    ?: throw Exception("$gid groupInfo is null")
            if (!ByteUtils.equals(groupInfo.channelPublicKey, groupMsgPubKey.base64Decode())) {
                throw Exception("$gid groupMsgPubKey is wrong")
            }
            val djbECPublicKey = Curve.decodePoint(ephemeralPubKey.base64Decode(), 0) as DjbECPublicKey
            val ecdh = Curve25519.getInstance(Curve25519.BEST).calculateAgreement(djbECPublicKey.publicKey, groupInfo.channelPrivateKey)
            return String(EncryptUtils.decryptAES(encryptSource.base64Decode(),
                    EncryptUtils.computeSHA256(ecdh), EncryptUtils.MODE_AES, iv.base64Decode()), StandardCharsets.UTF_8)

        } catch (ex: Exception) {
            ALog.e(TAG, "$gid getFinalSource error", ex)
        }
        return null
    }
}
