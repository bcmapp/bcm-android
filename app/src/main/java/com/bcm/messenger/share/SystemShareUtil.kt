package com.bcm.messenger.share

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import com.bcm.messenger.chats.group.logic.GroupMessageLogic
import com.bcm.messenger.chats.group.logic.MessageSender
import com.bcm.messenger.chats.thread.ThreadListViewModel
import com.bcm.messenger.chats.util.AttachmentUtils
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.database.repositories.ThreadRepo
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.mms.OutgoingMediaMessage
import com.bcm.messenger.common.mms.OutgoingSecureMediaMessage
import com.bcm.messenger.common.mms.SlideDeck
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.sms.OutgoingEncryptedMessage
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.GroupUtil
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.io.FileOutputStream

/**
 * System share util, analyse intend and send messages.
 *
 * Created by Kin on 2018/10/8
 */

object SystemShareUtil {
    private fun sendTextToPrivateChat(threadId: Long, recipient: Recipient, masterSecret: MasterSecret, body: String, callback: (() -> Unit)? = null) {
        val textMessage = OutgoingEncryptedMessage(recipient, body, recipient.expireMessages * 1000L)
        Observable.create<Long> {
            it.onNext(com.bcm.messenger.chats.privatechat.logic.MessageSender.send(AppContextHolder.APP_CONTEXT, masterSecret.accountContext,
                    textMessage, threadId, null))
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback?.invoke()
                }, {
                    ALog.e("SystemShareUtil", "sendTextToPrivateChat error", it)
                    callback?.invoke()
                })
    }

    private fun sendImageToPrivateChat(threadId: Long, recipient: Recipient, masterSecret: MasterSecret, uri: Uri, callback: (() -> Unit)? = null) {
        val deck = SlideDeck()
        deck.addSlide(AttachmentUtils.getImageSlide(AppContextHolder.APP_CONTEXT, uri)?:return)

        val secureMessage = OutgoingSecureMediaMessage(OutgoingMediaMessage(recipient, deck, "", AmeTimeUtil.getMessageSendTime(),
                -1, recipient.expireMessages * 1000L, ThreadRepo.DistributionTypes.CONVERSATION))


        Observable.create<Long> {
            it.onNext(com.bcm.messenger.chats.privatechat.logic.MessageSender.send(AppContextHolder.APP_CONTEXT, masterSecret,
                    secureMessage, threadId, null))
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback?.invoke()
                }, {
                    ALog.e("SystemShareUtil", "sendImageToPrivateChat error", it)
                    callback?.invoke()
                })
    }

    private fun sendFileToPrivateChat(threadId: Long, recipient: Recipient, masterSecret: MasterSecret, uri: Uri, callback: (() -> Unit)? = null) {
        val deck = SlideDeck()
        deck.addSlide(AttachmentUtils.getDocumentSlide(AppContextHolder.APP_CONTEXT, uri)?:return)

        val secureMessage = OutgoingSecureMediaMessage(OutgoingMediaMessage(recipient, deck, "", AmeTimeUtil.getMessageSendTime(),
                -1, recipient.expireMessages * 1000L, ThreadRepo.DistributionTypes.CONVERSATION))

        Observable.create<Long> {
            it.onNext(com.bcm.messenger.chats.privatechat.logic.MessageSender.send(AppContextHolder.APP_CONTEXT, masterSecret,
                    secureMessage, threadId, null))
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback?.invoke()
                }, {
                    ALog.e("SystemShareUtil", "sendFileToPrivateChat error", it)
                    callback?.invoke()
                })
    }

    private fun sendTextToGroupChat(groupId: Long, body: String, callback: (() -> Unit)? = null) {
        Observable.create<Unit> {
            GroupMessageLogic.get(AMELogin.majorContext).messageSender.sendTextMessage(groupId,  body, object : MessageSender.SenderCallback {
                override fun call(messageDetail: AmeGroupMessageDetail?, indexId: Long, isSuccess: Boolean) {
                    it.onComplete()
                }
            })
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete {
                    callback?.invoke()
                }
                .subscribe()
    }

    private fun sendImageToGroupChat(masterSecret: MasterSecret, groupId: Long, uri: Uri, type: String, callback: (() -> Unit)? = null) {
        Observable.create<Unit> {
            val triple = getGroupContent(uri, type)
            val content = triple.third as AmeGroupMessage.ImageContent
            GroupMessageLogic.get(AMELogin.majorContext).messageSender.sendImageMessage(masterSecret, groupId, content, Uri.fromFile(File(triple.second)), triple.second, object : MessageSender.SenderCallback {
                override fun call(messageDetail: AmeGroupMessageDetail?, indexId: Long, isSuccess: Boolean) {
                    it.onComplete()
                }
            })
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete {
                    callback?.invoke()
                }
                .subscribe()
    }

    private fun sendVideoToGroupChat(masterSecret: MasterSecret, groupId: Long, uri: Uri, type: String, callback: (() -> Unit)? = null) {
        Observable.create<Unit> {
            val triple = getGroupContent(uri, type)
            val content = triple.third as AmeGroupMessage.VideoContent
            GroupMessageLogic.get(AMELogin.majorContext).messageSender.sendVideoMessage(masterSecret, groupId, Uri.fromFile(File(triple.second)), content, triple.second, object : MessageSender.SenderCallback {
                override fun call(messageDetail: AmeGroupMessageDetail?, indexId: Long, isSuccess: Boolean) {
                    it.onComplete()
                }
            })
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete {
                    callback?.invoke()
                }
                .subscribe()
    }

    private fun sendFileToGroupChat(masterSecret: MasterSecret, groupId: Long, uri: Uri, type: String, callback: (() -> Unit)? = null) {
        Observable.create<Unit> {
            val triple = getGroupContent(uri, type)
            val content = triple.third as AmeGroupMessage.FileContent
            GroupMessageLogic.get(AMELogin.majorContext).messageSender.sendDocumentMessage(masterSecret, groupId, content, triple.second, object : MessageSender.SenderCallback {
                override fun call(messageDetail: AmeGroupMessageDetail?, indexId: Long, isSuccess: Boolean) {
                    it.onComplete()
                }
            })
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete {
                    callback?.invoke()
                }
                .subscribe()
    }

    fun shareToPrivateChat(intent: Intent, recipient: Recipient, masterSecret: MasterSecret, callback: () -> Unit) {
        ThreadListViewModel.getThreadId(recipient) { id ->
            if (intent.action == Intent.ACTION_SEND) {
                val type = intent.type ?: ""
                when {
                    type.startsWith("text/") -> {
                        val body = intent.getStringExtra(Intent.EXTRA_TEXT)
                        if (body != null) {
                            sendTextToPrivateChat(id, recipient, masterSecret, body, callback)
                        } else {
                            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                            sendFileToPrivateChat(id, recipient, masterSecret, uri, callback)
                        }
                    }
                    type.startsWith("image/") -> {
                        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                        sendImageToPrivateChat(id, recipient, masterSecret, uri, callback)

                        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                        if (!text.isNullOrBlank()) {
                            sendTextToPrivateChat(id, recipient, masterSecret, text, null)
                        }
                    }
                    else -> {
                        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                        sendFileToPrivateChat(id, recipient, masterSecret, uri, callback)

                        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                        if (!text.isNullOrBlank()) {
                            sendTextToPrivateChat(id, recipient, masterSecret, text, null)
                        }
                    }
                }
            } else {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                uris.forEachIndexed { index, uri ->
                    val type = if (uri.scheme == "content") {
                        AppContextHolder.APP_CONTEXT.contentResolver.getType(uri)
                    } else {
                        val path = uri.path
                        if (path?.isNotEmpty() == true) {
                            val file = File(path)
                            AttachmentUtils.getMimeType(file.name)
                        } else {
                            "application/octet-stream"
                        }
                    }
                    when {
                        type.startsWith("text/") -> {
                            val body = intent.getStringExtra(Intent.EXTRA_TEXT)
                            sendTextToPrivateChat(id, recipient, masterSecret, body, if (index == uris.size - 1) callback else null)
                        }
                        type.startsWith("image/") -> {
                            sendImageToPrivateChat(id, recipient, masterSecret, uri, if (index == uris.size - 1) callback else null)
                        }
                        else -> {
                            sendFileToPrivateChat(id, recipient, masterSecret, uri, if (index == uris.size - 1) callback else null)
                        }
                    }
                }

                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!text.isNullOrBlank()) {
                    sendTextToPrivateChat(id, recipient, masterSecret, text, null)
                }
                callback()
            }
        }
    }

    fun shareCommentToPrivateChat(comment: String, recipient: Recipient, masterSecret: MasterSecret) {
        ThreadListViewModel.getThreadId(recipient) { id ->
            sendTextToPrivateChat(id, recipient, masterSecret, comment)
        }
    }

    fun shareToGroupChat(masterSecret: MasterSecret, intent: Intent, recipient: Recipient, callback: () -> Unit) {
        val groupId = GroupUtil.gidFromAddress(recipient.address)
        if (intent.action == Intent.ACTION_SEND) {
            val type = intent.type ?: ""
            when {
                type.startsWith("text/") -> {
                    val body = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (body != null) {
                        sendTextToGroupChat(groupId, body, callback)
                    } else {
                        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                        sendFileToGroupChat(masterSecret, groupId, uri, type, callback)
                    }
                }
                type.startsWith("image/") -> {
                    val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    sendImageToGroupChat(masterSecret, groupId, uri, type, callback)

                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!text.isNullOrBlank()) {
                        sendTextToGroupChat(groupId, text, null)
                    }
                }
                type.startsWith("video/") -> {
                    val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    sendVideoToGroupChat(masterSecret, groupId, uri, type, callback)

                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!text.isNullOrBlank()) {
                        sendTextToGroupChat(groupId, text, null)
                    }
                }
                else -> {
                    val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    sendFileToGroupChat(masterSecret, groupId, uri, type, callback)

                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!text.isNullOrBlank()) {
                        sendTextToGroupChat(groupId, text, null)
                    }
                }
            }
        } else {
            val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            uris.forEachIndexed { index, uri ->
                val type = if (uri.scheme == "content") {
                    AppContextHolder.APP_CONTEXT.contentResolver.getType(uri)
                } else {
                    val path = uri.path
                    if (path?.isNotEmpty() == true) {
                        val file = File(path)
                        AttachmentUtils.getMimeType(file.name)
                    } else {
                        "application/octet-stream"
                    }
                }
                when {
                    type.startsWith("text/") -> {
                        val body = intent.getStringExtra(Intent.EXTRA_TEXT)
                        sendTextToGroupChat(groupId, body, if (index == uris.size - 1) callback else null)
                    }
                    type.startsWith("image/") -> {
                        sendImageToGroupChat(masterSecret, groupId, uri, type, if (index == uris.size - 1) callback else null)
                    }
                    type.startsWith("video/") -> {
                        sendVideoToGroupChat(masterSecret, groupId, uri, type, if (index == uris.size - 1) callback else null)
                    }
                    else -> {
                        sendFileToGroupChat(masterSecret, groupId, uri, type, if (index == uris.size - 1) callback else null)
                    }
                }
            }

            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                sendTextToGroupChat(groupId, text, null)
            }
        }
    }

    fun shareCommentToGroupChat(comment: String, recipient: Recipient) {
        val groupId = GroupUtil.gidFromAddress(recipient.address)
        sendTextToGroupChat(groupId, comment)
    }

    /**
     * @param uri Shared data uri
     * @param type data Mimetype
     * @return Triple<FileUri, FilePath, MessageContent>
     */
    private fun getGroupContent(uri: Uri, type: String): Triple<Uri, String, AmeGroupMessage.Content?> {
        return try {
            when {
                type.startsWith("image/") -> {
                    if (uri.scheme == "file") {
                        val file = File(uri.path)
                        val opts = BitmapFactory.Options()
                        opts.inJustDecodeBounds = true
                        opts.inSampleSize = 1
                        BitmapFactory.decodeFile(uri.path, opts)
                        Triple(uri, uri.path, AmeGroupMessage.ImageContent("", opts.outWidth, opts.outHeight, AttachmentUtils.getMimeType(file.name),
                                "", uri.path ?: "", "", file.length()))
                    } else {
                        val path = BcmFileUtils.getFileAbsolutePath(AMELogin.majorContext, AppContextHolder.APP_CONTEXT, uri)
                                ?: ""
                        val content = AttachmentUtils.getAttachmentContent(AMELogin.majorContext, AppContextHolder.APP_CONTEXT, uri, path)
                        Triple(uri, path, content)
                    }
                }
                type.startsWith("video/") -> {
                    if (uri.scheme == "file") {
                        Triple(uri, uri.path, AttachmentUtils.getAttachmentContent(AMELogin.majorContext, AppContextHolder.APP_CONTEXT, uri, uri.path))
                    } else {
                        val path = BcmFileUtils.getFileAbsolutePath(AMELogin.majorContext, AppContextHolder.APP_CONTEXT, uri)
                                ?: ""
                        val content = AttachmentUtils.getAttachmentContent(AMELogin.majorContext, AppContextHolder.APP_CONTEXT, uri, path)
                        Triple(uri, path, content)
                    }
                }
                else -> {
                    if (uri.scheme == "file") {
                        val file = File(uri.path)
                        Triple(uri, uri.path, AmeGroupMessage.FileContent("", file.name, file.length(), AttachmentUtils.getMimeType(file.name)))
                    } else {
                        val path = BcmFileUtils.getFileAbsolutePath(AMELogin.majorContext, AppContextHolder.APP_CONTEXT, uri)
                                ?: ""
                        val content = AttachmentUtils.getAttachmentContent(AMELogin.majorContext, AppContextHolder.APP_CONTEXT, uri, path)
                        Triple(uri, path, content)
                    }
                }
            }
        } catch (e: Exception) {
            val file = File("${AppContextHolder.APP_CONTEXT.cacheDir}/sent/${uri.lastPathSegment}")
            if (!file.exists()) {
                val folder = File("${AppContextHolder.APP_CONTEXT.cacheDir}/sent/")
                if (!folder.exists()) {
                    folder.mkdirs()
                }
                file.createNewFile()
            }
            val fos = FileOutputStream(file)
            val stream = AppContextHolder.APP_CONTEXT.contentResolver.openInputStream(uri)
            stream?.copyTo(fos)
            stream?.close()
            fos.close()
            when {
                type.startsWith("image/*") -> {
                    val opts = BitmapFactory.Options()
                    opts.inJustDecodeBounds = true
                    opts.inSampleSize = 1
                    BitmapFactory.decodeFile(file.path, opts)
                    Triple(Uri.fromFile(file), file.path, AmeGroupMessage.ImageContent("", opts.outWidth, opts.outHeight, AttachmentUtils.getMimeType(file.name), "", file.path, "", file.length()))
                }
                type.startsWith("video/*") -> {
                    val fileUri = Uri.fromFile(file)
                    Triple(fileUri, file.absolutePath, AttachmentUtils.getAttachmentContent(AMELogin.majorContext, AppContextHolder.APP_CONTEXT, fileUri, file.absolutePath))
                }
                else -> {
                    Triple(Uri.fromFile(file), file.path, AmeGroupMessage.FileContent("", file.name, file.length(), AttachmentUtils.getMimeType(file.name)))
                }
            }
        }
    }
}