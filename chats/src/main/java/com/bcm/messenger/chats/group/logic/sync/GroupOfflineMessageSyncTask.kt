package com.bcm.messenger.chats.group.logic.sync

import com.bcm.messenger.chats.group.core.GroupMessageCore
import com.bcm.messenger.chats.group.core.group.GroupMessageEntity
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.chats.group.logic.secure.GroupKeyRotate
import com.bcm.messenger.common.grouprepository.manager.GroupInfoDataManager
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.common.crypto.encrypt.GroupMessageEncryptUtils
import com.bcm.messenger.utility.GsonUtils
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.proguard.NotGuard
import com.google.gson.annotations.SerializedName
import io.reactivex.Observable
import org.whispersystems.signalservice.internal.websocket.GroupMessageProtos
import java.io.Serializable
import java.util.*
import java.util.concurrent.TimeUnit

/**　
 * Created by bcm.social.01 on 2019/3/19.
 */
class GroupOfflineMessageSyncTask(val gid: Long, val fromMid: Long, val toMid: Long, var executing: Boolean = false, var isSucceed: Boolean = false, var delay: Long = 0) : Serializable {
    companion object {
        private const val serialVersionUID = 1000L
        private const val EACH_DELAY = 100L
        private const val MAX_DELAY = 10000L
    }

    fun execute(onComplete: (task: GroupOfflineMessageSyncTask, messageList: List<GroupMessageEntity>?) -> Unit) {
        ALog.i("GroupOfflineMessageSyncTask", "execute $gid delay$delay")
        executing = true

        val queryUid = AMESelfData.uid
        var stash:List<GroupMessageEntity> = listOf()
        GroupMessageCore.getMessagesWithRange(gid, fromMid, toMid)
                .subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AmeDispatcher.ioScheduler)
                .delaySubscription(getCompatibleDelay(), TimeUnit.MILLISECONDS, AmeDispatcher.ioScheduler)
                .flatMap { serverResult ->
                    if (!AMESelfData.isLogin || AMESelfData.uid != queryUid) {
                        ALog.i("GroupOfflineMessageSyncTask", "sync failed $gid  from:$fromMid to:$toMid login state changed")
                        throw Exception("Sync failed")
                    }

                    isSucceed = serverResult.isSuccess
                    if (serverResult.isSuccess) {
                        stash = serverResult.data.messages?: listOf()

                        val newGroup = GroupInfoDataManager.queryOneGroupInfo(gid)?.isNewGroup
                                ?: false

                        if (newGroup) {
                            val keyVersions = serverResult.data.messages.mapNotNull { msg ->
                                val decryptBean = GroupMessageEncryptUtils.decapsulateMessage(msg.text)
                                decryptBean?.keyVersion
                            }.toMutableSet()

                            val versionsFromMessage = keyVersionsFromKeySwitchMessage(serverResult.data.messages)
                            keyVersions.addAll(versionsFromMessage)

                            refreshGroupKeyIfNeed(serverResult.data.messages, keyVersions.toList())

                            val localVersions = GroupInfoDataManager.queryGroupKeyList(gid, keyVersions.toList()).map { it.version }
                            keyVersions.removeAll(localVersions)

                            if (keyVersions.isNotEmpty()) {
                                return@flatMap syncGroupKeyVersions(keyVersions.toSet().toList())
                            }
                        }
                        Observable.just(true)
                    }
                    else {
                        throw Exception("Sync failed")
                    }
                }
                .observeOn(AmeDispatcher.ioScheduler)
                .doOnComplete {
                    if ( AMESelfData.isLogin && AMESelfData.uid == queryUid) {
                        ALog.i("GroupOfflineMessageSyncTask", "sync succeed $gid  from:$fromMid to:$toMid succeed")
                        onComplete(this@GroupOfflineMessageSyncTask, stash)
                    } else {
                        ALog.i("GroupOfflineMessageSyncTask", "login state changed")
                    }
                }
                .doOnError {
                    delayOnFailed()
                    onComplete(this@GroupOfflineMessageSyncTask, null)
                    ALog.e("GroupOfflineMessageSyncTask", "execute gid = $gid from $fromMid  to  $toMid", it)
                }
                .subscribe()
    }

    private fun refreshGroupKeyIfNeed(messages: List<GroupMessageEntity>, keyVersionsGot:List<Long>) {
        val keyUpdateRequest = keyUpdateRequestFromMessageList(messages)
        if (null != keyUpdateRequest && !keyVersionsGot.contains(keyUpdateRequest.mid)) {
            if (keyVersionsGot.isNotEmpty()) {
                if (keyUpdateRequest.mid > Collections.max(keyVersionsGot)) {
                    GroupLogic.uploadGroupKeys(gid, keyUpdateRequest.mid, keyUpdateRequest.mode)
                } else {
                    ALog.i("GroupOfflineMessageSyncTask", "key update message expired")
                }
            } else {
                GroupLogic.uploadGroupKeys(gid, keyUpdateRequest.mid, keyUpdateRequest.mode)
            }
        } else if (null != keyUpdateRequest) {
            ALog.i("GroupOfflineMessageSyncTask", "key update message ignore ${keyUpdateRequest.mid}")
        }
    }

    private fun syncGroupKeyVersions(versionList: List<Long>): Observable<Boolean> {
        val keyVersions = versionList.toMutableList()
        return GroupLogic.syncGroupKeyList(gid, keyVersions)
                .subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AmeDispatcher.ioScheduler)
                .flatMap {
                    val newVersionList = GroupInfoDataManager.queryGroupKeyList(gid, keyVersions.toList()).map { it.version }
                    keyVersions.removeAll(newVersionList)
                    if (keyVersions.isNotEmpty()) {
                        //
                        //The encrypted version of the message is larger than the password version I can get, which means I can't solve it with the password.
                        if (newVersionList.isNotEmpty() && Collections.max(keyVersions) > Collections.max(newVersionList)
                                || newVersionList.isEmpty()) {
                            return@flatMap GroupKeyRotate.rotateGroup(gid)
                                    .observeOn(AmeDispatcher.ioScheduler)
                                    .map { refreshResult ->
                                        if (refreshResult.succeed?.contains(gid) != true) {
                                            throw Exception("refresh group key failed")
                                        }
                                        true
                                    }
                        }
                    }
                    Observable.just(true)
                }.subscribeOn(AmeDispatcher.ioScheduler)
    }

    data class KeySwitchMessage(
            @SerializedName("version")
            val version: Long):NotGuard

    private fun keyVersionsFromKeySwitchMessage(msgs: List<GroupMessageEntity>): List<Long> {
        return msgs.filter { it.type == GroupMessageProtos.GroupMsg.Type.TYPE_SWITCH_GROUP_KEYS_VALUE }
                .mapNotNull {
                    try {
                        val switch = GsonUtils.fromJson<KeySwitchMessage>(it.text, KeySwitchMessage::class.java)
                        switch.version
                    } catch (e: Throwable) {
                        ALog.e("GroupOfflineMessageSyncTask", "keyVersionsFromKeySwitchMessage", e)
                        null
                    }
                }.toSet().toList()
    }

    data class KeyUpdateMessage(
            @SerializedName("group_keys_mode")
            val mode: Int = -1,
            var mid: Long = 0):NotGuard

    private fun keyUpdateRequestFromMessageList(msgs: List<GroupMessageEntity>): KeyUpdateMessage? {
        return msgs.filter { it.type == GroupMessageProtos.GroupMsg.Type.TYPE_UPDATE_GROUP_KEYS_REQUEST_VALUE }
                .mapNotNull {
                    try {
                        val update = GsonUtils.fromJson<KeyUpdateMessage>(it.text, KeyUpdateMessage::class.java)
                        update.mid = it.mid
                        if (update.mode >= 0) {
                            update
                        } else {
                            ALog.i("GroupOfflineMessageSyncTask", "keyUpdateMessage key mode is null")
                            null
                        }
                    } catch (e: Throwable) {
                        ALog.e("GroupOfflineMessageSyncTask", "keyVersionsFromKeySwitchMessage", e)
                        null
                    }
                }.maxBy { it.mid }
    }


    private fun delayOnFailed() {
        delay = Math.max(delay + 2000, 2000)
        delay = Math.min(MAX_DELAY, delay)
    }

    fun parseFail() {
        executing = false
        delayOnFailed()
    }

    private fun getCompatibleDelay(): Long {
        if (delay > 0) {
            return if (delay <= 16) {
                delay * 1000
            } else {
                delay
            }
        }
        return EACH_DELAY
    }

    /**
     * If it doesn't work after 4 retries, just wait for the next start and reactivate the task.
     */
    fun isDead(): Boolean {
        return getCompatibleDelay() >= MAX_DELAY
    }

    fun isSame(task: GroupOfflineMessageSyncTask): Boolean {
        return gid == task.gid && fromMid == task.fromMid && toMid == task.toMid
    }
}
