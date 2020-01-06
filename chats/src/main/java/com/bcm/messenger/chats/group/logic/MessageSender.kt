package com.bcm.messenger.chats.group.logic

import android.annotation.SuppressLint
import android.net.Uri
import android.widget.Toast
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.group.core.GroupMessageCore
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.ServerResult
import com.bcm.messenger.common.core.corebean.AmeGroupInfo
import com.bcm.messenger.common.core.corebean.GroupKeyParam
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.crypto.encrypt.GroupMessageEncryptUtils
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.manager.GroupInfoDataManager
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.grouprepository.modeltransform.GroupMessageTransform
import com.bcm.messenger.common.grouprepository.room.entity.GroupMessage
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.base64Decode
import com.bcm.messenger.common.utils.base64Encode
import com.bcm.messenger.common.utils.format
import com.bcm.messenger.utility.*
import com.bcm.messenger.utility.bcmhttp.utils.ServerCodeUtil
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.bcm.messenger.utility.proguard.NotGuard
import com.google.gson.reflect.TypeToken
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.json.JSONArray

/**
 * group message handler
 * Created by zjl on 2018/6/7.
 */
class MessageSender(private val mAccountContext: AccountContext) {

    companion object {
        private const val TAG = "MessageSender"
        private const val PREF_LAST_PENDING_MSG = "pref_last_pending_msg"
    }

    interface SenderCallback {
        fun call(messageDetail: AmeGroupMessageDetail?, indexId: Long, isSuccess: Boolean)
    }

    interface ResendCallback : SenderCallback

    data class PendingMsgInfo(var gid: Long, var indexId: Long): NotGuard {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PendingMsgInfo

            if (gid != other.gid) return false
            if (indexId != other.indexId) return false

            return true
        }

        override fun hashCode(): Int {
            var result = gid.hashCode()
            result = 31 * result + indexId.hashCode()
            return result
        }
    }

    private val mPendingMsgSet: MutableSet<PendingMsgInfo> by lazy {
        val pendingSet = mutableSetOf<PendingMsgInfo>()
        TextSecurePreferences.getStringSetPreference(mAccountContext, PREF_LAST_PENDING_MSG)?.mapTo(pendingSet) {
            GsonUtils.fromJson(it, object : TypeToken<PendingMsgInfo>() {}.type)
        }
        pendingSet
    }

    private fun savePendingSet(value: MutableSet<PendingMsgInfo>) {
        TextSecurePreferences.setStringSetPreference(mAccountContext, PREF_LAST_PENDING_MSG, mPendingMsgSet.map {
            GsonUtils.toJson(it)
        }.toSet())
    }


    @Synchronized
    private fun addPendingItem(value: PendingMsgInfo) {
        try {
            if (!mPendingMsgSet.contains(value)) {
                mPendingMsgSet.add(value)
                savePendingSet(mPendingMsgSet)
            }
        }catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun addPendingItem(gid: Long, indexId: Long) {
        addPendingItem(PendingMsgInfo(gid, indexId))
    }

    private fun removePendingItem(gid: Long, indexId: Long) {
        removePendingItem(PendingMsgInfo(gid, indexId))
    }

    @Synchronized
    private fun removePendingItem(value: PendingMsgInfo) {
        try {
            if (mPendingMsgSet.contains(value)) {
                mPendingMsgSet.remove(value)
                savePendingSet(mPendingMsgSet)
            }
        }catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    fun resetPendingMessageState() {

        Observable.create(ObservableOnSubscribe<Int> {
            var count = 0
            synchronized(this) {
                val removeList = mutableListOf<PendingMsgInfo>()
                for (pending in mPendingMsgSet) {
                    if (MessageDataManager.updateMessageSendStateByIndex(mAccountContext, pending.gid, pending.indexId, GroupMessage.SEND_FAILURE) == 0) {
                        removeList.add(pending)
                        count++
                    }
                }
                mPendingMsgSet.removeAll(removeList)
                savePendingSet(mPendingMsgSet)
            }

            it.onNext(count)
            it.onComplete()

        }).subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe({

                }, {

                })

    }

    fun sendTextMessage(groupId: Long, text: String, callback: SenderCallback? = null, extContent: AmeGroupMessageDetail.ExtensionContent? = null) {
        val messageDetail = AmeGroupMessageDetail().apply {
            gid = groupId
            sendTime = AmeTimeUtil.getMessageSendTime()
            senderId = mAccountContext.uid
            isSendByMe = true
            attachmentUri = ""
            this.extContent = extContent
        }
        if (AmeURLUtil.isLegitimateUrl(text)) {

            val shareContent = AmeGroupMessage.GroupShareContent.fromLink(text)
            if (shareContent == null) {
                messageDetail.apply {
                    message = AmeGroupMessage(AmeGroupMessage.LINK, AmeGroupMessage.LinkContent(text.trim(), ""))
                }
            }else {
                messageDetail.apply {
                    message = AmeGroupMessage(AmeGroupMessage.GROUP_SHARE_CARD, shareContent)
                }
            }

        } else {
            messageDetail.apply { message = AmeGroupMessage(AmeGroupMessage.TEXT, AmeGroupMessage.TextContent(text)) }
        }

        sendTextMessage(groupId, messageDetail, callback)
    }


    fun sendContactMessage(groupId: Long, contact: AmeGroupMessage.ContactContent, callback: SenderCallback?) {
        sendTextMessage(groupId, AmeGroupMessageDetail().apply {
            sendTime = AmeTimeUtil.getMessageSendTime()
            message = AmeGroupMessage(AmeGroupMessage.CONTACT, contact)
        }, callback)
    }

    fun sendGroupShareMessage(groupId: Long, content: AmeGroupMessage.GroupShareContent, callback: SenderCallback?) {
        sendTextMessage(groupId, AmeGroupMessageDetail().apply {
            sendTime = AmeTimeUtil.getMessageSendTime()
            message = AmeGroupMessage(AmeGroupMessage.GROUP_SHARE_CARD, content)
        }, callback)
    }

    fun sendShareChannelMessage(groupId: Long, shareChannelContent: AmeGroupMessage.NewShareChannelContent, callback: SenderCallback?) {
        sendTextMessage(groupId, AmeGroupMessageDetail().apply {
            sendTime = AmeTimeUtil.getMessageSendTime()
            message = AmeGroupMessage(AmeGroupMessage.NEWSHARE_CHANNEL, shareChannelContent)
        }, callback)
    }

    fun sendLocationMessage(groupId: Long, latitude: Double, longitude: Double, mapType: Int, title: String, address: String, callback: SenderCallback?) {

        ALog.d(TAG, "sendLocationMessage latitude: $latitude, longitude: $longitude")
        sendTextMessage(groupId, AmeGroupMessageDetail().apply {
            sendTime = AmeTimeUtil.getMessageSendTime()
            message = AmeGroupMessage(AmeGroupMessage.LOCATION, AmeGroupMessage.LocationContent(latitude, longitude, mapType, title, address))
        }, callback)
    }

    fun sendHistoryMessage(groupId: Long, historyContent: AmeGroupMessage.HistoryContent, callback: SenderCallback?) {
        sendTextMessage(groupId, AmeGroupMessageDetail().apply {
            sendTime = AmeTimeUtil.getMessageSendTime()
            message = AmeGroupMessage(AmeGroupMessage.CHAT_HISTORY, historyContent)
        }, callback)
    }

    fun sendReplyMessage(groupId: Long, text: CharSequence, replyContent: AmeGroupMessage.ReplyContent, callback: SenderCallback? = null, extContent: AmeGroupMessageDetail.ExtensionContent? = null) {
        sendTextMessage(groupId, AmeGroupMessageDetail().apply {
            sendTime = AmeTimeUtil.getMessageSendTime()
            var replyString = replyContent.replyString
            val message = replyContent.getReplyMessage()
            if (message.type == AmeGroupMessage.LINK) {
                val linkContent = message.content as AmeGroupMessage.LinkContent
                replyString = AmeGroupMessage(AmeGroupMessage.TEXT, AmeGroupMessage.TextContent(linkContent.url)).toString()
            }

            this.message = AmeGroupMessage(AmeGroupMessage.CHAT_REPLY, AmeGroupMessage.ReplyContent(replyContent.mid, replyContent.uid, replyString, text.toString()))
            this.extContent = extContent

        }, callback)
    }

    fun sendShareChannelMessage(groupId: Long, groupInfo: AmeGroupInfo, callback: SenderCallback?) {
        sendTextMessage(groupId, AmeGroupMessageDetail().apply {
            sendTime = AmeTimeUtil.getMessageSendTime()
            message = AmeGroupMessage(AmeGroupMessage.NEWSHARE_CHANNEL,
                    AmeGroupMessage.NewShareChannelContent(groupInfo.channelKey
                            ?: "",
                            groupInfo.channelURL, groupInfo.name, groupInfo.gid, groupInfo.iconUrl, groupInfo.shareContent))
        }, callback)
    }

    fun sendVideoMessage(masterSecret: MasterSecret, groupId: Long, uri: Uri, content: AmeGroupMessage.VideoContent, path: String?, callback: SenderCallback?) {
        sendMediaMessage(masterSecret, groupId, path, AmeGroupMessageDetail().apply {
            sendTime = AmeTimeUtil.getMessageSendTime()
            attachmentUri = uri.toString()
            message = AmeGroupMessage(AmeGroupMessage.VIDEO, content)

        }, callback)
    }

    fun sendImageMessage(masterSecret: MasterSecret, groupId: Long, content: AmeGroupMessage.ImageContent, uri: Uri, filePath: String, callback: SenderCallback?) {
        sendMediaMessage(masterSecret, groupId, filePath, AmeGroupMessageDetail().apply {
            sendTime = AmeTimeUtil.getMessageSendTime()
            attachmentUri = uri.toString()
            message = AmeGroupMessage(AmeGroupMessage.IMAGE, content)
        }, callback)
    }

    fun sendDocumentMessage(masterSecret: MasterSecret, groupId: Long, content: AmeGroupMessage.FileContent, path: String, callback: SenderCallback?) {
        sendMediaMessage(masterSecret, groupId, path, AmeGroupMessageDetail().apply {
            sendTime = AmeTimeUtil.getMessageSendTime()
            attachmentUri = BcmFileUtils.getFileUri(path).toString()
            message = AmeGroupMessage(AmeGroupMessage.FILE, content)
        }, callback)
    }

    fun sendAudioMessage(masterSecret: MasterSecret, groupId: Long, content: AmeGroupMessage.AudioContent, path: String, callback: SenderCallback?) {
        sendMediaMessage(masterSecret, groupId, path, AmeGroupMessageDetail().apply {
            sendTime = AmeTimeUtil.getMessageSendTime()
            attachmentUri = BcmFileUtils.getFileUri(path).toString()
            message = AmeGroupMessage(AmeGroupMessage.AUDIO, content)
        }, callback)
    }

    fun sendPinMessage(groupId: Long, mid: Long, callback: SenderCallback?) {
        sendTextMessage(groupId, AmeGroupMessageDetail().apply {
            sendTime = AmeTimeUtil.getMessageSendTime()
            message = AmeGroupMessage(AmeGroupMessage.PIN, AmeGroupMessage.PinContent(mid))
        }, callback)
    }

    fun sendStartLiveMessage(groupId: Long, liveId: Long, sourceUrl: String, playSource: AmeGroupMessage.LiveContent.PlaySource, duration: Long, callback: SenderCallback?) {
        realSendLiveMessage(groupId, sourceUrl, playSource, liveId, duration, 0, 1, callback)
    }

    fun sendEndLiveMessage(isLiving: Boolean, groupId: Long, sourceUrl: String, playSource: AmeGroupMessage.LiveContent.PlaySource, liveId: Long, duration: Long, callback: SenderCallback?) {
        if (isLiving) {
            realSendLiveMessage(groupId, sourceUrl, playSource, liveId, duration, duration, -1, callback)
        } else {
            realSendLiveMessage(groupId, sourceUrl, playSource, liveId, duration, duration, 4, callback)
        }

    }

    fun sendPauseLiveMessage(groupId: Long, sourceUrl: String, playSource: AmeGroupMessage.LiveContent.PlaySource, liveId: Long, duration: Long, currentSeekTime: Long, callback: SenderCallback?) {
        realSendLiveMessage(groupId, sourceUrl, playSource, liveId, duration, currentSeekTime, 2, callback)
    }

    fun sendRestartLiveMessage(groupId: Long, sourceUrl: String, playSource: AmeGroupMessage.LiveContent.PlaySource, liveId: Long, duration: Long, currentSeekTime: Long, callback: SenderCallback?) {
        realSendLiveMessage(groupId, sourceUrl, playSource, liveId, duration, currentSeekTime, 3, callback)
    }

    @SuppressLint("CheckResult")
    fun resendMediaMessage(messageDetail: AmeGroupMessageDetail, call: ResendCallback? = null) {
        val masterSecret = BCMEncryptUtils.getMasterSecret(mAccountContext) ?: return

        Observable.create(ObservableOnSubscribe<String> {

            val path = BcmFileUtils.getFileAbsolutePath(AppContextHolder.APP_CONTEXT, Uri.parse(messageDetail.attachmentUri))
            if (path == null || path.isEmpty()) {
                it.onError(Exception("media path is null"))
            } else {
                it.onNext(path)
                it.onComplete()
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe({

            sendMediaMessage(masterSecret, messageDetail.gid, it, messageDetail, call)

        }, {
            ALog.e(TAG, "resendMediaMessage error", it)
            call?.call(messageDetail, -1, false)
        })

    }

    fun resendTextMessage(messageDetail: AmeGroupMessageDetail, call: ResendCallback? = null) {
        sendTextMessage(messageDetail.gid, messageDetail, call)
    }

    fun resendLocationMessage(messageDetail: AmeGroupMessageDetail, call: ResendCallback? = null) {
        resendTextMessage(messageDetail, call)
    }


    fun resendContactMessage(messageDetail: AmeGroupMessageDetail, call: ResendCallback? = null) {
        resendTextMessage(messageDetail, call)
    }

    fun resendGroupShareMessage(messageDetail: AmeGroupMessageDetail, call: ResendCallback? = null) {
        resendTextMessage(messageDetail, call)
    }

    fun resendHistoryMessage(messageDetail: AmeGroupMessageDetail, call: ResendCallback? = null) {
        resendTextMessage(messageDetail, call)
    }

    fun resendPinMessage(messageDetail: AmeGroupMessageDetail, call: ResendCallback? = null) {
        resendTextMessage(messageDetail, call)
    }

    fun recallMessage(messageDetail: AmeGroupMessageDetail?, callback: SenderCallback) {

        Observable.create(ObservableOnSubscribe<Boolean> { emiter ->

            if (null != messageDetail) {
                val groupInfo = GroupInfoDataManager.queryOneGroupInfo(mAccountContext, messageDetail.gid)
                if (groupInfo == null) {
                    emiter.onError(Exception("GroupInfo is null"))
                }else {
                    GroupMessageCore.recallMessage(mAccountContext, messageDetail.gid, messageDetail.serverIndex, messageDetail.identityIvString, groupInfo.channelPublicKey.base64Encode().format())
                            .subscribeOn(Schedulers.io())
                            .observeOn(Schedulers.io())
                            .subscribe({
                                if (it.isSuccess) {
                                    MessageDataManager.recallMessage(mAccountContext, mAccountContext.uid, messageDetail.gid, messageDetail.serverIndex)
                                }
                                emiter.onNext(it.isSuccess)
                                emiter.onComplete()

                            }, {
                                emiter.onError(it)
                            })
                }
            } else {
                emiter.onError(Exception("groupMessageDetail is null"))
            }

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({result ->
                    callback.call(messageDetail, messageDetail?.indexId ?: 0, result)
                }, {
                    ALog.e(TAG, "recallMessage fail", it)
                    callback.call(messageDetail, messageDetail?.indexId ?: 0, false)
                })
    }


    fun recallMessage(groupId: Long, indexId: Long, callback: SenderCallback) {

        var groupMessageDetail: AmeGroupMessageDetail? = null
        Observable.create(ObservableOnSubscribe<Boolean> { emiter ->
            val detail = MessageDataManager.fetchOneMessageByGidAndIndexId(mAccountContext, groupId, indexId)
            groupMessageDetail = detail
            if (null != detail) {
                val groupInfo = GroupInfoDataManager.queryOneGroupInfo(mAccountContext, groupId)
                if (groupInfo == null) {
                    emiter.onError(Exception("GroupInfo is null"))
                }else {

                    GroupMessageCore.recallMessage(mAccountContext, groupId, detail.serverIndex, detail.identityIvString, groupInfo.channelPublicKey.base64Encode().format())
                            .subscribeOn(Schedulers.io())
                            .observeOn(Schedulers.io())
                            .subscribe({
                                if (it.isSuccess) {
                                    MessageDataManager.recallMessage(mAccountContext, mAccountContext.uid, groupId, detail.serverIndex)
                                }
                                emiter.onNext(it.isSuccess)
                                emiter.onComplete()

                            }, {
                                emiter.onError(it)
                            })
                }
            } else {
                emiter.onError(Exception("groupMessageDetail is null"))
            }

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({result ->
                    callback.call(groupMessageDetail, indexId, result)
                }, {
                    ALog.e(TAG, "recallMessage fail", it)
                    callback.call(groupMessageDetail, indexId, false)
                })
    }

    private fun realSendLiveMessage(groupId: Long, sourceUrl: String, playSource: AmeGroupMessage.LiveContent.PlaySource, liveId: Long, duration: Long, currentSeekTime: Long, action: Int, callback: SenderCallback?) {
        sendTextMessage(groupId, AmeGroupMessageDetail().apply {
            sendTime = AmeTimeUtil.getMessageSendTime()
            message = AmeGroupMessage(AmeGroupMessage.LIVE_MESSAGE, AmeGroupMessage.LiveContent(liveId, action, AmeTimeUtil.serverTimeMillis(), sourceUrl, duration, currentSeekTime, playSource))
        }, callback)
    }


    @SuppressLint("CheckResult")
    private fun sendTextMessage(groupId: Long, messageDetail: AmeGroupMessageDetail, callback: SenderCallback?) {
        if (groupId == -1L) {
            ALog.e(TAG, "gid is null")
            AmeDispatcher.mainThread.dispatch {
                callback?.call(messageDetail, -1, false)
            }
            return
        }


        ALog.i(TAG, "sendTextMessage begin, indexId: ${messageDetail.indexId} gid: $groupId")
        messageDetail.apply {
            gid = groupId
            senderId = mAccountContext.uid
            isSendByMe = true
            attachmentUri = ""
        }

        val isResend = messageDetail.sendState == AmeGroupMessageDetail.SendState.SEND_FAILED || messageDetail.sendState == AmeGroupMessageDetail.SendState.SENDING

        Observable.create(ObservableOnSubscribe<AmeGroupMessageDetail> {

            try {
                if (isResend) {
                    updateMessageResending(messageDetail)
                } else {
                    updateMessageSending(messageDetail)
                }
                it.onNext(messageDetail)
                it.onComplete()

            } catch (ex: Exception) {
                updateMessageFail(messageDetail)
                it.onError(ex)
            }

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    ALog.i(TAG, "sendTextMessage end, indexId: ${messageDetail.indexId} gid: $groupId")
                    realSendGroupMessage(it, it.indexId, callback)

                }, {
                    ALog.e(TAG, "sendTextMessage fail, indexId: ${messageDetail.indexId} gid: $groupId")

                    callback?.call(messageDetail, -1, false)

                })

    }

    @SuppressLint("CheckResult")
    private fun sendMediaMessage(masterSecret: MasterSecret, groupId: Long, filePath: String?, message: AmeGroupMessageDetail, callback: SenderCallback?) {

        if (groupId == -1L || filePath == null || filePath.isEmpty()) {
            ALog.e(TAG, "gid is null or filePath is null")
            AmeDispatcher.mainThread.dispatch {
                callback?.call(message, -1, false)
            }
            return
        }

        fun callback(emitter: ObservableEmitter<AmeGroupMessageDetail>, messageDetail: AmeGroupMessageDetail, causeException: Exception? = null) {
            try {
                if (causeException != null) {
                    updateMessageFail(message)
                    emitter.onError(causeException)
                } else {
                    emitter.onNext(messageDetail)
                }
            }catch (ex: Exception) {
                emitter.onError(ex)
            }finally {
                emitter.onComplete()
            }
        }

        message.apply {
            gid = groupId
            senderId = mAccountContext.uid
            isSendByMe = true
        }

        val isResend = message.sendState == AmeGroupMessageDetail.SendState.SEND_FAILED || message.sendState == AmeGroupMessageDetail.SendState.SENDING

        ALog.i(TAG, "sendMediaMessage begin, indexId: ${message.indexId} gid: ${message.gid} type: ${message.message.type}")

        Observable.create(ObservableOnSubscribe<AmeGroupMessageDetail> {

            try {
                if (isResend) {
                    updateMessageResending(message)
                } else {
                    updateMessageSending(message)
                }

                when (message.message.type) {
                    AmeGroupMessage.IMAGE -> {
                        val content = message.message.content as AmeGroupMessage.ImageContent
                        if (!content.url.isNullOrEmpty()) {
                            callback(it, message)
                        } else {
                            MessageFileHandler.uploadImage(masterSecret, message.gid, filePath) { urlResult, thumbResult ->
                                ALog.i(TAG, "uploadImage finish groupId: $groupId")
                                if (urlResult != null && thumbResult != null) {
                                    message.attachmentUri = Uri.fromFile(urlResult.fileInfo.file).toString()
                                    message.dataHash = urlResult.fileInfo.hash
                                    message.dataRandom = urlResult.fileInfo.random
                                    message.attachmentSize = urlResult.fileInfo.size

                                    message.thumbnailUri = Uri.fromFile(thumbResult.fileInfo.file)
                                    message.thumbHash = thumbResult.fileInfo.hash
                                    message.thumbRandom = thumbResult.fileInfo.random

                                    MessageDataManager.updateMessageAttachmentUri(mAccountContext, groupId, message.indexId, urlResult.fileInfo)
                                    MessageDataManager.updateMessageThumbnailUri(mAccountContext, groupId, message.indexId, thumbResult.fileInfo)

                                    if (urlResult.url.isNotEmpty() && thumbResult.url.isNotEmpty()) {
                                        content.url = urlResult.url
                                        content.sign = urlResult.sign
                                        content.thumbnail_url = thumbResult.url
                                        content.sign_thumbnail = thumbResult.sign
                                        message.message = AmeGroupMessage(AmeGroupMessage.IMAGE, content)

                                        callback(it, message)
                                    } else {
                                        callback(it, message, Exception("uploadImage fail"))
                                    }
                                } else {
                                    callback(it, message, Exception("uploadImage fail"))
                                }
                            }
                        }
                    }

                    AmeGroupMessage.VIDEO -> {
                        val content = message.message.content as AmeGroupMessage.VideoContent
                        if (!content.url.isNullOrEmpty()) {
                            callback(it, message)
                        } else {
                            MessageFileHandler.uploadVideo(masterSecret, groupId, filePath) { urlResult, thumbResult ->
                                if (null != urlResult && thumbResult != null) {
                                    message.attachmentUri = Uri.fromFile(urlResult.fileInfo.file).toString()
                                    message.dataHash = urlResult.fileInfo.hash
                                    message.dataRandom = urlResult.fileInfo.random
                                    message.attachmentSize = urlResult.fileInfo.size

                                    message.thumbnailUri = Uri.fromFile(thumbResult.fileInfo.file)
                                    message.thumbHash = thumbResult.fileInfo.hash
                                    message.thumbRandom = thumbResult.fileInfo.random

                                    MessageDataManager.updateMessageAttachmentUri(mAccountContext, groupId, message.indexId, urlResult.fileInfo)
                                    MessageDataManager.updateMessageThumbnailUri(mAccountContext, groupId, message.indexId, thumbResult.fileInfo)

                                    if (urlResult.url.isNotEmpty() && thumbResult.url.isNotEmpty()) {
                                        content.url = urlResult.url
                                        content.sign = urlResult.sign

                                        content.thumbnail_url = thumbResult.url
                                        content.sign_thumbnail = thumbResult.sign
                                        content.thumbnail_width = thumbResult.width
                                        content.thumbnail_height = thumbResult.height
                                        message.message = AmeGroupMessage(AmeGroupMessage.VIDEO, content)

                                        callback(it, message)
                                    } else {
                                        callback(it, message, Exception("uploadVideo fail"))
                                    }
                                } else {
                                    callback(it, message, Exception("uploadVideo fail"))
                                }
                            }
                        }

                    }
                    AmeGroupMessage.AUDIO -> {
                        val content = message.message.content as AmeGroupMessage.AudioContent
                        if (!content.url.isNullOrEmpty()) {
                            callback(it, message)
                        } else {
                            MessageFileHandler.uploadFile(masterSecret, groupId, message.indexId, filePath) { result ->
                                if (result != null) {
                                    message.attachmentUri = Uri.fromFile(result.fileInfo.file).toString()
                                    message.dataHash = result.fileInfo.hash
                                    message.dataRandom = result.fileInfo.random
                                    message.attachmentSize = result.fileInfo.size

                                    MessageDataManager.updateMessageAttachmentUri(mAccountContext, groupId, message.indexId, result.fileInfo)

                                    if (result.url.isNotEmpty()) {
                                        content.url = result.url
                                        content.sign = result.sign
                                        message.message = AmeGroupMessage(AmeGroupMessage.AUDIO, content)

                                        callback(it, message)
                                    } else {
                                        callback(it, message, Exception("uploadAudio fail"))
                                    }
                                } else {
                                    callback(it, message, Exception("uploadAudio fail"))
                                }
                            }
                        }
                    }
                    AmeGroupMessage.FILE -> {
                        val content = message.message.content as AmeGroupMessage.FileContent
                        if (!content.url.isNullOrEmpty()) {
                            callback(it, message)
                        } else {
                            MessageFileHandler.uploadFile(masterSecret, groupId, message.indexId, filePath) { result ->
                                if (result != null) {
                                    message.attachmentUri = Uri.fromFile(result.fileInfo.file).toString()
                                    message.dataHash = result.fileInfo.hash
                                    message.dataRandom = result.fileInfo.random
                                    message.attachmentSize = result.fileInfo.size

                                    MessageDataManager.updateMessageAttachmentUri(mAccountContext, groupId, message.indexId, result.fileInfo)

                                    if (result.url.isNotEmpty()) {
                                        content.url = result.url
                                        content.sign = result.sign
                                        message.message = AmeGroupMessage(AmeGroupMessage.FILE, content)

                                        callback(it, message)
                                    } else {
                                        callback(it, message, Exception("uploadFile fail"))
                                    }
                                } else {
                                    callback(it, message, Exception("uploadFile fail"))
                                }
                            }
                        }
                    }
                    else -> {
                        callback(it, message)
                    }
                }

            } catch (ex: Exception) {
                callback(it, message, ex)
            }

        }).subscribeOn(Schedulers.io()).observeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    ALog.i(TAG, "sendMediaMessage end, indexId: ${it.indexId} gid: ${it.gid} type: ${it.message.type}")
                    realSendGroupMessage(it, it.indexId, callback)

                }, {
                    ALog.e(TAG, "sendMediaMessage end, indexId: ${message.indexId} gid: ${message.gid} type: ${message.message.type}", it)
                    callback?.call(message, -1, false)
                })

    }


    fun sendMessage(groupId:Long, message:AmeGroupMessage<*>, visible:Boolean, result:(succeed:Boolean)->Unit) {
        if (groupId == -1L) {
            result(false)
            return
        }


        ALog.i(TAG, "sendMessage begin, gid: $groupId")
        val messageDetail = AmeGroupMessageDetail().apply {
            gid = groupId
            senderId = mAccountContext.uid
            isSendByMe = true
            attachmentUri = ""
            sendTime = AmeTimeUtil.getMessageSendTime()
            this.message = message
        }

        Observable.create(ObservableOnSubscribe<AmeGroupMessageDetail> {
            try {
                updateMessageSending(messageDetail, visible)
                it.onNext(messageDetail)
                it.onComplete()

            } catch (ex: Exception) {
                updateMessageFail(messageDetail)
                it.onError(ex)
            }

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    ALog.i(TAG, "sendMessage end, indexId: ${messageDetail.indexId} gid: $groupId")
                    realSendGroupMessage(it, it.indexId, object :SenderCallback {
                        override fun call(messageDetail: AmeGroupMessageDetail?, indexId: Long, isSuccess: Boolean) {
                            result(isSuccess)
                        }
                    })

                }, {
                    ALog.e(TAG, "sendMessage fail, indexId: ${messageDetail.indexId} gid: $groupId")
                    result(false)
                })
    }

    @SuppressLint("CheckResult")
    private fun realSendGroupMessage(messageDetail: AmeGroupMessageDetail, index: Long, callback: SenderCallback?): Long {

        var groupEncryptSpec: GroupKeyParam? = null
        Observable.create(ObservableOnSubscribe<Pair<String, String?>> {

            addPendingItem(messageDetail.gid, index)
            var msg = messageDetail.message
            if (msg.type == AmeGroupMessage.LINK) {
                msg = AmeGroupMessage(AmeGroupMessage.TEXT, AmeGroupMessage.TextContent((msg.content as AmeGroupMessage.LinkContent).url))
            }
            var messageBody = msg.toString()

            val groupInfo = GroupInfoDataManager.queryOneGroupInfo(mAccountContext, messageDetail.gid) ?: throw Exception("groupInfo is null")
            groupEncryptSpec = GroupKeyParam(groupInfo.currentKey.base64Decode(), groupInfo.currentKeyVersion)
            val messageEncryptResult: Triple<Boolean, String, Int> = GroupMessageEncryptUtils.encryptMessageProcess(messageBody, groupEncryptSpec)
            messageBody = messageEncryptResult.second

            val atListString = try {
                val atList = messageDetail.extContent?.atList
                if (atList != null && atList.isNotEmpty()) {
                    val array = JSONArray()
                    for (at in atList) {
                        array.put(at)
                    }
                    array.toString()
                } else {
                    null
                }
            } catch (ex: Exception) {
                ALog.e("AmeGroupMessageDetail", "toAtListString error", ex)
                null
            }
            it.onNext(Pair(messageBody, atListString))
            it.onComplete()

        }).subscribeOn(Schedulers.io()).observeOn(Schedulers.io()).flatMap {
            ALog.i(TAG, "realSendGroupMessage being, index: ${messageDetail.indexId}, gid: ${messageDetail.gid}")
            //Randomly generate a vector for the identity of the current message to facilitate future recall logic
            val iv = if (messageDetail.identityIvString.isNullOrEmpty()) {
                val iv = EncryptUtils.getSecretBytes(16)
                messageDetail.identityIvString = Base64.encodeBytes(iv)
                iv
            }else {
                Base64.decode(messageDetail.identityIvString)
            }

            val groupInfo = GroupInfoDataManager.queryOneGroupInfo(mAccountContext, messageDetail.gid) ?: throw Exception("groupInfo is null")
            messageDetail.identityIvString = Base64.encodeBytes(iv)
            GroupMessageCore.sendGroupMessage(mAccountContext, messageDetail.gid, it.first, Base64.encodeBytes(BCMEncryptUtils.signWithMe(mAccountContext, iv)), Base64.encodeBytes(groupInfo.channelPublicKey), it.second)

        }.observeOn(Schedulers.io())
                .doOnNext {
                    ALog.i(TAG, "realSendGroupMessage end, index: ${messageDetail.indexId}, gid: ${messageDetail.gid}")
                    val spec = groupEncryptSpec
                    if (null != spec) {
                        MessageDataManager.updateMessageEncryptMode(mAccountContext, messageDetail.gid, index, spec.keyVersion)
                    }

                    if (it.code == ServerResult.RESULT_SUCCESS) {
                        updateMessageSendSuccess(messageDetail, it.data.mid.toLong(), it.data.create_time?.toLong()?: AmeTimeUtil.serverTimeMillis())
                    } else {
                        updateMessageFail(messageDetail)
                    }

                    Repository.getThreadRepo(mAccountContext)?.updateByNewGroup(messageDetail.gid)

                }
                .observeOn(Schedulers.io())
                .doOnError {
                    ALog.e(TAG, "realSendGroupMessage error", it)
                    val spec = groupEncryptSpec
                    if (null != spec) {
                        MessageDataManager.updateMessageEncryptMode(mAccountContext, messageDetail.gid, index, spec.keyVersion)
                    }

                    updateMessageFail(messageDetail)

                    Repository.getThreadRepo(mAccountContext)?.updateByNewGroup(messageDetail.gid)

                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback?.call(messageDetail, messageDetail.indexId, true)

                }, {

                    if (ServerCodeUtil.getNetStatusCode(it) == ServerCodeUtil.CODE_LOW_VERSION) {
                        ToastUtil.show(AppContextHolder.APP_CONTEXT, AppContextHolder.APP_CONTEXT.getString(R.string.common_too_low_version_notice), Toast.LENGTH_LONG)
                    }
                    callback?.call(messageDetail, messageDetail.indexId, false)
                })

        return index
    }

    private fun updateMessageSendSuccess(messageDetail: AmeGroupMessageDetail, mid: Long, createTime: Long) {
        removePendingItem(messageDetail.gid, messageDetail.indexId)
        messageDetail.sendState = AmeGroupMessageDetail.SendState.SEND_SUCCESS
        messageDetail.serverIndex = mid
        messageDetail.sendTime = createTime
        MessageDataManager.updateMessageSendResult(mAccountContext, messageDetail.gid, messageDetail.indexId, mid,
                createTime, messageDetail.identityIvString, messageDetail.message.toString(), GroupMessage.SEND_SUCCESS)
    }

    private fun updateMessageSending(messageDetail: AmeGroupMessageDetail, visible: Boolean = true) {
        messageDetail.isRead = true
        messageDetail.sendState = AmeGroupMessageDetail.SendState.SENDING
        val groupMessage = GroupMessageTransform.transformToEntity(messageDetail)
        if (!visible) {
            groupMessage.is_confirm = GroupMessage.CONFIRM_BUT_NOT_SHOW
        }
        messageDetail.indexId = MessageDataManager.insertSendMessage(mAccountContext, groupMessage)
        addPendingItem(messageDetail.gid, messageDetail.indexId)
    }

    private fun updateMessageResending(messageDetail: AmeGroupMessageDetail) {

        try {
            messageDetail.sendState = AmeGroupMessageDetail.SendState.SENDING
            val result = MessageDataManager.updateMessageSendStateByIndex(mAccountContext, messageDetail.gid, messageDetail.indexId, GroupMessage.SENDING)
            if (result == 0) {
                addPendingItem(messageDetail.gid, messageDetail.indexId)
            }
        } catch (ex: Exception) {
            ALog.e(TAG, "updateMessageResending fail", ex)
        }
    }

    private fun updateMessageFail(message: AmeGroupMessageDetail) {
        removePendingItem(message.gid, message.indexId)
        message.sendState = AmeGroupMessageDetail.SendState.SEND_FAILED
        MessageDataManager.updateMessageSendStateByIndex(mAccountContext, message.gid, message.indexId, GroupMessage.SEND_FAILURE)
    }

}