package com.bcm.messenger.chats.group.logic

import android.annotation.SuppressLint
import android.text.TextUtils
import com.bcm.messenger.chats.group.core.GroupManagerCore
import com.bcm.messenger.chats.group.core.group.GroupMessageEntity
import com.bcm.messenger.chats.group.logic.GroupLogic.checkGroupKeyValidState
import com.bcm.messenger.chats.group.logic.GroupLogic.queryGroupInfo
import com.bcm.messenger.chats.group.logic.secure.GroupKeyRotate
import com.bcm.messenger.chats.group.logic.sync.GroupOfflineDecryptFailCounter
import com.bcm.messenger.chats.group.logic.sync.GroupOfflineSyncManager
import com.bcm.messenger.chats.group.logic.viewmodel.GroupViewModel
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.event.ServiceConnectEvent
import com.bcm.messenger.common.grouprepository.events.*
import com.bcm.messenger.common.grouprepository.manager.GroupInfoDataManager
import com.bcm.messenger.common.grouprepository.manager.GroupLiveInfoManager
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager
import com.bcm.messenger.common.grouprepository.manager.UserDataManager
import com.bcm.messenger.common.grouprepository.model.AmeGroupMemberChanged
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.grouprepository.modeltransform.GroupMessageTransform
import com.bcm.messenger.common.grouprepository.room.entity.GroupInfo
import com.bcm.messenger.common.grouprepository.room.entity.GroupMessage
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.utils.AmePushProcess
import com.bcm.messenger.common.crypto.encrypt.GroupMessageEncryptUtils
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.server.ConnectState
import com.bcm.messenger.common.server.IServerConnectStateListener
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.AmeURLUtil
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.foreground.AppForeground
import com.bcm.messenger.utility.logger.ALog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * group message logic
 * Created by bcm.social.01 on 2018/9/4.
 */
@SuppressLint("CheckResult")
object GroupMessageLogic : GroupOfflineSyncManager.OfflineSyncCallback
        , AppForeground.IForegroundEvent
        , IServerConnectStateListener {
    private const val TAG = "GroupMessageLogic"

    private var inited = false
    private var offlineMessageSyncing = false
    private val offlineSyncManager = GroupOfflineSyncManager(this)
    private var ackReporter = GroupAckReporter()
    private val failCounter = GroupOfflineDecryptFailCounter()
    private val lastMidCache = ConcurrentHashMap<Long, Long>()

    val messageSender = MessageSender()

    fun init() {
        inited = true
        offlineMessageSyncing = false
        offlineSyncManager.init()
        syncOfflineMessage()
        messageSender.resetPendingMessageState()

        EventBus.getDefault().register(this)
        AppForeground.listener.addListener(this)
        AmeModuleCenter.serverDaemon().addConnectionListener(this)
    }

    fun unInit() {
        inited = false
        offlineSyncManager.unInit()
        EventBus.getDefault().unregister(this)
        AppForeground.listener.removeListener(this)
        AmeModuleCenter.serverDaemon().removeConnectionListener(this)
    }

    override fun onServerConnectionChanged(accountContext: AccountContext, newState: ConnectState) {
        if (AMELogin.isLogin && newState == ConnectState.CONNECTED && inited) {
            syncOfflineMessage()
            offlineSyncManager.resetDelay()
        }

        if (newState != ConnectState.CONNECTED) {
            ackReporter.resetSyncState()
        }
    }

    override fun onForegroundChanged(isForeground: Boolean) {
        if (AMELogin.isLogin && isForeground && inited) {
            offlineSyncManager.sync()
        }
    }

    @Subscribe
    fun onEvent(e: GroupMessageEvent) {
        val groupInfo = GroupLogic.getGroupInfo(e.message.gid)
        if (groupInfo == null) {
            ALog.w(TAG, "receiveGroupMessage, but not have groupInfo, ignore gid: ${e.message.gid}")
            return
        }
        val isAtMe = e.message?.extContent?.isAtAll == true || e.message?.extContent?.atList?.contains(e.accountContext.uid) == true
        if (e.message.type.toLong() != AmeGroupMessage.DECRYPT_FAIL) {
            val bcmData = AmePushProcess.BcmData(AmePushProcess.BcmNotify(AmePushProcess.GROUP_NOTIFY, null, AmePushProcess.GroupNotifyData(e.message.serverIndex, e.message.gid, isAtMe), null, null))
            AmePushProcess.processPush(e.accountContext, bcmData)
        } else {
            ALog.e(TAG, "receive group message and DECRYPT_FAIL---gid " + e.message.gid)
        }

        handleReceiveGroupMessage(e.message)
    }

    @Subscribe
    fun onEvent(e: GroupMessageMissedEvent) {
        ALog.i(TAG, "GroupMessageMissedEvent ${e.gid}")
        GroupLogic.queryGroupInfo(e.gid)
                .observeOn(AmeDispatcher.ioScheduler)
                .doOnError {
                    ALog.e(TAG, "GroupMessageMissedEvent sync failed")
                }
                .subscribe()
    }

    @Subscribe
    fun onEvent(e: GroupKeyRefreshStartEvent) {
        ALog.i(TAG, "GroupKeyRefreshStartEvent coming ${e.gid} ${e.mid}")
        GroupLogic.uploadGroupKeys(e.gid, e.mid, e.mode)
    }

    @Subscribe
    fun onEvent(e: GroupKeyRefreshCompleteEvent) {
        ALog.i(TAG, "GroupKeyRefreshCompleteEvent sync key coming")
        GroupKeyRotate.rotateFinished(e.gid)

        GroupLogic.syncGroupKeyList(e.gid, listOf(e.version))
                .doOnError {
                    ALog.e(TAG, "GroupKeyRefreshCompleteEvent", it)
                }
                .subscribe {
                    ALog.i(TAG, "GroupKeyRefreshCompleteEvent succeed")
                }
    }

    @SuppressLint("CheckResult")
    private fun syncOfflineMessage() {
        if (offlineMessageSyncing) {
            return
        }

        val syncingUid = AMELogin.uid

        offlineMessageSyncing = true
        GroupManagerCore.offlineMessageState
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe({
                    ALog.i(TAG, "offline message state ${it.msg}, ${it.code}")
                    if (syncingUid != AMELogin.uid) {
                        offlineMessageSyncing = false
                        return@subscribe
                    }

                    if (it.isSuccess && it.data?.groups?.size ?: 0 > 0) {
                        val refreshList = ArrayList<Long>()
                        val myGroupList = it.data.groups?.map { it.gid } ?: listOf()
                        GroupLogic.updateMyGroupList(myGroupList)

                        for (state in it.data.groups ?: listOf()) {
                            val groupInfo = GroupLogic.getGroupInfo(state.gid)
                            if (groupInfo == null
                                    || (state.lastAckMid != state.lastMid)) {
                                refreshList.add(state.gid)
                            } else if (groupInfo.role != AmeGroupMemberInfo.VISITOR) {
                                if (TextUtils.isEmpty(groupInfo.key) && TextUtils.isEmpty(groupInfo.channelKey)) {
                                    refreshList.add(state.gid)
                                } else if (TextUtils.isEmpty(groupInfo.infoSecret)) {
                                    refreshList.add(state.gid)
                                }
                            }
                        }

                        GroupLogic.queryGroupInfoByGids(refreshList) { succeed ->
                            if (syncingUid != AMELogin.uid) {
                                offlineMessageSyncing = false
                                return@queryGroupInfoByGids
                            }

                            if (succeed) {
                                offlineSyncManager.syncJoinReq(myGroupList)
                                ackReporter.groupMessageSyncing(myGroupList)

                                val syncIgnoreList = it.data.groups?.filter { state ->
                                    !syncOfflineMessage(
                                            state.gid, state.lastMid, state.lastAckMid)
                                }?.map { state -> state.gid }

                                if (null != syncIgnoreList) {
                                    checkGroupKeyValidState(syncIgnoreList)
                                }
                                //check sync running
                                offlineSyncManager.sync()
                            }
                            offlineMessageSyncing = false
                        }
                    } else {
                        offlineMessageSyncing = false
                    }
                }, {
                    offlineMessageSyncing = false
                    ALog.e(TAG, "syncOfflineMessage", it)
                })
    }


    fun syncJoinReqMessage(groupId: Long) {
        offlineSyncManager.syncJoinReq(groupId)
    }


    override fun onOfflineMessageFetched(gid: Long, messageList: List<GroupMessageEntity>): Boolean {
        return try {
            val fetchList = handleOnFetchMessage(messageList, gid) ?: return false
            val fetchDetailList = fetchList.mapNotNull {
                if (it.is_confirm == GroupMessage.CONFIRM_MESSAGE) {
                    GroupMessageTransform.transformToModel(it)
                } else {
                    null
                }
            }
            if (!fetchDetailList.isNullOrEmpty()) {
                EventBus.getDefault().post(MessageEvent(gid, fetchDetailList.last().indexId, MessageEvent.EventType.FETCH_MESSAGE_SUCCESS, fetchDetailList))
            }
            true
        } catch (ex: Exception) {
            ALog.e(TAG, "offlineSync error", ex)
            false
        }
    }

    override fun onOfflineMessageSyncFinished(gid: Long) {
        ALog.i(TAG, "sync $gid offline message finish")

        ackReporter.groupMessageSyncReady(gid)

        GroupLogic.refreshGroupAvatar(gid)
        GroupLogic.getModel(gid)?.checkSync()

        Repository.getThreadRepo().updateByNewGroup(gid)
        checkGroupKeyValidState(listOf(gid))

        failCounter.finishCounter(gid)

        if (GroupLogic.isGroupMemberDirty(gid)) {
            queryGroupInfo(gid) { _, _, _ -> }
        }
    }

    override fun onJoinRequestMessageSyncFinished(gid: Long) {
        if (GroupLogic.isCurrentModel(gid)) {
            AmeDispatcher.mainThread.dispatch {
                EventBus.getDefault().post(GroupViewModel.JoinRequestListChanged(gid))
            }
        }
    }

    private fun handleReceiveGroupMessage(message: AmeGroupMessageDetail) {
        val groupMessage = GroupMessageTransform.transformToEntity(message)
        doControlMessage(message.gid, message.message.content)
        val result: MessageDataManager.InsertMessageResult = MessageDataManager.insertReceiveMessage(groupMessage)
        when (result.resultCode) {
            MessageDataManager.InsertMessageResult.REPLAY_MESSAGE -> {
                ALog.e(TAG, "message replay " + message.serverIndex)
            }
            MessageDataManager.InsertMessageResult.UPDATE_SUCCESS -> {
                ALog.e(TAG, "message update " + message.serverIndex)
            }
            else -> {
                if (groupMessage.is_confirm == GroupMessage.CONFIRM_MESSAGE) {
                    val updateMessageDetail = GroupMessageTransform.transformToModel(groupMessage)
                    updateMessageDetail?.let {
                        EventBus.getDefault().post(MessageEvent(message.gid, result.indexId, MessageEvent.EventType.RECEIVE_MESSAGE_INSERT, listOf(it)))
                    }
                }
            }
        }
    }

    private fun doControlMessage(gid: Long, message: AmeGroupMessage.Content) {
        when (message) {
            is AmeGroupMessage.LiveContent -> {
                GroupLiveInfoManager.getInstance().handleReceiveLiveMessage(gid, message, false)
            }
            is AmeGroupMessage.PinContent -> {
                GroupLogic.updateGroupInfoPinMid(gid, message.mid)
            }
            is AmeGroupMessage.GroupShareSettingRefreshContent -> {
                EventBus.getDefault().post(GroupShareSettingRefreshEvent(gid, message.shareCode, message.shareSetting, message.shareSettingSign, message.shareAndOwnerConfirmSign, message.needConfirm))
            }
        }
    }

    private fun handleOnFetchMessage(messages: List<GroupMessageEntity>, gid: Long): List<GroupMessage>? {
        val groupInfo = GroupInfoDataManager.queryOneGroupInfo(gid) ?: return null
        val fetchList = mutableListOf<GroupMessage>()
        var memberListChanged = false

        var decryptFailCount = 0
        for (msg in messages) {
            val fetchGroupMessage = if (msg.type == 1 || msg.type == 2) {
                if (msg.status == GroupMessageEntity.STATUS_RECALLED) {
                    handleRecallMessage(msg, groupInfo)
                } else {
                    handleChatMessage(msg, groupInfo)
                }
            } else if (msg.type == 3) {
                val groupMessage = GroupMessage()
                groupMessage.is_confirm = GroupMessage.CONFIRM_BUT_NOT_SHOW
                groupMessage.gid = gid
                groupMessage.mid = msg.mid
                groupMessage.create_time = msg.createTime
                groupMessage.from_uid = msg.getFinalSource(groupInfo)
                groupMessage.text = msg.text
                MessageDataManager.insertFetchedMessages(groupMessage)
                groupMessage
            } else if (msg.type == 4) {
                val memberChangeMessage = Gson().fromJson<AmeGroupMemberChanged.MemberChangeMessage>(msg.text, object : TypeToken<AmeGroupMemberChanged.MemberChangeMessage>() {
                }.type) ?: continue

                val changed = AmeGroupMemberChanged(gid, msg.mid)
                changed.action = memberChangeMessage.action
                changed.fromUid = msg.getFinalSource(groupInfo)
                changed.createTime = msg.createTime
                val list = ArrayList<AmeGroupMemberInfo>()
                if (memberChangeMessage.members != null) {
                    for (member in memberChangeMessage.members!!) {
                        if (member.uid != null && member.role != null) {
                            val info = AmeGroupMemberInfo()
                            info.uid = Address.fromSerialized(member.uid!!)
                            info.role = member.role
                            info.gid = gid
                            if (changed.action == AmeGroupMemberChanged.LEAVE) {
                                info.role = UserDataManager.queryGroupMemberRole(info.gid, member.uid
                                        ?: "")
                            }
                            list.add(info)
                        }
                    }
                }

                changed.memberList = list
                val messageDetail = changed.toDetail()
                val groupMessage = GroupMessageTransform.transformToEntity(messageDetail)
                groupMessage.read_state = GroupMessage.READ_STATE_READ
                groupMessage.content_type = messageDetail.message.type.toInt()

                if (changed.action == AmeGroupMemberChanged.LEAVE || changed.action == AmeGroupMemberChanged.JOIN) {
                    if (changed.action == AmeGroupMemberChanged.LEAVE) {
                        if (AMELogin.uid != groupInfo?.owner && !changed.isMyLeave()) {
                            groupMessage.is_confirm = GroupMessage.CONFIRM_BUT_NOT_SHOW
                        }
                    }
                    memberListChanged = true
                }
                MessageDataManager.insertFetchedMessages(groupMessage)
                groupMessage
            } else if (msg.type == 6) {
                val groupMessage = GroupMessage()
                groupMessage.gid = gid
                groupMessage.mid = msg.mid
                groupMessage.is_confirm = GroupMessage.CONFIRM_BUT_NOT_SHOW
                groupMessage.create_time = msg.createTime
                groupMessage.from_uid = msg.getFinalSource(groupInfo)
                MessageDataManager.insertFetchedMessages(groupMessage)
                groupMessage

            } else {
                val groupMessage = GroupMessage()
                groupMessage.gid = gid
                groupMessage.mid = msg.mid
                groupMessage.is_confirm = GroupMessage.CONFIRM_BUT_NOT_SHOW
                groupMessage.create_time = msg.createTime
                groupMessage.from_uid = msg.getFinalSource(groupInfo)
                MessageDataManager.insertFetchedMessages(groupMessage)
                groupMessage
            }

            if (null != fetchGroupMessage) {
                if (fetchGroupMessage.content_type == AmeGroupMessage.DECRYPT_FAIL.toInt()) {
                    ++decryptFailCount
                }
                fetchList.add(fetchGroupMessage)
            }
        }

        if (decryptFailCount > 0) {
            failCounter.updateFailCount(gid, decryptFailCount)
        }

        if (memberListChanged) {
            GroupLogic.setGroupMemberDirty(gid)
        }

        return fetchList.sortedByDescending { it.mid }
    }


    private fun handleRecallMessage(msg: GroupMessageEntity, groupInfo: GroupInfo): GroupMessage {
        val gid = groupInfo.gid
        val senderId = msg.getFinalSource(groupInfo)

        val detail = AmeGroupMessageDetail()
        detail.gid = gid
        val content = AmeGroupMessage.SystemContent(AmeGroupMessage.SystemContent.TIP_RECALL, senderId, ArrayList(), "")
        val message = AmeGroupMessage(AmeGroupMessage.SYSTEM_INFO, content)
        detail.serverIndex = msg.mid
        detail.senderId = senderId
        detail.sendTime = msg.createTime
        detail.message = message
        detail.type = GroupMessage.CHAT_MESSAGE
        detail.isSendByMe = MessageDataManager.isMe(detail.senderId, detail.serverIndex, detail.gid)
        detail.sendState = if (detail.isSendByMe) AmeGroupMessageDetail.SendState.SEND_SUCCESS else AmeGroupMessageDetail.SendState.RECEIVE_SUCCESS

        val groupMessage = GroupMessageTransform.transformToEntity(detail)
        groupMessage.content_type = message.type.toInt()
        groupMessage.is_confirm = GroupMessage.CONFIRM_MESSAGE

        MessageDataManager.insertFetchedMessages(groupMessage)

        return groupMessage
    }

    private fun handleChatMessage(msg: GroupMessageEntity, groupInfo: GroupInfo): GroupMessage? {
        val gid = groupInfo.gid

        val detail = AmeGroupMessageDetail()
        detail.senderId = msg.getFinalSource(groupInfo)
        if (detail.senderId == AMELogin.uid) {
            return null
        }

        val decryptBean = GroupMessageEncryptUtils.decapsulateMessage(msg.text)
        val decryptText = if (decryptBean == null) {
            msg.text
        } else {
            val keyParam = GroupInfoDataManager.queryGroupKeyParam(groupInfo.gid, decryptBean.keyVersion)
            GroupMessageEncryptUtils.decryptMessageProcess(keyParam, decryptBean)
        }

        val groupMessage = if (decryptText != null) {
            var message = AmeGroupMessage.messageFromJson(decryptText)

            if (message.isText() && AmeURLUtil.isLegitimateUrl((message.content as AmeGroupMessage.TextContent).text)) {
                val content = AmeGroupMessage.LinkContent((message.content as AmeGroupMessage.TextContent).text, "")
                message = AmeGroupMessage(AmeGroupMessage.LINK, content)
            }

            detail.gid = gid
            detail.serverIndex = msg.mid
            detail.sendTime = msg.createTime
            detail.message = message
            detail.type = when (msg.type) {
                1 -> GroupMessage.CHAT_MESSAGE
                2 -> GroupMessage.PUB_MESSAGE
                else -> GroupMessage.CHAT_MESSAGE
            }

            detail.keyVersion = decryptBean?.keyVersion ?: 0
            detail.isSendByMe = MessageDataManager.isMe(detail.senderId, detail.serverIndex, detail.gid)
            detail.sendState = if (detail.isSendByMe) AmeGroupMessageDetail.SendState.SEND_SUCCESS else AmeGroupMessageDetail.SendState.RECEIVE_SUCCESS
            val ext = AmeGroupMessageDetail.ExtensionContent();
            ext.atAll = if (msg.atAll != null && msg.atAll) {
                1
            } else {
                0
            }

            ext.atList = msg.atList
            detail.extContent = ext

            if (detail.message.isLiveMessage()) {
                val liveMessage = message.content as AmeGroupMessage.LiveContent
                GroupLiveInfoManager.getInstance().handleReceiveLiveMessage(gid, liveMessage, true)
            }
            if (detail.message.isPin()) {
                val data = detail.message.content as AmeGroupMessage.PinContent
                GroupLogic.updateGroupInfoPinMid(detail.gid, data.mid)
            }

            val groupMessage = GroupMessageTransform.transformToEntity(detail)
            groupMessage.content_type = message.type.toInt()
            groupMessage.is_confirm = GroupMessage.CONFIRM_MESSAGE

            groupMessage
        } else {
            detail.apply {
                this.gid = gid
                sendTime = AmeTimeUtil.getMessageSendTime()
                sendState = AmeGroupMessageDetail.SendState.SEND_FAILED
                senderId = AMELogin.uid
                isSendByMe = true
                attachmentUri = ""
                extContent = null
                isRead = true
                message = AmeGroupMessage(AmeGroupMessage.DECRYPT_FAIL, AmeGroupMessage.TextContent(""))
            }
            val group = GroupMessageTransform.transformToEntity(detail)
            group.is_confirm = GroupMessage.CONFIRM_BUT_NOT_SHOW
            group
        }

        MessageDataManager.insertFetchedMessages(groupMessage)

        doControlMessage(gid, detail.message.content)

        return groupMessage
    }

    private fun isCurrentLive(sendTime: Long, duration: Long): Boolean {
        if (sendTime + duration > AmeTimeUtil.serverTimeMillis()) {
            return true
        }
        return false
    }


    fun syncOfflineMessage(gid: Long, lastMid: Long, lastAck: Long): Boolean {
        val localMax = MessageDataManager.queryMixMid(gid)
        var from = lastAck + 1

        ALog.i(TAG, "syncOfflineMessage start $gid last:$lastMid  ack:$lastAck, localmax:$localMax")

        // If the last ack is smaller than the id of the local record, it means that some records are not read
        if (from < localMax) {
            val list = MessageDataManager.getExistMessageByMids(gid, from, localMax).sorted()

            if (list.isNotEmpty() && list.first() == from) {
                //found next discontinuous mid
                var pre = from - 1
                for (i in list) {
                    if (i - pre != 1L) {
                        pre += 1
                        ALog.i(TAG, "adjust gid:$gid $pre")
                        break
                    }
                    pre = i
                }
                from = pre
            }
        }

        if (from <= lastMid && lastMid > lastMidCache[gid] ?: 0) {
            failCounter.updateFailCount(gid, 0)
            lastMidCache[gid] = lastMid
            MessageDataManager.insertFetchingMessages(gid, from, lastMid)
            offlineSyncManager.sync(gid, from, lastMid)
            return true
        } else if (localMax < lastAck) {
            GroupLogic.checkLastGroupKeyValid(gid)
            ackReporter.groupMessageSyncReady(gid)
            return true
        }
        ackReporter.groupMessageSyncReady(gid)
        return false
    }

    @SuppressLint("CheckResult")
    fun readAllMessage(groupId: Long, threadId: Long, lastSeen: Long) {
        Observable.create(ObservableOnSubscribe<Void> {
            ALog.d(TAG, "readAllMessage threadId: $threadId, groupId: $groupId")
            Repository.getThreadRepo().setGroupRead(threadId, groupId, lastSeen)
            val lastAckMid = MessageDataManager.queryMixMid(groupId)
            if (lastAckMid > 0) {
                reportAckForGroup(groupId, MessageDataManager.queryMixMid(groupId))
            }
            it.onComplete()
        }).subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe({}, {
                    ALog.e(TAG, it)
                })
    }

    @SuppressLint("CheckResult")
    private fun reportAckForGroup(gid: Long, maxMid: Long) {
        ackReporter.reportAck(gid, maxMid)
    }

    fun systemNotice(groupId: Long, content: AmeGroupMessage.SystemContent, read: Boolean = true, visible: Boolean = true): Long {
        ALog.d(TAG, "systemNotice groupId: $groupId read: $read")
        return MessageDataManager.systemNotice(groupId, content, read, visible)
    }

    fun doOnLogin() {
        offlineSyncManager.doOnLogin()
        syncOfflineMessage()
    }
}