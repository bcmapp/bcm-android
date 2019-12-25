package com.bcm.messenger.chats.forward

import android.annotation.SuppressLint
import android.content.Context
import android.util.Size
import com.bcm.messenger.chats.group.logic.MessageFileHandler
import com.bcm.messenger.common.core.AmeFileUploader
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.corebean.GroupKeyParam
import com.bcm.messenger.common.core.corebean.HistoryMessageDetail
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.crypto.MasterSecretUtil
import com.bcm.messenger.common.crypto.MediaKey
import com.bcm.messenger.common.database.records.AttachmentRecord
import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.grouprepository.manager.GroupInfoDataManager
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.MediaUtil
import com.bcm.messenger.common.utils.base64Encode
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.crypto.encrypt.GroupMessageEncryptUtils
import com.bcm.messenger.common.utils.format
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.Base64
import com.bcm.messenger.utility.BitmapUtils
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.*
import kotlin.collections.ArrayList

object ForwardMessageEncapsulator {

    private const val GROUPEN_ENCRYPT_TYPE = 0
    private const val PRIVATE_ENCRYPT_TYPE = 1
    private const val NOT_ENCRYPT = -1
    const val TAG = "ForwardEncapsulator"

    // group message forward list
    fun encapsulateGroupHistoryMessages(gid: Long, messageList: List<AmeGroupMessageDetail>): List<HistoryMessageDetail> {
        val messageDetailList = LinkedList<HistoryMessageDetail>()
        for (message in messageList) {
            val historyMessage = HistoryMessageDetail()

            val content = message.message.content
            val keyParam: GroupKeyParam = GroupInfoDataManager.queryGroupKeyParam(gid, message.keyVersion)?:return listOf()
            val groupKey = keyParam.key.base64Encode().format()
            when (content) {
                is AmeGroupMessage.LinkContent -> historyMessage.messagePayload = AmeGroupMessage(AmeGroupMessage.TEXT, AmeGroupMessage.TextContent(content.url)).toString()
                is AmeGroupMessage.ReplyContent -> historyMessage.messagePayload = AmeGroupMessage(AmeGroupMessage.TEXT, AmeGroupMessage.TextContent(content.text)).toString()
                else -> historyMessage.messagePayload = message.message.toString()
            }
            historyMessage.sendTime = message.sendTime
            historyMessage.sender = message.senderId
            historyMessage.attachmentPsw = HistoryMessageDetail.PswBean()
            historyMessage.thumbPsw = HistoryMessageDetail.PswBean()
            val messageContent: AmeGroupMessage.Content = message.message.content


            historyMessage.attachmentPsw!!.type = GROUPEN_ENCRYPT_TYPE
            historyMessage.thumbPsw!!.type = GROUPEN_ENCRYPT_TYPE
            when (message.message.type) {
                AmeGroupMessage.IMAGE -> {
                    messageContent as AmeGroupMessage.ImageContent
                    historyMessage.attachmentPsw!!.psw = computeFilePsw(groupKey, messageContent.sign)
                    if (!messageContent.sign_thumbnail.isNullOrBlank()) {
                        historyMessage.thumbPsw!!.psw = computeFilePsw(groupKey, messageContent.sign_thumbnail)
                    }
                }
                AmeGroupMessage.VIDEO -> {
                    messageContent as AmeGroupMessage.VideoContent
                    historyMessage.attachmentPsw!!.psw = computeFilePsw(groupKey, messageContent.sign)
                    historyMessage.thumbPsw!!.psw = computeFilePsw(groupKey, messageContent.sign_thumbnail)
                }
                AmeGroupMessage.AUDIO -> {
                    messageContent as AmeGroupMessage.AudioContent
                    historyMessage.attachmentPsw!!.psw = computeFilePsw(groupKey, messageContent.sign)
                }
                AmeGroupMessage.FILE -> {
                    messageContent as AmeGroupMessage.FileContent
                    historyMessage.attachmentPsw!!.psw = computeFilePsw(groupKey, messageContent.sign)
                }
            }

            messageDetailList.add(historyMessage)

        }
        return messageDetailList
    }

    @SuppressLint("CheckResult")
    fun uploadMessageFilesIfNeed(masterSecret: MasterSecret, messageList: List<AmeGroupMessageDetail>, result: (succeed: Boolean) -> Unit) {
        val uploadList = ArrayList<Observable<Pair<MessageFileHandler.UploadResult?, MessageFileHandler.UploadResult?>>>()
        for (message in messageList) {
            if (message.isSendByMe) {

                val path = if (!message.isAttachmentComplete) {
                    ""
                } else {
                    BcmFileUtils.getFileAbsolutePath(AppContextHolder.APP_CONTEXT, message.toAttachmentUri())
                }
                if (path?.isNotEmpty() == true) {
                    try {
                        if (message.message.isImage() && (message.message.content as AmeGroupMessage.ImageContent).url.isEmpty()) {
                            val observable = Observable.create(ObservableOnSubscribe<Pair<MessageFileHandler.UploadResult?, MessageFileHandler.UploadResult?>> {
                                MessageFileHandler.uploadImage(masterSecret, message.gid, path) { urlResult, thumbResult ->
                                    if (null != urlResult && null != thumbResult) {
                                        val content = message.message.content as AmeGroupMessage.ImageContent
                                        content.url = urlResult.url
                                        content.sign = urlResult.sign
                                        content.thumbnail_url = thumbResult.url
                                        content.sign_thumbnail = thumbResult.sign

                                        MessageDataManager.updateMessageContent(message.gid, message.indexId, message.message.toString())
                                        it.onNext(Pair(urlResult, thumbResult))
                                    } else {
                                        it.onError(Exception("upload failed $path"))
                                    }

                                    it.onComplete()
                                }
                            }).subscribeOn(Schedulers.io())
                            uploadList.add(observable)
                        } else if (message.message.isVideo() && (message.message.content as AmeGroupMessage.VideoContent).url.isEmpty()) {
                            val observable = Observable.create(ObservableOnSubscribe<Pair<MessageFileHandler.UploadResult?, MessageFileHandler.UploadResult?>> {
                                MessageFileHandler.uploadVideo(masterSecret, message.gid, path) { urlResult, thumbResult ->
                                    if (null != urlResult && thumbResult != null) {
                                        val content = message.message.content as AmeGroupMessage.VideoContent
                                        content.url = urlResult.url
                                        content.sign = urlResult.sign
                                        content.thumbnail_url = thumbResult.url
                                        content.sign_thumbnail = thumbResult.sign
                                        content.thumbnail_width = thumbResult.width
                                        content.thumbnail_height = thumbResult.height

                                        MessageDataManager.updateMessageContent(message.gid, message.indexId, message.message.toString())
                                        it.onNext(Pair(urlResult, thumbResult))
                                    } else {
                                        it.onError(Exception("upload failed $path"))
                                    }

                                    it.onComplete()
                                }
                            }).subscribeOn(Schedulers.io())
                            uploadList.add(observable)
                        } else if (message.message.isFile() && (message.message.content as AmeGroupMessage.FileContent).url.isEmpty()) {
                            val observable = Observable.create(ObservableOnSubscribe<Pair<MessageFileHandler.UploadResult?, MessageFileHandler.UploadResult?>> {
                                MessageFileHandler.uploadFile(masterSecret, message.gid, message.indexId, path) { urlResult ->
                                    if (null != urlResult) {
                                        val content = message.message.content as AmeGroupMessage.FileContent
                                        content.url = urlResult.url
                                        content.sign = urlResult.sign

                                        MessageDataManager.updateMessageContent(message.gid, message.indexId, message.message.toString())
                                        it.onNext(Pair(urlResult, null))
                                    } else {
                                        it.onError(Exception("upload failed $path"))
                                    }

                                    it.onComplete()
                                }
                            }).subscribeOn(Schedulers.io())
                            uploadList.add(observable)
                        }
                    } catch (e: NullPointerException) {
                        ALog.e(TAG, e)
                    }
                }
            }

        }
        if (uploadList.isNotEmpty()) {
            Observable.zip(uploadList) {
                if (it.size == uploadList.size) {
                    result(true)
                }
            }.subscribeOn(Schedulers.io()).observeOn(Schedulers.io())
                    .subscribe({
                        ALog.i(TAG, "xxxx")
                    }, {
                        result(false)
                        ALog.i(TAG, "xxxx bbb")
                    }, {
                        ALog.i(TAG, "xxxx ccc")
                    })

        } else {
            result(true)
        }
    }


    class PrepareMessage(var hasPrepared: Boolean, val message: MessageRecord, var historyMessage: HistoryMessageDetail, var ameMessage: AmeGroupMessage<*>?)

    /**
     * Upload the files not uploaded in the private chat message to the server
     */
    @SuppressLint("CheckResult")
    private fun uploadPrivateHistoryFiles(prepareList: List<PrepareMessage>, result: (succeed: Boolean, historyList: List<HistoryMessageDetail>) -> Unit) {
        val historyList = ArrayList<HistoryMessageDetail>(prepareList.count())
        val uploadList = ArrayList<Observable<String>>()
        for (prepare in prepareList) {
            historyList.add(prepare.historyMessage)
            if (!prepare.hasPrepared) {
                when (prepare.ameMessage?.type) {
                    AmeGroupMessage.VIDEO -> {
                        var needUpload = false
                        val c = prepare.ameMessage!!.content as AmeGroupMessage.VideoContent
                        var psw = prepare.historyMessage.thumbPsw?.psw
                        if (!c.thumbnail_url.startsWith("http")) {
                            val observable = uploadProxyObserver(c.thumbnail_url, psw) {
                                c.thumbnail_url = it
                            }
                            uploadList.add(observable)
                            needUpload = true
                        }

                        psw = prepare.historyMessage.attachmentPsw?.psw
                        if (!c.url.startsWith("http")) {
                            val observable = uploadProxyObserver(c.url, psw) {
                                c.url = it
                            }
                            uploadList.add(observable)
                            needUpload = true
                        }

                        if (!needUpload) {
                            prepare.historyMessage.messagePayload = prepare.ameMessage.toString()
                        }
                    }
                    AmeGroupMessage.IMAGE -> {
                        var needUpload = false
                        val c = prepare.ameMessage!!.content as AmeGroupMessage.ImageContent
                        var psw = prepare.historyMessage.thumbPsw?.psw
                        if (!c.thumbnail_url.startsWith("http")) {
                            val observable = uploadProxyObserver(c.thumbnail_url, psw) {
                                c.thumbnail_url = it
                            }
                            uploadList.add(observable)
                            needUpload = true
                        }

                        psw = prepare.historyMessage.attachmentPsw?.psw
                        if (!c.url.startsWith("http")) {
                            val observable = uploadProxyObserver(c.url, psw) {
                                c.url = it
                            }
                            uploadList.add(observable)
                            needUpload = true
                        }

                        if (!needUpload) {
                            prepare.historyMessage.messagePayload = prepare.ameMessage.toString()
                        }
                    }
                    AmeGroupMessage.FILE -> {
                        val c = prepare.ameMessage!!.content as AmeGroupMessage.FileContent
                        val psw = prepare.historyMessage.attachmentPsw?.psw

                        if (!c.url.startsWith("http")) {
                            val observable = uploadProxyObserver(c.url, psw) {
                                c.url = it
                            }
                            uploadList.add(observable)
                        } else {
                            prepare.historyMessage.messagePayload = prepare.ameMessage.toString()
                        }
                    }
                }
            }
        }

        if (uploadList.isNotEmpty()) {
            Observable.zip(uploadList) {
                if (it.size == uploadList.size) {
                    for (prepare in prepareList) {
                        if (!prepare.hasPrepared) {
                            prepare.hasPrepared = true
                            prepare.historyMessage.messagePayload = prepare.ameMessage.toString()
                        }
                    }
                    result(true, historyList)
                }
            }.subscribeOn(Schedulers.io()).observeOn(Schedulers.io())
                    .subscribe({
                        ALog.i(TAG, "upload private file succeed!")
                    }, {
                        ALog.e(TAG, "upload private file failed!", it)
                        result(false, historyList)
                    })

        } else {
            result(true, historyList)
        }

    }

    private fun uploadProxyObserver(localPath: String, psw: String?, result: (url: String) -> Unit): Observable<String> {
        return Observable.create(ObservableOnSubscribe<String> {
            if (psw?.isNotEmpty() == true) {
                val destFile = createTempFile(AppContextHolder.APP_CONTEXT)

                BCMEncryptUtils.encryptFile(File(localPath), Base64.decode(psw), destFile)

                AmeFileUploader.uploadAttachmentToAws(AppContextHolder.APP_CONTEXT, AmeFileUploader.AttachmentType.GROUP_MESSAGE, destFile, object : AmeFileUploader.FileUploadCallback() {
                    override fun onUploadSuccess(url: String?, id: String?) {
                        result(url ?: "")
                        it.onNext(url ?: "")
                        it.onComplete()
                    }

                    override fun onUploadFailed(filePath: String, msg: String?) {
                        it.onError(Exception("upload private file failed"))
                    }

                })

            } else {
                it.onError(Exception("upload private file failed: psw null"))
            }
        }).subscribeOn(Schedulers.io())

    }

    //private message forward list
    fun encapsulatePrivateHistoryMessages(context: Context, masterSecret: MasterSecret, messageList: List<MessageRecord>, result: (succeed: Boolean, List<HistoryMessageDetail>) -> Unit) {
        ALog.i(TAG, "count :${messageList.count()}")
        val prepareMessageList = LinkedList<PrepareMessage>()
        for (message in messageList) {
            if (message.isFailed())
                continue
            if (!message.isMediaMessage()) {
                val history = smsMessage2HistoryMessage(message)
                prepareMessageList.add(PrepareMessage(true, message, history, null))
            } else {
                prepareMessageList.add(PrepareMessage(false, message, HistoryMessageDetail(), null))
            }
        }
        val afterPrepareMessages: List<PrepareMessage> = prepareDetailMessages(masterSecret, prepareMessageList)
        uploadPrivateHistoryFiles(afterPrepareMessages) { succeed, historyList ->
            result(succeed, historyList)
        }
    }

    private fun prepareDetailMessages(masterSecret: MasterSecret, prepareMessageList: LinkedList<PrepareMessage>): List<PrepareMessage> {
        ALog.d(TAG, "prepareDetailMessages begin")
        for (prepareMessage in prepareMessageList) {
            if (prepareMessage.hasPrepared) {
                continue
            }
            prepareOneMessage(masterSecret, prepareMessage)
        }
        ALog.d(TAG, "prepareDetailMessages end")
        return prepareMessageList
    }

    private fun prepareOneMessage(masterSecret: MasterSecret, prepareMessage: PrepareMessage) {
        val messageRecord = prepareMessage.message
        val attachmentRepo = Repository.getAttachmentRepo()
        val attachment = attachmentRepo.getAttachments(messageRecord.id)[0]
        prepareMessage.historyMessage.sendTime = messageRecord.dateSent

        if (messageRecord.isOutgoing()) {
            prepareMessage.historyMessage.sender = AMELogin.uid
        } else {
            prepareMessage.historyMessage.sender = messageRecord.getRecipient().address.serialize()
        }

        prepareMessage.historyMessage.attachmentPsw = HistoryMessageDetail.PswBean()
        prepareMessage.historyMessage.thumbPsw = HistoryMessageDetail.PswBean()
        prepareMessage.historyMessage.thumbPsw?.type = GROUPEN_ENCRYPT_TYPE
        prepareMessage.historyMessage.thumbPsw?.psw = EncryptUtils.getSecret(64)
        if (attachment.contentLocation.isNotEmpty()) {
            prepareMessage.historyMessage.attachmentPsw?.type = PRIVATE_ENCRYPT_TYPE
            prepareMessage.historyMessage.attachmentPsw?.psw = Base64.encodeBytes(MediaKey.getDecrypted(masterSecret, MasterSecretUtil.getAsymmetricMasterSecret(AppContextHolder.APP_CONTEXT, masterSecret), attachment.contentKey))
            if (MediaUtil.isGif(attachment.contentType)) {
                prepareMessage.historyMessage.thumbPsw?.type = PRIVATE_ENCRYPT_TYPE
                prepareMessage.historyMessage.thumbPsw?.psw = Base64.encodeBytes(MediaKey.getDecrypted(masterSecret, MasterSecretUtil.getAsymmetricMasterSecret(AppContextHolder.APP_CONTEXT, masterSecret), attachment.contentKey))
            }
        } else {
            prepareMessage.historyMessage.attachmentPsw?.type = GROUPEN_ENCRYPT_TYPE
            prepareMessage.historyMessage.attachmentPsw?.psw = EncryptUtils.getSecret(64)
        }

        val inputStream = attachmentRepo.getAttachmentStream(masterSecret, attachment.id, attachment.uniqueId) ?: return
        val originPlainFile = createTempFile(AppContextHolder.APP_CONTEXT)

        outputDataToFile(inputStream, originPlainFile)
        val fileSign = Base64.encodeBytes(EncryptUtils.encryptSHA512(FileInputStream(originPlainFile)))
        when {
            attachment.isVideo() -> {
                when {
                    !attachment.url.isNullOrEmpty() -> {
                        val attachmentUrl = attachment.url!!
                        val frameFilePath = BcmFileUtils.getVideoFramePath(AppContextHolder.APP_CONTEXT, BcmFileUtils.getFileUri(originPlainFile.absolutePath!!))
                        val thumbFilePath = BcmFileUtils.saveBitmap2File(BitmapUtils.compressImageForThumbnail(frameFilePath!!)!!)
                        val thumbSize: Size = getThumbSizeForImage(attachment, thumbFilePath!!)
                        val thumbnailSign = Base64.encodeBytes(EncryptUtils.encryptSHA512(FileInputStream(File(thumbFilePath))))
                        prepareMessage.ameMessage = AmeGroupMessage(AmeGroupMessage.VIDEO, AmeGroupMessage.VideoContent(attachmentUrl, attachment.contentType, attachment.dataSize, attachment.duration, Base64.encodeBytes(attachment.digest), thumbFilePath, thumbSize.width, thumbSize.height, thumbnailSign))
                    }
                    attachment.contentLocation.isNotEmpty() -> {
                        val attachmentUrl = "https://ameim.bs2dl.yy.com/attachments/${attachment.contentLocation}"
                        val frameFilePath = BcmFileUtils.getVideoFramePath(AppContextHolder.APP_CONTEXT, BcmFileUtils.getFileUri(originPlainFile.absolutePath!!))
                        val thumbFilePath = BcmFileUtils.saveBitmap2File(BitmapUtils.compressImageForThumbnail(frameFilePath!!)!!)
                        val thumbSize: Size = getThumbSizeForImage(attachment, thumbFilePath!!)
                        val thumbnailSign = Base64.encodeBytes(EncryptUtils.encryptSHA512(FileInputStream(File(thumbFilePath))))
                        prepareMessage.ameMessage = AmeGroupMessage(AmeGroupMessage.VIDEO, AmeGroupMessage.VideoContent(attachmentUrl, attachment.contentType, attachment.dataSize, attachment.duration, Base64.encodeBytes(attachment.digest), thumbFilePath, thumbSize.width, thumbSize.height, thumbnailSign))
                    }
                    else -> {
                        val attachmentUrl = originPlainFile.absolutePath
                        val frameFilePath = BcmFileUtils.getVideoFramePath(AppContextHolder.APP_CONTEXT, BcmFileUtils.getFileUri(originPlainFile.absolutePath!!))
                        val thumbFilePath = BcmFileUtils.saveBitmap2File(BitmapUtils.compressImageForThumbnail(frameFilePath!!)!!)
                        val thumbSize: Size = getThumbSizeForImage(attachment, thumbFilePath!!)
                        val thumbnailSign = Base64.encodeBytes(EncryptUtils.encryptSHA512(FileInputStream(File(thumbFilePath))))
                        prepareMessage.ameMessage = AmeGroupMessage(AmeGroupMessage.VIDEO, AmeGroupMessage.VideoContent(attachmentUrl, attachment.contentType, attachment.dataSize, attachment.duration, fileSign, thumbFilePath, thumbSize.width, thumbSize.height, thumbnailSign))
                    }
                }
            }
            attachment.isGif() -> {
                when {
                    !attachment.url.isNullOrEmpty() -> {
                        val attachmentUrl = attachment.url!!
                        val thumbSize: Size = getThumbSizeForImage(attachment, originPlainFile.absolutePath)
                        prepareMessage.ameMessage = AmeGroupMessage(AmeGroupMessage.IMAGE, AmeGroupMessage.ImageContent(attachmentUrl, thumbSize.width, thumbSize.height, attachment.contentType, Base64.encodeBytes(attachment.digest), attachmentUrl, Base64.encodeBytes(attachment.digest), attachment.dataSize))
                    }
                    attachment.contentLocation.isNotEmpty() -> {
                        val attachmentUrl = "https://ameim.bs2dl.yy.com/attachments/${attachment.contentLocation}"
                        val thumbSize: Size = getThumbSizeForImage(attachment, originPlainFile.absolutePath)
                        prepareMessage.ameMessage = AmeGroupMessage(AmeGroupMessage.IMAGE, AmeGroupMessage.ImageContent(attachmentUrl, thumbSize.width, thumbSize.height, attachment.contentType, Base64.encodeBytes(attachment.digest), attachmentUrl, Base64.encodeBytes(attachment.digest), attachment.dataSize))
                    }
                    else -> {
                        val attachmentUrl = originPlainFile.absolutePath
                        val thumbSize: Size = getThumbSizeForImage(attachment, originPlainFile.absolutePath)
                        prepareMessage.ameMessage = AmeGroupMessage(AmeGroupMessage.IMAGE, AmeGroupMessage.ImageContent(attachmentUrl, thumbSize.width, thumbSize.height, attachment.contentType, fileSign, attachmentUrl, fileSign, attachment.dataSize))
                    }
                }
            }
            attachment.isImage() -> {
                when {
                    !attachment.url.isNullOrEmpty() -> {
                        val attachmentUrl = attachment.url!!
                        val thumbFilePath = BcmFileUtils.saveBitmap2File(BitmapUtils.compressImageForThumbnail(originPlainFile.absoluteFile)!!)
                        val thumbSize: Size = getThumbSizeForImage(attachment, thumbFilePath!!)
                        val thumbnailSign = Base64.encodeBytes(EncryptUtils.encryptSHA512(FileInputStream(File(thumbFilePath))))
                        prepareMessage.ameMessage = AmeGroupMessage(AmeGroupMessage.IMAGE, AmeGroupMessage.ImageContent(attachmentUrl, thumbSize.width, thumbSize.height, attachment.contentType, Base64.encodeBytes(attachment.digest), thumbFilePath, thumbnailSign, attachment.dataSize))
                    }
                    attachment.contentLocation.isNotEmpty() -> {
                        val attachmentUrl = "https://ameim.bs2dl.yy.com/attachments/${attachment.contentLocation}"
                        val thumbFilePath = BcmFileUtils.saveBitmap2File(BitmapUtils.compressImageForThumbnail(originPlainFile.absoluteFile)!!)
                        val thumbSize: Size = getThumbSizeForImage(attachment, thumbFilePath!!)
                        val thumbnailSign = Base64.encodeBytes(EncryptUtils.encryptSHA512(FileInputStream(File(thumbFilePath))))
                        prepareMessage.ameMessage = AmeGroupMessage(AmeGroupMessage.IMAGE, AmeGroupMessage.ImageContent(attachmentUrl, thumbSize.width, thumbSize.height, attachment.contentType, Base64.encodeBytes(attachment.digest), thumbFilePath, thumbnailSign, attachment.dataSize))
                    }
                    else -> {
                        val attachmentUrl = originPlainFile.absolutePath
                        val thumbFilePath = BcmFileUtils.saveBitmap2File(BitmapUtils.compressImageForThumbnail(originPlainFile.absoluteFile)!!)
                        val thumbSize: Size = getThumbSizeForImage(attachment, thumbFilePath!!)
                        val thumbnailSign = Base64.encodeBytes(EncryptUtils.encryptSHA512(FileInputStream(File(thumbFilePath))))
                        prepareMessage.ameMessage = AmeGroupMessage(AmeGroupMessage.IMAGE, AmeGroupMessage.ImageContent(attachmentUrl, thumbSize.width, thumbSize.height, attachment.contentType, fileSign, thumbFilePath, thumbnailSign, attachment.dataSize))
                    }
                }
            }
            attachment.isDocument() -> {
                when {
                    !attachment.url.isNullOrEmpty() -> {
                        val attachmentUrl = attachment.url!!
                        prepareMessage.ameMessage = AmeGroupMessage(AmeGroupMessage.FILE, AmeGroupMessage.FileContent(attachmentUrl, attachment.fileName, attachment.dataSize, attachment.contentType, Base64.encodeBytes(attachment.digest)))
                    }
                    attachment.contentLocation.isNotEmpty() -> {
                        val attachmentUrl = "https://ameim.bs2dl.yy.com/attachments/${attachment.contentLocation}"
                        prepareMessage.ameMessage = AmeGroupMessage(AmeGroupMessage.FILE, AmeGroupMessage.FileContent(attachmentUrl, attachment.fileName, attachment.dataSize, attachment.contentType, Base64.encodeBytes(attachment.digest)))
                    }
                    else -> {
                        val attachmentUrl = originPlainFile.absolutePath
                        prepareMessage.ameMessage = AmeGroupMessage(AmeGroupMessage.FILE, AmeGroupMessage.FileContent(attachmentUrl, attachment.fileName, attachment.dataSize, attachment.contentType, fileSign))
                    }
                }
            }
            else -> {
                //
            }
        }

    }


    private fun smsMessage2HistoryMessage(message: MessageRecord): HistoryMessageDetail {
        val historyMessage = HistoryMessageDetail()
        if (message.isOutgoing()) {
            historyMessage.sender = AMELogin.uid
        } else {
            historyMessage.sender = message.getRecipient().address.serialize()
        }

        historyMessage.sendTime = message.dateSent

        historyMessage.messagePayload = if (message.isLocation()) {
            message.body
        } else {
            AmeGroupMessage(AmeGroupMessage.TEXT, AmeGroupMessage.TextContent(message.body)).toString()
        }
        return historyMessage
    }


    private fun computeFilePsw(groupKey: String, digest: String): String {
        val groupKeyData: ByteArray = GroupMessageEncryptUtils.decodeGroupPassword(groupKey)
        val digestData: ByteArray = GroupMessageEncryptUtils.decodeGroupPassword(digest)
        return Base64.encodeBytes(EncryptUtils.encryptSHA512(GroupMessageEncryptUtils.byteMerger(groupKeyData, digestData)))
    }


    private fun getThumbSizeForImage(attachment: AttachmentRecord, path: String): Size {
        if (attachment.isImage() || attachment.isGif()) {
            return BitmapUtils.getImageSize(path)
        }
        return Size(-1, -1)
    }

    private fun createTempFile(context: Context): File {
        val file = File.createTempFile("upload-attachment", EncryptUtils.getSecretHex(5), context.cacheDir)
        file.deleteOnExit()
        return file
    }

    private fun outputDataToFile(input: InputStream, outFile: File) {
        val output = FileOutputStream(outFile)
        val buffer = ByteArray(4096)
        var totalRead = 0
        var read: Int = input.read(buffer)
        while (read != -1) {
            output.write(buffer, 0, read)
            totalRead += read
            read = input.read(buffer)
        }
        output.close()
    }

}
