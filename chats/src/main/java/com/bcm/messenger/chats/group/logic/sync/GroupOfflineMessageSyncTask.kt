package com.bcm.messenger.chats.group.logic.sync

import com.bcm.messenger.chats.group.core.GroupMessageCore
import com.bcm.messenger.chats.group.core.group.GroupMessageEntity
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.chats.group.logic.secure.GroupKeyRotate
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.grouprepository.manager.GroupInfoDataManager
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.crypto.encrypt.GroupMessageEncryptUtils
import com.bcm.messenger.common.utils.log.ACLog
import com.bcm.messenger.utility.AmeTimeUtil
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
import kotlin.math.abs

/**　
 * bcm.social.01 2019/3/19.
 */
class GroupOfflineMessageSyncTask(val gid: Long, val fromMid: Long, val toMid: Long, var executing: Boolean = false, var isSucceed: Boolean = false, var delay: Long = 0) : Serializable {
    companion object {
        private const val serialVersionUID = 1000L
        private const val EACH_DELAY = 100L
        private const val MAX_DELAY = 10000L
    }

    fun execute(accountContext: AccountContext, onComplete: (task: GroupOfflineMessageSyncTask, messageList: List<GroupMessageEntity>?) -> Unit) {
        ACLog.i(accountContext,  "GroupOfflineMessageSyncTask", "execute $gid delay$delay")
        executing = true
        
        var stash:List<GroupMessageEntity> = listOf()
        GroupMessageCore.getMessagesWithRange(accountContext, gid, fromMid, toMid)
                .subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AmeDispatcher.ioScheduler)
                .delaySubscription(getCompatibleDelay(), TimeUnit.MILLISECONDS, AmeDispatcher.ioScheduler)
                .flatMap { serverResult ->
                    if (!accountContext.isLogin) {
                        ACLog.i(accountContext,  "GroupOfflineMessageSyncTask", "sync failed $gid  from:$fromMid to:$toMid login state changed")
                        throw Exception("Sync failed")
                    }

                    isSucceed = serverResult.isSuccess
                    if (serverResult.isSuccess) {
                        stash = serverResult.data.messages?: listOf()

                        val newGroup = GroupInfoDataManager.queryOneGroupInfo(accountContext, gid)?.isNewGroup
                                ?: false

                        if (newGroup) {
                            val keyVersions = serverResult.data.messages.mapNotNull { msg ->
                                val decryptBean = GroupMessageEncryptUtils.decapsulateMessage(msg.text)
                                decryptBean?.keyVersion
                            }.toMutableSet()

                            val versionsFromMessage = keyVersionsFromKeySwitchMessage(accountContext, serverResult.data.messages)
                            keyVersions.addAll(versionsFromMessage)

                            refreshGroupKeyIfNeed(accountContext, serverResult.data.messages, keyVersions.toList())

                            val localVersions = GroupInfoDataManager.queryGroupKeyList(accountContext, gid, keyVersions.toList()).map { it.version }
                            keyVersions.removeAll(localVersions)

                            if (keyVersions.isNotEmpty()) {
                                return@flatMap syncGroupKeyVersions(accountContext, keyVersions.toSet().toList())
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
                    if ( accountContext.isLogin) {
                        ACLog.i(accountContext,  "GroupOfflineMessageSyncTask", "sync succeed $gid  from:$fromMid to:$toMid succeed")
                        onComplete(this@GroupOfflineMessageSyncTask, stash)
                    } else {
                        ACLog.i(accountContext,  "GroupOfflineMessageSyncTask", "login state changed")
                    }
                }
                .doOnError {
                    delayOnFailed()
                    onComplete(this@GroupOfflineMessageSyncTask, null)
                    ACLog.e(accountContext,  "GroupOfflineMessageSyncTask", "execute gid = $gid from $fromMid  to  $toMid", it)
                }
                .subscribe()
    }

    private fun refreshGroupKeyIfNeed(accountContext: AccountContext, messages: List<GroupMessageEntity>, keyVersionsGot:List<Long>) {
        val keyUpdateRequest = keyUpdateRequestFromMessageList(accountContext, messages)
        if (null != keyUpdateRequest && !keyVersionsGot.contains(keyUpdateRequest.mid)) {
            if (abs(keyUpdateRequest.time - AmeTimeUtil.serverTimeMillis()) < 30000) {
                ACLog.i(accountContext,  "GroupOfflineMessageSyncTask", "sync got a update key message, generate in 30s")
                return
            }

            if (keyVersionsGot.isNotEmpty()) {
                if (keyUpdateRequest.mid > Collections.max(keyVersionsGot)) {
                    GroupLogic.get(accountContext).uploadGroupKeys(gid, keyUpdateRequest.mid, keyUpdateRequest.mode)
                } else {
                    ACLog.i(accountContext,  "GroupOfflineMessageSyncTask", "key update message expired")
                }
            } else {
                GroupLogic.get(accountContext).uploadGroupKeys(gid, keyUpdateRequest.mid, keyUpdateRequest.mode)
            }
        } else if (null != keyUpdateRequest) {
            ACLog.i(accountContext,  "GroupOfflineMessageSyncTask", "key update message ignore ${keyUpdateRequest.mid}")
        }
    }

    private fun syncGroupKeyVersions(accountContext: AccountContext, versionList: List<Long>): Observable<Boolean> {
        val keyVersions = versionList.toMutableList()
        return GroupLogic.get(accountContext).syncGroupKeyList(gid, keyVersions)
                .subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AmeDispatcher.ioScheduler)
                .flatMap {
                    val localKeyVersions = GroupInfoDataManager.queryGroupKeyList(accountContext, gid, keyVersions.toList()).map { it.version }
                    keyVersions.removeAll(localKeyVersions)
                    if (keyVersions.isNotEmpty()) {
                        //
                        //The encrypted version of the message is larger than the password version I can get, which means I can't solve it with the password.
                        if ((localKeyVersions.isNotEmpty() && Collections.max(keyVersions) > Collections.max(localKeyVersions))
                                || localKeyVersions.isEmpty()) {
                            GroupInfoDataManager.saveGroupKeyParam(accountContext, gid, Collections.max(keyVersions), "")

                            return@flatMap GroupLogic.get(accountContext).getKeyRotate().rotateGroup(gid)
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

    private fun keyVersionsFromKeySwitchMessage(accountContext: AccountContext, msgs: List<GroupMessageEntity>): List<Long> {
        return msgs.filter { it.type == GroupMessageProtos.GroupMsg.Type.TYPE_SWITCH_GROUP_KEYS_VALUE }
                .mapNotNull {
                    try {
                        val switch = GsonUtils.fromJson<KeySwitchMessage>(it.text, KeySwitchMessage::class.java)
                        switch.version
                    } catch (e: Throwable) {
                        ACLog.e(accountContext,  "GroupOfflineMessageSyncTask", "keyVersionsFromKeySwitchMessage", e)
                        null
                    }
                }.toSet().toList()
    }

    data class KeyUpdateMessage(
            @SerializedName("group_keys_mode")
            val mode: Int = -1,
            var mid: Long = 0,
            var time:Long = 0):NotGuard

    private fun keyUpdateRequestFromMessageList(accountContext: AccountContext, msgs: List<GroupMessageEntity>): KeyUpdateMessage? {
        return msgs.filter { it.type == GroupMessageProtos.GroupMsg.Type.TYPE_UPDATE_GROUP_KEYS_REQUEST_VALUE }
                .mapNotNull {
                    try {
                        val update = GsonUtils.fromJson<KeyUpdateMessage>(it.text, KeyUpdateMessage::class.java)
                        update.mid = it.mid
                        update.time = it.createTime
                        if (update.mode >= 0) {
                            update
                        } else {
                            ACLog.i(accountContext,  "GroupOfflineMessageSyncTask", "keyUpdateMessage key mode is null")
                            null
                        }
                    } catch (e: Throwable) {
                        ACLog.e(accountContext,  "GroupOfflineMessageSyncTask", "keyVersionsFromKeySwitchMessage", e)
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
