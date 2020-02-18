package com.bcm.messenger.chats.group.logic

import android.annotation.SuppressLint
import android.text.TextUtils
import com.bcm.messenger.chats.group.core.GroupManagerCore
import com.bcm.messenger.chats.group.core.group.GroupMessageEntity
import com.bcm.messenger.chats.group.logic.sync.GroupOfflineDecryptFailCounter
import com.bcm.messenger.chats.group.logic.sync.GroupOfflineSyncManager
import com.bcm.messenger.chats.group.logic.viewmodel.GroupViewModel
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.crypto.encrypt.GroupMessageEncryptUtils
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.events.GroupMessageEvent
import com.bcm.messenger.common.grouprepository.events.GroupMessageMissedEvent
import com.bcm.messenger.common.grouprepository.events.GroupShareSettingRefreshEvent
import com.bcm.messenger.common.grouprepository.events.MessageEvent
import com.bcm.messenger.common.grouprepository.manager.GroupInfoDataManager
import com.bcm.messenger.common.grouprepository.manager.GroupLiveInfoManager
import com.bcm.messenger.common.grouprepository.manager.GroupMemberManager
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager
import com.bcm.messenger.common.grouprepository.model.AmeGroupMemberChanged
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.grouprepository.modeltransform.GroupMessageTransform
import com.bcm.messenger.common.grouprepository.room.entity.GroupInfo
import com.bcm.messenger.common.grouprepository.room.entity.GroupMessage
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.server.ConnectState
import com.bcm.messenger.common.server.IServerConnectStateListener
import com.bcm.messenger.common.ui.grouprepository.events.GroupKeyRefreshCompleteEvent
import com.bcm.messenger.common.ui.grouprepository.events.GroupKeyRefreshStartEvent
import com.bcm.messenger.common.utils.AccountContextMap
import com.bcm.messenger.common.utils.AmePushProcess
import com.bcm.messenger.common.utils.log.ACLog
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
object GroupMessageLogic : AccountContextMap<GroupMessageLogic.GroupMessageLogicImpl>({
    GroupMessageLogicImpl(it)
}) {
    private const val TAG = "GroupMessageLogic"

    class GroupMessageLogicImpl(private val accountContext: AccountContext) : GroupOfflineSyncManager.OfflineSyncCallback
            , AppForeground.IForegroundEvent
            , IServerConnectStateListener {
        private var inited = false
        private var offlineMessageSyncing = false
        private val offlineSyncManager = GroupOfflineSyncManager(accountContext, this)
        private var ackReporter = GroupAckReporter(accountContext)
        private val failCounter = GroupOfflineDecryptFailCounter(accountContext)
        private val lastMidCache = ConcurrentHashMap<Long, Long>()

        val messageSender = MessageSender(accountContext)

        fun init() {
            inited = true
            offlineMessageSyncing = false
            offlineSyncManager.init()
            syncOfflineMessage()
            messageSender.resetPendingMessageState()

            EventBus.getDefault().register(this)
            AppForeground.listener.addListener(this)
            AmeModuleCenter.serverDaemon(accountContext).addConnectionListener(this)
        }

        fun unInit() {
            inited = false
            offlineSyncManager.unInit()
            EventBus.getDefault().unregister(this)
            AppForeground.listener.removeListener(this)
            AmeModuleCenter.serverDaemon(accountContext).removeConnectionListener(this)
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
            if (e.accountContext != accountContext) {
                ALog.d(TAG, "GroupMessageEvent this message is not for me")
                return
            }

            val groupInfo = GroupLogic.get(accountContext).getGroupInfo(e.message.gid)
            if (groupInfo == null) {
                ACLog.w(accountContext, TAG, "receiveGroupMessage, but not have groupInfo, ignore gid: ${e.message.gid}")
                return
            }
            val isAtMe = e.message.extContent?.isAtAll == true || e.message.extContent?.atList?.contains(e.accountContext.uid) == true
            if (e.message.type.toLong() != AmeGroupMessage.DECRYPT_FAIL) {
                val bcmData = AmePushProcess.BcmData(AmePushProcess.BcmNotify(AmePushProcess.GROUP_NOTIFY, 0, null, AmePushProcess.GroupNotifyData(e.message.serverIndex, e.message.gid, isAtMe), null, null))
                AmePushProcess.processPush(e.accountContext, bcmData)
            } else {
                ACLog.e(accountContext, TAG, "receive group message and DECRYPT_FAIL---gid " + e.message.gid)
            }

            handleReceiveGroupMessage(e.message)
        }

        @Subscribe
        fun onEvent(e: GroupMessageMissedEvent) {
            if (e.accountContext != accountContext) {
                ALog.d(TAG, "GroupMessageMissedEvent this message is not for me")
                return
            }

            ACLog.i(accountContext, TAG, "GroupMessageMissedEvent ${e.gid}")
            GroupLogic.get(accountContext).queryGroupInfo(e.gid)
                    .observeOn(AmeDispatcher.ioScheduler)
                    .doOnError {
                        ACLog.e(accountContext, TAG, "GroupMessageMissedEvent sync failed")
                    }
                    .subscribe()
        }

        @Subscribe
        fun onEvent(e: GroupKeyRefreshStartEvent) {
            if (e.accountContext != accountContext) {
                ALog.d(TAG, "GroupKeyRefreshStartEvent this message is not for me")
                return
            }

            if (e.accountContext != accountContext) {
                ALog.d(TAG, "GroupKeyRefreshStartEvent this message is not for me")
                return
            }
            ACLog.i(accountContext, TAG, "GroupKeyRefreshStartEvent coming ${e.gid} ${e.mid}")
            GroupLogic.get(accountContext).uploadGroupKeys(e.gid, e.mid, e.mode)
        }

        @Subscribe
        fun onEvent(e: GroupKeyRefreshCompleteEvent) {
            if (e.accountContext != accountContext) {
                ALog.d(TAG, "GroupKeyRefreshStartEvent this message is not for me")
                return
            }

            ACLog.i(accountContext, TAG, "GroupKeyRefreshCompleteEvent sync key coming")
            GroupLogic.get(accountContext).getKeyRotate().rotateFinished(e.gid)

            GroupLogic.get(accountContext).syncGroupKeyList(e.gid, listOf(e.version))
                    .doOnError {
                        ACLog.e(accountContext, TAG, "GroupKeyRefreshCompleteEvent", it)
                    }
                    .subscribe {
                        ACLog.i(accountContext, TAG, "GroupKeyRefreshCompleteEvent succeed")
                    }
        }

        @SuppressLint("CheckResult")
        private fun syncOfflineMessage() {
            if (offlineMessageSyncing) {
                return
            }

            offlineMessageSyncing = true
            GroupManagerCore.offlineMessageState(accountContext)
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .subscribe({
                        ACLog.i(accountContext, TAG, "offline message state ${it.msg}, ${it.code}")
                        if (!accountContext.isLogin) {
                            offlineMessageSyncing = false
                            return@subscribe
                        }

                        if (it.isSuccess && it.data?.groups?.size ?: 0 > 0) {
                            val refreshList = ArrayList<Long>()
                            val myGroupList = it.data.groups?.map { it.gid } ?: listOf()
                            GroupLogic.get(accountContext).updateMyGroupList(myGroupList)

                            for (state in it.data.groups ?: listOf()) {
                                val groupInfo = GroupLogic.get(accountContext).getGroupInfo(state.gid)
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

                            GroupLogic.get(accountContext).queryGroupInfoByGids(refreshList) { succeed ->
                                if (!accountContext.isLogin) {
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
                                        GroupLogic.get(accountContext).checkGroupKeyValidState(syncIgnoreList)
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
                        ACLog.e(accountContext, TAG, "syncOfflineMessage", it)
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
                    EventBus.getDefault().post(MessageEvent(accountContext, gid, fetchDetailList.last().indexId, MessageEvent.EventType.FETCH_MESSAGE_SUCCESS, fetchDetailList))
                }
                true
            } catch (ex: Exception) {
                ACLog.e(accountContext, TAG, "offlineSync error", ex)
                false
            }
        }

        override fun onOfflineMessageSyncFinished(gid: Long) {
            ACLog.i(accountContext, TAG, "sync $gid offline message finish")

            ackReporter.groupMessageSyncReady(gid)

            GroupLogic.get(accountContext).refreshGroupAvatar(gid)
            GroupLogic.get(accountContext).getModel(gid)?.checkSync()

            Repository.getThreadRepo(accountContext)?.updateByNewGroup(gid)
            GroupLogic.get(accountContext).checkGroupKeyValidState(listOf(gid))

            failCounter.finishCounter(gid)

            if (GroupLogic.get(accountContext).isGroupMemberDirty(gid)) {
                GroupLogic.get(accountContext).queryGroupInfo(gid, null)
            }
        }

        override fun onJoinRequestMessageSyncFinished(gid: Long) {
            if (GroupLogic.get(accountContext).isCurrentModel(gid)) {
                AmeDispatcher.mainThread.dispatch {
                    EventBus.getDefault().post(GroupViewModel.JoinRequestListChanged(gid))
                }
            }
        }

        private fun handleReceiveGroupMessage(message: AmeGroupMessageDetail) {
            val groupMessage = GroupMessageTransform.transformToEntity(message)
            doControlMessage(message.gid, message.message.content)
            val result: MessageDataManager.InsertMessageResult = MessageDataManager.insertReceiveMessage(accountContext, groupMessage)
                    ?: return
            when (result.resultCode) {
                MessageDataManager.InsertMessageResult.REPLAY_MESSAGE -> {
                    ACLog.e(accountContext, TAG, "message replay " + message.serverIndex)
                }
                MessageDataManager.InsertMessageResult.UPDATE_SUCCESS -> {
                    ACLog.e(accountContext, TAG, "message update " + message.serverIndex)
                }
                else -> {
                    if (groupMessage.is_confirm == GroupMessage.CONFIRM_MESSAGE) {
                        val updateMessageDetail = GroupMessageTransform.transformToModel(groupMessage)
                        updateMessageDetail?.let {
                            EventBus.getDefault().post(MessageEvent(accountContext, message.gid, result.indexId, MessageEvent.EventType.RECEIVE_MESSAGE_INSERT, listOf(it)))
                        }
                    }
                }
            }
        }

        private fun doControlMessage(gid: Long, message: AmeGroupMessage.Content) {
            when (message) {
                is AmeGroupMessage.LiveContent -> {
                    GroupLiveInfoManager.get(accountContext).handleReceiveLiveMessage(gid, message, false)
                }
                is AmeGroupMessage.PinContent -> {
                    GroupLogic.get(accountContext).updateGroupInfoPinMid(gid, message.mid)
                }
                is AmeGroupMessage.GroupShareSettingRefreshContent -> {
                    EventBus.getDefault().post(GroupShareSettingRefreshEvent(gid, message.shareCode, message.shareSetting, message.shareSettingSign, message.shareAndOwnerConfirmSign, message.needConfirm, message.ekey))
                }
            }
        }

        private fun handleOnFetchMessage(messages: List<GroupMessageEntity>, gid: Long): List<GroupMessage>? {
            val groupInfo = GroupInfoDataManager.queryOneGroupInfo(accountContext, gid)
                    ?: return null
            val fetchList = mutableListOf<GroupMessage>()
            var memberListChanged = false

            var decryptFailCount = 0
            var decryptFailLastMid = 0L
            var decryptFailLastTime = 0L
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
                    groupMessage
                } else if (msg.type == 4) {
                    val memberChangeMessage = Gson().fromJson<AmeGroupMemberChanged.MemberChangeMessage>(msg.text, object : TypeToken<AmeGroupMemberChanged.MemberChangeMessage>() {
                    }.type) ?: continue

                    val changed = AmeGroupMemberChanged(accountContext, gid, msg.mid)
                    changed.action = memberChangeMessage.action
                    changed.fromUid = msg.getFinalSource(groupInfo)
                    changed.createTime = msg.createTime
                    val list = ArrayList<AmeGroupMemberInfo>()
                    if (memberChangeMessage.members != null) {
                        for (member in memberChangeMessage.members!!) {
                            if (member.uid != null && member.role != null) {
                                val info = AmeGroupMemberInfo()
                                info.uid = member.uid ?: continue
                                info.role = member.role
                                info.gid = gid
                                if (changed.action == AmeGroupMemberChanged.LEAVE) {
                                    info.role = GroupMemberManager.queryGroupMemberRole(accountContext, info.gid, member.uid
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
                            if (accountContext.uid != groupInfo.owner && !changed.contains(accountContext.uid)) {
                                groupMessage.is_confirm = GroupMessage.CONFIRM_BUT_NOT_SHOW
                            }
                        }
                        memberListChanged = true
                    }

                    groupMessage
                } else if (msg.type == 6) {
                    val groupMessage = GroupMessage()
                    groupMessage.gid = gid
                    groupMessage.mid = msg.mid
                    groupMessage.is_confirm = GroupMessage.CONFIRM_BUT_NOT_SHOW
                    groupMessage.create_time = msg.createTime
                    groupMessage.from_uid = msg.getFinalSource(groupInfo)
                    groupMessage

                } else {
                    val groupMessage = GroupMessage()
                    groupMessage.gid = gid
                    groupMessage.mid = msg.mid
                    groupMessage.is_confirm = GroupMessage.CONFIRM_BUT_NOT_SHOW
                    groupMessage.create_time = msg.createTime
                    groupMessage.from_uid = msg.getFinalSource(groupInfo)
                    groupMessage
                }

                MessageDataManager.insertFetchedMessages(accountContext, fetchGroupMessage)
                if (fetchGroupMessage.content_type == AmeGroupMessage.DECRYPT_FAIL.toInt()
                        && fetchGroupMessage.send_or_receive == GroupMessage.RECEIVE) {
                    ++decryptFailCount
                    decryptFailLastMid = msg.mid
                    decryptFailLastTime = msg.createTime
                }

                fetchList.add(fetchGroupMessage)
            }

            if (decryptFailCount > 0) {
                failCounter.updateFailCount(gid, decryptFailCount, decryptFailLastMid, decryptFailLastTime)
            }

            if (memberListChanged) {
                GroupLogic.get(accountContext).setGroupMemberDirty(gid)
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
            detail.isSendByMe = detail.senderId == accountContext.uid
            detail.sendState = if (detail.isSendByMe) AmeGroupMessageDetail.SendState.SEND_SUCCESS else AmeGroupMessageDetail.SendState.RECEIVE_SUCCESS

            val groupMessage = GroupMessageTransform.transformToEntity(detail)
            groupMessage.content_type = message.type.toInt()
            groupMessage.is_confirm = GroupMessage.CONFIRM_MESSAGE

            return groupMessage
        }

        private fun handleChatMessage(msg: GroupMessageEntity, groupInfo: GroupInfo): GroupMessage {
            val gid = groupInfo.gid

            val detail = AmeGroupMessageDetail()
            detail.senderId = msg.getFinalSource(groupInfo)
            if (detail.senderId == accountContext.uid) {
                val exist = MessageDataManager.queryOneMessage(accountContext, gid, msg.mid, true)
                if (exist != null) {
                    return exist
                }
            }

            val decryptBean = GroupMessageEncryptUtils.decapsulateMessage(msg.text)
            val decryptText = if (decryptBean == null) {
                msg.text
            } else {
                val keyParam = GroupInfoDataManager.queryGroupKeyParam(accountContext, groupInfo.gid, decryptBean.keyVersion)
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
                detail.isSendByMe = detail.senderId == accountContext.uid
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
                    GroupLiveInfoManager.get(accountContext).handleReceiveLiveMessage(gid, liveMessage, true)
                }
                if (detail.message.isPin()) {
                    val data = detail.message.content as AmeGroupMessage.PinContent
                    GroupLogic.get(accountContext).updateGroupInfoPinMid(detail.gid, data.mid)
                }

                val groupMessage = GroupMessageTransform.transformToEntity(detail)
                groupMessage.content_type = message.type.toInt()
                groupMessage.is_confirm = GroupMessage.CONFIRM_MESSAGE

                groupMessage
            } else {
                detail.apply {
                    this.gid = gid
                    this.serverIndex = msg.mid
                    sendTime = AmeTimeUtil.serverTimeMillis()
                    sendState = AmeGroupMessageDetail.SendState.SEND_FAILED
                    senderId = detail.senderId
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
            val localMax = MessageDataManager.queryMixMid(accountContext, gid)
            var from = lastAck + 1

            ACLog.i(accountContext, TAG, "syncOfflineMessage start $gid last:$lastMid  ack:$lastAck, localmax:$localMax")

            // If the last ack is smaller than the id of the local record, it means that some records are not read
            if (from < localMax) {
                val list = MessageDataManager.getExistMessageByMids(accountContext, gid, from, localMax).sorted()

                if (list.isNotEmpty() && list.first() == from) {
                    //found next discontinuous mid
                    var pre = from - 1
                    for (i in list) {
                        if (i - pre != 1L) {
                            pre += 1
                            ACLog.i(accountContext, TAG, "adjust gid:$gid $pre")
                            break
                        }
                        pre = i
                    }
                    from = pre
                }
            }

            if (from <= lastMid && lastMid > lastMidCache[gid] ?: 0) {
                ACLog.i(accountContext, TAG, "updateFailCounthaha start $gid last:$lastMid  ack:$lastAck, from:$from")
                failCounter.updateFailCount(gid, 0, 0, 0)
                lastMidCache[gid] = lastMid
                MessageDataManager.insertFetchingMessages(accountContext, gid, from, lastMid)
                offlineSyncManager.sync(gid, from, lastMid)
                return true
            } else if (localMax < lastAck) {
                GroupLogic.get(accountContext).checkLastGroupKeyValid(gid)
                ackReporter.groupMessageSyncReady(gid)
                return true
            }
            ackReporter.groupMessageSyncReady(gid)
            return false
        }

        @SuppressLint("CheckResult")
        fun readAllMessage(groupId: Long, threadId: Long, lastSeen: Long) {
            Observable.create(ObservableOnSubscribe<Void> {
                ACLog.d(accountContext, TAG, "readAllMessage threadId: $threadId, groupId: $groupId")
                Repository.getThreadRepo(accountContext)?.setGroupRead(threadId, groupId, lastSeen)
                val lastAckMid = MessageDataManager.queryMixMid(accountContext, groupId)
                if (lastAckMid > 0) {
                    reportAckForGroup(groupId, MessageDataManager.queryMixMid(accountContext, groupId))
                }
                it.onComplete()
            }).subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .subscribe({}, {
                        ACLog.e(accountContext, TAG, it)
                    })
        }

        @SuppressLint("CheckResult")
        private fun reportAckForGroup(gid: Long, maxMid: Long) {
            ackReporter.reportAck(gid, maxMid)
        }

        fun systemNotice(groupId: Long, content: AmeGroupMessage.SystemContent, read: Boolean = true, visible: Boolean = true): Long {
            ACLog.d(accountContext, TAG, "systemNotice groupId: $groupId read: $read")
            return MessageDataManager.systemNotice(accountContext, groupId, content, read, visible)
        }

        fun doOnLogin() {
            offlineSyncManager.doOnLogin()
            syncOfflineMessage()
        }
    }

}