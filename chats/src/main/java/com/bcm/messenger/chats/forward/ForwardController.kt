package com.bcm.messenger.chats.forward

import android.net.Uri
import com.bcm.messenger.chats.group.logic.GroupLogic
import com.bcm.messenger.chats.group.logic.GroupMessageLogic
import com.bcm.messenger.chats.group.logic.MessageFileHandler
import com.bcm.messenger.chats.privatechat.logic.MessageSender
import com.bcm.messenger.chats.thread.ThreadListViewModel
import com.bcm.messenger.chats.util.AttachmentSaver
import com.bcm.messenger.chats.util.AttachmentUtils
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.database.repositories.ThreadRepo
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.mms.*
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.sms.OutgoingEncryptedMessage
import com.bcm.messenger.common.sms.OutgoingLocationMessage
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.GroupUtil
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.BitmapUtils
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.util.concurrent.LinkedBlockingQueue

/**
 * Created by zjl on 2018/8/22.
 */
object ForwardController {
    private const val TAG = "ForwardController"

    private val messageForwardQueue = LinkedBlockingQueue<ForwardMessage>()
    private val messageOnceQueue = LinkedBlockingQueue<ForwardOnceMessage>()
    private var sendingFlag = false
    private val filePaths = mutableListOf<String>()

    fun addForwardMessage(message: ForwardMessage) {
        message.list.mapTo(messageOnceQueue) { ForwardOnceMessage(message.privateMessage, message.groupMessage, it) }
    }

    fun getForwardMessage(): ForwardMessage? {
        if (messageOnceQueue.size > 0) {
            return messageForwardQueue.poll()
        }
        return null
    }

    fun addOnceMessage(message: ForwardOnceMessage) {
        if (!sendingFlag) {
            sendOnceMessage(message)
        } else {
            messageOnceQueue.add(message)
        }
    }

    private fun getOnceMessage(): ForwardOnceMessage? {
        if (messageOnceQueue.size > 0) {
            return messageOnceQueue.poll()
        } else if (messageForwardQueue.size > 0) {
            addForwardMessage(messageForwardQueue.poll())
            return getOnceMessage()
        }
        return null
    }

    private fun sendOnceMessage(message: ForwardOnceMessage) {
        sendingFlag = true
        if (ForwardType.getType(message) == ForwardType.HAS_PRIVATE) {
            if (message.recipient.address.isGroup) {
                privateForwardGroupMessage(message)
            } else {
                privateForwardPrivateMessage(message)
            }
        } else if (ForwardType.getType(message) == ForwardType.HAS_PUBLIC) {
            if (message.recipient.address.isGroup) {
                groupForwardGroupMessage(message)
            } else {
                groupForwardPrivateMessage(message)
            }
        }
    }

    private fun privateForwardPrivateMessage(message: ForwardOnceMessage) {
        ALog.d(TAG, "Private forward private")
        ThreadListViewModel.getThreadId(message.recipient) { threadId ->
            try {
                val privateMessage = message.privateMessage
                val masterSecret = message.masterSecret
                if (privateMessage != null && masterSecret != null) {
                    when {
                        privateMessage.isMediaMessage() && privateMessage.getDocumentAttachment() != null -> {
                            ALog.d(TAG, "PrivateMessage is a file")
                            val attachment = privateMessage.getDocumentAttachment() ?: return@getThreadId
                            val slide = DocumentSlide(AppContextHolder.APP_CONTEXT, attachment.getPartUri(), attachment.contentType, attachment.dataSize, attachment.fileName)
                            val deck = SlideDeck()
                            deck.addSlide(slide)

                            val secureMessage = OutgoingSecureMediaMessage(OutgoingMediaMessage(message.recipient, deck, "", AmeTimeUtil.getMessageSendTime(),
                                    -1, message.recipient.expireMessages * 1000L, ThreadRepo.DistributionTypes.DEFAULT))

                            Observable.create<Long> {

                                it.onNext(MessageSender.send(AppContextHolder.APP_CONTEXT, masterSecret,
                                        secureMessage, threadId, null))
                                it.onComplete()

                            }.subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe({
                                        sendFinish()
                                    }, {
                                        ALog.e(TAG, "privateForwardPrivateMessage error", it)
                                        sendFinish()
                                    })
                        }
                        privateMessage.isMediaMessage() && privateMessage.getImageAttachment() != null -> {
                            ALog.d(TAG, "PrivateMessage is an image")
                            val attachment = privateMessage.getImageAttachment() ?: return@getThreadId
                            val slide = ImageSlide(AppContextHolder.APP_CONTEXT, attachment.getPartUri(), attachment.contentType, attachment.dataSize, attachment.transferState)
                            val deck = SlideDeck()
                            deck.addSlide(slide)

                            val secureMessage = OutgoingSecureMediaMessage(OutgoingMediaMessage(message.recipient, deck, "", AmeTimeUtil.getMessageSendTime(),
                                    -1, message.recipient.expireMessages * 1000L, ThreadRepo.DistributionTypes.DEFAULT))

                            Observable.create<Long> {

                                it.onNext(MessageSender.send(AppContextHolder.APP_CONTEXT, masterSecret,
                                        secureMessage, threadId, null))
                                it.onComplete()

                            }.subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe({
                                        sendFinish()
                                    }, {
                                        ALog.e(TAG, "privateForwardPrivateMessage error", it)
                                        sendFinish()
                                    })
                        }
                        privateMessage.isMediaMessage() && privateMessage.getVideoAttachment() != null -> {
                            ALog.d(TAG, "PrivateMessage is an video")
                            val attachment = privateMessage.getVideoAttachment() ?: return@getThreadId
                            val slide = DocumentSlide(AppContextHolder.APP_CONTEXT, attachment.getPartUri(), attachment.contentType, attachment.dataSize, attachment.fileName)
                            val deck = SlideDeck()
                            deck.addSlide(slide)

                            val secureMessage = OutgoingSecureMediaMessage(OutgoingMediaMessage(message.recipient, deck, "", AmeTimeUtil.getMessageSendTime(),
                                    -1, message.recipient.expireMessages * 1000L, ThreadRepo.DistributionTypes.DEFAULT))

                            Observable.create<Long> {

                                it.onNext(MessageSender.send(AppContextHolder.APP_CONTEXT, masterSecret,
                                        secureMessage, threadId, null))
                                it.onComplete()

                            }.subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe({
                                        sendFinish()
                                    }, {
                                        ALog.e(TAG, "privateForwardPrivateMessage error", it)
                                        sendFinish()
                                    })
                        }
                        privateMessage.isLocation() -> {
                            ALog.d(TAG, "PrivateMessage is a location or contact")
                            val newMessage = AmeGroupMessage.messageFromJson(privateMessage.body)
                            val contentStr = newMessage.toString()
                            val locationMessage = OutgoingLocationMessage(message.recipient, contentStr, message.recipient.expireMessages * 1000L)

                            Observable.create<Long> {

                                it.onNext(MessageSender.send(AppContextHolder.APP_CONTEXT,
                                        locationMessage, threadId, null))
                                it.onComplete()

                            }.subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe({
                                        sendFinish()
                                    }, {
                                        ALog.e(TAG, "privateForwardPrivateMessage error", it)
                                        sendFinish()
                                    })
                        }
                        else -> {
                            ALog.d(TAG, "PrivateMessage is a text or unknown message")
                            val body = privateMessage.body
                            val textMessage = OutgoingEncryptedMessage(message.recipient, body, message.recipient.expireMessages * 1000L)

                            Observable.create<Long> {

                                it.onNext(MessageSender.send(AppContextHolder.APP_CONTEXT,
                                        textMessage, threadId, null))
                                it.onComplete()

                            }.subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe({
                                        sendFinish()
                                    }, {
                                        ALog.e(TAG, "privateForwardPrivateMessage error", it)
                                        sendFinish()
                                    })
                        }
                    }

                    sendPrivateCommentMessage(message.commentText, message.recipient, masterSecret, threadId)
                }
            } catch (e: Exception) {
                ALog.e(TAG, "privateForwardPrivateMessage error", e)
                sendFinish()
            }
        }
    }

    /**
     * private forward to group
     * As the private chat attachments are encrypted, you must decrypt the attachments and send them to the sandbox before forwarding to the group.
     */
    private fun privateForwardGroupMessage(message: ForwardOnceMessage) {
        ALog.d(TAG, "Private forward to group")
        try {
            val privateMessage = message.privateMessage
            val masterSecret = message.masterSecret
            if (privateMessage != null && masterSecret != null) {
                val groupId = GroupUtil.gidFromAddress(message.recipient.address)
                when {
                    privateMessage.isMediaMessage() && privateMessage.getImageAttachment() != null -> {
                        val attachment = privateMessage.getImageAttachment() ?: return
                        val slide = ImageSlide(AppContextHolder.APP_CONTEXT, attachment.getPartUri(), attachment.contentType, attachment.dataSize, attachment.transferState)
                        ALog.d(TAG, "PrivateMessage is an image")
                        val slideUri = slide.asAttachment().dataUri
                        if (slideUri != null) {
                            ALog.d(TAG, "Attachment uri is not null, save a decrypted file first")
                            Observable.create<Unit> { emitter ->
                                val file = AttachmentSaver.saveTempAttachment(AppContextHolder.APP_CONTEXT, masterSecret, slideUri, slide.contentType, slide.fileNameString)
                                file?.let {
                                    val smallBitmap = BitmapUtils.compressImageForThumbnail(it)
                                    smallBitmap?.let { bitmap ->
                                        val content = AmeGroupMessage.ImageContent("", bitmap.width, bitmap.height, slide.contentType,
                                                "", BcmFileUtils.saveBitmap2File(bitmap)
                                                ?: "", "", it.length())
                                        GroupMessageLogic.messageSender.sendImageMessage(masterSecret, groupId,  content, Uri.fromFile(it), it.path, privateToGroupCallback)
                                    }
                                    emitter.onComplete()
                                }
                            }.subscribeOn(Schedulers.io())
                                    .subscribe({

                                    }, {

                                    })
                        }
                    }
                    privateMessage.isMediaMessage() && privateMessage.getVideoAttachment() != null -> {
                        ALog.d(TAG, "PrivateMessage is a video")
                        val attachment = privateMessage.getImageAttachment() ?: return
                        val slide = VideoSlide(AppContextHolder.APP_CONTEXT, attachment.getPartUri(), attachment.dataSize)
                        val slideUri = slide.asAttachment().dataUri

                        if (slideUri != null) {
                            ALog.d(TAG, "Attachment uri is not null, save a decrypted file first")
                            Observable.create<Unit> {
                                val file = AttachmentSaver.saveTempAttachment(AppContextHolder.APP_CONTEXT, masterSecret, slideUri, slide.contentType, slide.fileNameString)
                                file?.let { f ->
                                    BcmFileUtils.getVideoFrameInfo(AppContextHolder.APP_CONTEXT, f.absolutePath) { _, width, height ->
                                        val content = AmeGroupMessage.VideoContent("", slide.contentType, f.length(), slide.asAttachment().duration,
                                                "", Uri.fromFile(f).toString(), width, height, "")
                                        GroupMessageLogic.messageSender.sendVideoMessage(masterSecret, groupId, Uri.fromFile(f), content, f.path, privateToGroupCallback)
                                    }
                                }
                                it.onComplete()
                            }.subscribeOn(Schedulers.io())
                                    .subscribe({}, {})
                        }
                    }
                    privateMessage.isMediaMessage() && privateMessage.getDocumentAttachment() != null -> {
                        ALog.d(TAG, "PrivateMessage is a file")
                        val attachment = privateMessage.getDocumentAttachment() ?: return
                        val slide = DocumentSlide(AppContextHolder.APP_CONTEXT, attachment.getPartUri(), attachment.contentType, attachment.dataSize, attachment.fileName)
                        val slideUri = slide.asAttachment().dataUri
                        if (slideUri != null) {
                            Observable.create<Unit> {
                                ALog.d(TAG, "Attachment uri is not null, save a decrypted file first")
                                val file = AttachmentSaver.saveTempAttachment(AppContextHolder.APP_CONTEXT, masterSecret, slideUri, slide.contentType, slide.fileNameString)
                                file?.let { f ->
                                    val content = AmeGroupMessage.FileContent("", slide.fileNameString, f.length(), slide.contentType, "")
                                    GroupMessageLogic.messageSender.sendDocumentMessage(masterSecret, groupId, content, f.absolutePath, privateToGroupCallback)
                                }
                                it.onComplete()

                            }.subscribeOn(Schedulers.io())
                                    .subscribe({}, {})
                        }
                    }
                    privateMessage.isLocation() -> {
                        val m = AmeGroupMessage.messageFromJson(privateMessage.body)
                        when (m.type) {
                            AmeGroupMessage.LOCATION -> {
                                ALog.d(TAG, "PrivateMessage is a location")
                                val content = m.content as AmeGroupMessage.LocationContent
                                GroupMessageLogic.messageSender.sendLocationMessage(groupId, content.latitude, content.longtitude, content.mapType, content.title, content.address, groupMessageCallback)
                            }
                            AmeGroupMessage.NEWSHARE_CHANNEL -> {
                                ALog.d(TAG, "PrivateMessage is a newsharechannel")
                                val content = m.content as AmeGroupMessage.NewShareChannelContent
                                val groupInfo = GroupLogic.getGroupInfo(content.gid)
                                if (null != groupInfo) {
                                    GroupMessageLogic.messageSender.sendShareChannelMessage(groupId, groupInfo, groupMessageCallback)
                                }
                            }
                            AmeGroupMessage.CONTACT -> {
                                ALog.d(TAG, "PrivateMessage is a contact")
                                val content = m.content as AmeGroupMessage.ContactContent
                                GroupMessageLogic.messageSender.sendContactMessage(groupId, content, groupMessageCallback)
                            }
                            AmeGroupMessage.GROUP_SHARE_CARD -> {
                                ALog.d(TAG, "PrivateMessage is a group share card")
                                val content = m.content as AmeGroupMessage.GroupShareContent
                                GroupMessageLogic.messageSender.sendGroupShareMessage(groupId, content, groupMessageCallback)
                            }
                            else -> groupMessageCallback.call(null, 0, false)
                        }
                    }
                    else -> {
                        ALog.d(TAG, "PrivateMessage is a text or unknown message")
                        val body = privateMessage.body
                        GroupMessageLogic.messageSender.sendTextMessage(groupId,  body, groupMessageCallback)
                    }
                }
                sendGroupCommentMessage(message.commentText, groupId)
            }
        } catch (e: Exception) {
            ALog.e(TAG, "privateForwardGroupMessage error", e)
            groupMessageCallback.call(null, 0, false)
        }
    }

    /**
     * group forward to private
     */
    private fun groupForwardPrivateMessage(message: ForwardOnceMessage) {
        ALog.d(TAG, "Group forward to private")
        ThreadListViewModel.getThreadId(message.recipient) { threadId ->

            try {
                if (message.groupMessage != null && message.masterSecret != null) {
                    val groupMessage = message.groupMessage
                    when (groupMessage.message.type) {
                        AmeGroupMessage.TEXT -> {
                            ALog.d(TAG, "GroupMessage is a text")
                            val m = groupMessage.message.content as AmeGroupMessage.TextContent
                            val body = m.text
                            val textMessage = OutgoingEncryptedMessage(message.recipient, body, message.recipient.expireMessages * 1000L)
                            Observable.create<Long> {

                                it.onNext(MessageSender.send(AppContextHolder.APP_CONTEXT,
                                        textMessage, threadId, null))
                                it.onComplete()

                            }.subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe({
                                        sendFinish()
                                    }, {
                                        ALog.e(TAG, "groupForwardPrivateMessage error", it)
                                        sendFinish()
                                    })
                        }
                        AmeGroupMessage.LINK -> {
                            ALog.d(TAG, "GroupMessage is a link")
                            val m = groupMessage.message.content as AmeGroupMessage.LinkContent
                            val body = m.url
                            val textMessage = OutgoingEncryptedMessage(message.recipient, body, message.recipient.expireMessages * 1000L)
                            Observable.create<Long> {

                                it.onNext(MessageSender.send(AppContextHolder.APP_CONTEXT,
                                        textMessage, threadId,  null))
                                it.onComplete()

                            }.subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe({
                                        sendFinish()
                                    }, {
                                        ALog.e(TAG, "groupForwardPrivateMessage error", it)
                                        sendFinish()
                                    })
                        }
                        AmeGroupMessage.IMAGE -> {
                            ALog.d(TAG, "GroupMessage is an image")
                            Observable.create<OutgoingSecureMediaMessage> {
                                downloadGroupFile(groupMessage) { uri ->
                                    val slide = AttachmentUtils.getImageSlide(AppContextHolder.APP_CONTEXT, uri)
                                    val deck = SlideDeck()
                                    deck.addSlide(slide?:return@downloadGroupFile)

                                    val secureMessage = OutgoingSecureMediaMessage(OutgoingMediaMessage(message.recipient, deck, "", AmeTimeUtil.getMessageSendTime(),
                                            -1, message.recipient.expireMessages * 1000L, ThreadRepo.DistributionTypes.CONVERSATION))
                                    it.onNext(secureMessage)
                                    it.onComplete()
                                }
                            }.subscribeOn(Schedulers.io())
                                    .observeOn(Schedulers.io())
                                    .map {
                                        MessageSender.send(AppContextHolder.APP_CONTEXT, message.masterSecret,
                                                it, threadId, null)
                                    }
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe({
                                        sendFinish()
                                    }, {
                                        ALog.e(TAG, "groupForwardPrivateMessage error", it)
                                        sendFinish()
                                    })
                        }
                        AmeGroupMessage.FILE -> {
                            ALog.d(TAG, "GroupMessage is a file")
                            Observable.create<OutgoingSecureMediaMessage> {
                                downloadGroupFile(groupMessage) { uri ->
                                    val content = groupMessage.message.content as AmeGroupMessage.FileContent
                                    val slide = AttachmentUtils.getDocumentSlide(AppContextHolder.APP_CONTEXT, uri, content.fileName ?: "", content.mimeType)
                                    val deck = SlideDeck()
                                    deck.addSlide(slide?:return@downloadGroupFile)

                                    val secureMessage = OutgoingSecureMediaMessage(OutgoingMediaMessage(message.recipient, deck, "", AmeTimeUtil.getMessageSendTime(),
                                            -1, message.recipient.expireMessages * 1000L, ThreadRepo.DistributionTypes.CONVERSATION))
                                    it.onNext(secureMessage)
                                    it.onComplete()
                                }
                            }.subscribeOn(Schedulers.io())
                                    .observeOn(Schedulers.io())
                                    .map {
                                        MessageSender.send(AppContextHolder.APP_CONTEXT, message.masterSecret,
                                                it, threadId, null)
                                    }
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe({
                                        sendFinish()
                                    }, {
                                        ALog.e(TAG, "groupForwardPrivateMessage error", it)
                                        sendFinish()
                                    })
                        }
                        AmeGroupMessage.VIDEO -> {
                            ALog.d(TAG, "GroupMessage is a video")
                            Observable.create<OutgoingSecureMediaMessage> {
                                downloadGroupFile(groupMessage) { uri ->
                                    val content = groupMessage.message.content as AmeGroupMessage.VideoContent
                                    val slide = AttachmentUtils.getDocumentSlide(AppContextHolder.APP_CONTEXT, uri, "", content.mimeType)
                                    val deck = SlideDeck()
                                    deck.addSlide(slide?:return@downloadGroupFile)

                                    val secureMessage = OutgoingSecureMediaMessage(OutgoingMediaMessage(message.recipient, deck, "", AmeTimeUtil.getMessageSendTime(),
                                            -1, message.recipient.expireMessages * 1000L, ThreadRepo.DistributionTypes.CONVERSATION))
                                    it.onNext(secureMessage)
                                    it.onComplete()
                                }
                            }.subscribeOn(Schedulers.io())
                                    .observeOn(Schedulers.io())
                                    .map {
                                        MessageSender.send(AppContextHolder.APP_CONTEXT, message.masterSecret,
                                                it, threadId, null)
                                    }
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe({
                                        sendFinish()
                                    }, {
                                        ALog.e(TAG, "groupForwardPrivateMessage error", it)
                                        sendFinish()
                                    })
                        }
                        AmeGroupMessage.LOCATION -> {
                            ALog.d(TAG, "GroupMessage is a location")
                            val m = groupMessage.message.content as AmeGroupMessage.LocationContent
                            val contentStr = AmeGroupMessage(AmeGroupMessage.LOCATION, AmeGroupMessage.LocationContent(m.latitude, m.longtitude, m.mapType, m.title, m.address)).toString()
                            val locationMessage = OutgoingLocationMessage(message.recipient, contentStr,
                                    message.recipient.expireMessages * 1000L)
                            Observable.create<Long> {

                                it.onNext(MessageSender.send(AppContextHolder.APP_CONTEXT,
                                        locationMessage, threadId, null))
                                it.onComplete()

                            }.subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe({
                                        sendFinish()
                                    }, {
                                        ALog.e(TAG, "groupForwardPrivateMessage error", it)
                                        sendFinish()
                                    })
                        }
                        AmeGroupMessage.CONTACT -> {
                            ALog.d(TAG, "GroupMessage is a contact")
                            val m = groupMessage.message.content as AmeGroupMessage.ContactContent
                            val contentStr = AmeGroupMessage(AmeGroupMessage.CONTACT, AmeGroupMessage.ContactContent(m.nickName, m.uid, m.url)).toString()
                            val contactMessage = OutgoingLocationMessage(message.recipient, contentStr,
                                    message.recipient.expireMessages * 1000L)
                            Observable.create<Long> {

                                it.onNext(MessageSender.send(AppContextHolder.APP_CONTEXT,
                                        contactMessage, threadId, null))
                                it.onComplete()

                            }.subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe({
                                        sendFinish()
                                    }, {
                                        ALog.e(TAG, "groupForwardPrivateMessage error", it)
                                        sendFinish()
                                    })
                        }

                        AmeGroupMessage.NEWSHARE_CHANNEL -> {
                            ALog.d(TAG, "GroupMessage is a contact")
                            val m = groupMessage.message.content as AmeGroupMessage.NewShareChannelContent
                            val contentStr = AmeGroupMessage(AmeGroupMessage.NEWSHARE_CHANNEL,m).toString()
                            val contactMessage = OutgoingLocationMessage(message.recipient, contentStr,
                                    message.recipient.expireMessages * 1000L)

                            Observable.create<Long> {

                                it.onNext(MessageSender.send(AppContextHolder.APP_CONTEXT,
                                        contactMessage, threadId, null))
                                it.onComplete()

                            }.subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe({
                                        sendFinish()
                                    }, {
                                        ALog.e(TAG, "groupForwardPrivateMessage error", it)
                                        sendFinish()
                                    })
                        }
                        AmeGroupMessage.CHAT_REPLY -> {
                            ALog.d(TAG, "GroupMessage is a reply")
                            val m = groupMessage.message.content as AmeGroupMessage.ReplyContent
                            val body = m.text
                            val textMessage = OutgoingEncryptedMessage(message.recipient, body, message.recipient.expireMessages * 1000L)

                            Observable.create<Long> {

                                it.onNext(MessageSender.send(AppContextHolder.APP_CONTEXT,
                                        textMessage, threadId, null))
                                it.onComplete()

                            }.subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe({
                                        sendFinish()
                                    }, {
                                        ALog.e(TAG, "groupForwardPrivateMessage error", it)
                                        sendFinish()
                                    })
                        }
                        AmeGroupMessage.GROUP_SHARE_CARD -> {
                            ALog.d(TAG, "GroupMessage is a group share card")
                            val m = groupMessage.message.content as AmeGroupMessage.GroupShareContent
                            val body = AmeGroupMessage(AmeGroupMessage.GROUP_SHARE_CARD, m).toString()
                            val textMessage = OutgoingLocationMessage(message.recipient, body, message.recipient.expireMessages * 1000L)

                            Observable.create<Long> {

                                it.onNext(MessageSender.send(AppContextHolder.APP_CONTEXT,
                                        textMessage, threadId, null))
                                it.onComplete()

                            }.subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe({
                                        sendFinish()
                                    }, {
                                        ALog.e(TAG, "groupForwardPrivateMessage error", it)
                                        sendFinish()
                                    })
                        }
                    }
                    sendPrivateCommentMessage(message.commentText, message.recipient, message.masterSecret, threadId)
                }
            } catch (e: Exception) {
                ALog.e(TAG, "groupForwardPrivateMessage error", e)
                sendFinish()
            }
        }
    }

    /**
     * group forward to group
     */
    private fun groupForwardGroupMessage(message: ForwardOnceMessage) {
        ALog.d(TAG, "Group forward to group")
        try {if (message.groupMessage != null && message.masterSecret != null) {
                val groupMessage = message.groupMessage
                val masterSecret = message.masterSecret
            val groupId = GroupUtil.gidFromAddress(message.recipient.address)
            when (groupMessage.message.type) {
                AmeGroupMessage.TEXT -> {
                    ALog.d(TAG, "GroupMessage is a text")
                    val m = groupMessage.message.content as AmeGroupMessage.TextContent
                    GroupMessageLogic.messageSender.sendTextMessage(groupId, m.text, groupMessageCallback)
                }
                AmeGroupMessage.LINK -> {
                    ALog.d(TAG, "GroupMessage is a link")
                    val m = groupMessage.message.content as AmeGroupMessage.LinkContent
                    GroupMessageLogic.messageSender.sendTextMessage(groupId, m.url, groupMessageCallback)
                }
                AmeGroupMessage.AUDIO -> {
                }
                AmeGroupMessage.IMAGE -> {
                    ALog.d(TAG, "GroupMessage is an image")
                    Observable.create<Unit> {
                        downloadGroupFile(groupMessage) { uri ->
                            val content = groupMessage.message.content as AmeGroupMessage.ImageContent
                            val newContent = AmeGroupMessage.ImageContent(
                                    width = content.width,
                                    height = content.height,
                                    mimeType = content.mimeType,
                                    size = content.size
                            )
                            val path = BcmFileUtils.getFileAbsolutePath(AppContextHolder.APP_CONTEXT, uri)
                            GroupMessageLogic.messageSender.sendImageMessage(masterSecret, groupId, newContent, uri, path ?: "", groupMessageCallback)
                            it.onComplete()
                        }
                    }.subscribeOn(Schedulers.io())
                            .subscribe()
                }
                AmeGroupMessage.FILE -> {
                    ALog.d(TAG, "GroupMessage is a file")
                    Observable.create<Unit> {
                        downloadGroupFile(groupMessage) { uri ->
                            val content = groupMessage.message.content as AmeGroupMessage.FileContent
                            val newContent = AmeGroupMessage.FileContent(
                                    url = "",
                                    fileName = content.fileName,
                                    size = content.size,
                                    mimeType = content.mimeType
                            )
                            val path = BcmFileUtils.getFileAbsolutePath(AppContextHolder.APP_CONTEXT, uri)
                            GroupMessageLogic.messageSender.sendDocumentMessage(masterSecret, groupId, newContent, path ?: "", groupMessageCallback)
                            it.onComplete()
                        }
                    }.subscribeOn(Schedulers.io())
                            .subscribe()
                }
                AmeGroupMessage.VIDEO -> {
                    ALog.d(TAG, "GroupMessage is a video")
                    Observable.create<Unit> {
                        downloadGroupFile(groupMessage) { uri ->
                            val content = groupMessage.message.content as AmeGroupMessage.VideoContent
                            val newContent = AmeGroupMessage.VideoContent(
                                    mimeType = content.mimeType,
                                    size = content.size,
                                    duration = content.duration
                            )
                            val path = BcmFileUtils.getFileAbsolutePath(AppContextHolder.APP_CONTEXT, uri)
                            GroupMessageLogic.messageSender.sendVideoMessage(masterSecret, groupId, uri, newContent, path, groupMessageCallback)
                            it.onComplete()
                        }
                    }.subscribeOn(Schedulers.io())
                            .subscribe()
                }
                AmeGroupMessage.LOCATION -> {
                    ALog.d(TAG, "GroupMessage is a location")
                    val m = groupMessage.message.content as AmeGroupMessage.LocationContent
                    GroupMessageLogic.messageSender.sendLocationMessage(groupId, m.latitude, m.longtitude, m.mapType, m.title, m.address, groupMessageCallback)
                }
                AmeGroupMessage.CONTACT -> {
                    ALog.d(TAG, "GroupMessage is a contact")
                    val m = groupMessage.message.content as AmeGroupMessage.ContactContent
                    GroupMessageLogic.messageSender.sendContactMessage(groupId, m, groupMessageCallback)
                }
                AmeGroupMessage.NEWSHARE_CHANNEL ->{
                    ALog.d(TAG, "GroupMessage is a contact")
                    val m = groupMessage.message.content as AmeGroupMessage.NewShareChannelContent
                    GroupMessageLogic.messageSender.sendShareChannelMessage(groupId, m, groupMessageCallback)
                }
                AmeGroupMessage.CHAT_REPLY -> {
                    ALog.d(TAG, "GroupMessage is a reply")
                    val m = groupMessage.message.content as AmeGroupMessage.ReplyContent
                    GroupMessageLogic.messageSender.sendTextMessage(groupId, m.text, groupMessageCallback, groupMessage.extContent)
                }
                AmeGroupMessage.GROUP_SHARE_CARD -> {
                    ALog.d(TAG, "GroupMessage is a group share card")
                    val m = groupMessage.message.content as AmeGroupMessage.GroupShareContent
                    GroupMessageLogic.messageSender.sendGroupShareMessage(groupId, m, groupMessageCallback)
                }
            }
            sendGroupCommentMessage(message.commentText, groupId)}

        } catch (e: Exception) {
            ALog.e(TAG, "groupForwardGroupMessage error", e)
            groupMessageCallback.call(null, 0, false)
        }
    }

    private val groupMessageCallback = object : com.bcm.messenger.chats.group.logic.MessageSender.SenderCallback {
        override fun call(messageDetail: AmeGroupMessageDetail?, indexId: Long, isSuccess: Boolean) {
            sendFinish()
        }
    }

    private val privateToGroupCallback = object : com.bcm.messenger.chats.group.logic.MessageSender.SenderCallback {
        override fun call(messageDetail: AmeGroupMessageDetail?, indexId: Long, isSuccess: Boolean) {
            if (filePaths.isNotEmpty()) {
                val path = filePaths.removeAt(0)
                File(path).delete()
            }
            sendFinish()
        }
    }

    private fun sendFinish() {
        sendingFlag = false
        val m = getOnceMessage()
        if (m != null) {
            sendOnceMessage(m)
        }
    }

    private fun downloadGroupFile(msg: AmeGroupMessageDetail, callback: (uri: Uri) -> Unit) {

        MessageFileHandler.downloadAttachment(msg, object : MessageFileHandler.MessageFileCallback {
            override fun onResult(success: Boolean, uri: Uri?) {
                callback(uri ?: Uri.EMPTY)
            }
        })
    }

    fun sendGroupCommentMessage(commentText: String?, groudId: Long) {
        if (!commentText.isNullOrEmpty()) {
            AmeDispatcher.io.dispatch({
                GroupMessageLogic.messageSender.sendTextMessage(groudId, commentText, null)

            }, 200)
        }
    }

    fun sendPrivateCommentMessage(commentText: String?, recipient: Recipient, masterSecret: MasterSecret, threadId: Long) {
        if (commentText?.isEmpty() == false) {
            AmeDispatcher.io.dispatch({
                val commentMessage = OutgoingEncryptedMessage(recipient, commentText, recipient.expireMessages * 1000L)
                MessageSender.send(AppContextHolder.APP_CONTEXT,
                        commentMessage, threadId, null)
            }, 200)
        }
    }
}
