package com.bcm.messenger.chats.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.core.util.Pair
import com.bcm.messenger.chats.BuildConfig
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.components.ChatThumbnailView
import com.bcm.messenger.chats.mediapreview.MediaViewActivity
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.ShareElements
import com.bcm.messenger.common.core.AmeFileUploader
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.grouprepository.model.AmeHistoryMessageDetail
import com.bcm.messenger.common.mms.PartAuthority
import com.bcm.messenger.common.providers.PartProvider
import com.bcm.messenger.common.ui.activity.ApkInstallRequestActivity
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.Util
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Created by wjh on 2018/11/20
 */
open class ChatPreviewClickListener : ChatComponentListener {

    companion object {

        private val TAG = "ChatPreviewClickListener"

        private fun doForOtherFile(context: Context, uri: Uri, contentType: String?, name: String?) {

            fun handleWithPath(uri: Uri, path: String?) {
                AmeAppLifecycle.hideLoading()
                ALog.d(TAG, "doForOtherFile path: $path, uri: $uri, contentType: $contentType")

                var isApk = false
                if (path != null) {
                    isApk = AppUtil.isApkFile(context, path)
                }
                if (isApk) {
                    if (AppUtil.checkInstallPermission(context)) {
                        BcmFileUtils.installApk(context, path ?: "")
                    } else {
                        AppContextHolder.APP_CONTEXT.startActivity(Intent(context, ApkInstallRequestActivity::class.java).apply {
                            putExtra(ARouterConstants.PARAM.PARAM_APK, path)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                } else {
                    val finalContentType = if (contentType.isNullOrEmpty()) {
                        "*/*"
                    } else {
                        contentType
                    }
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.addCategory(Intent.CATEGORY_DEFAULT)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.setDataAndType(uri, finalContentType)
                    gotoActivity(context, intent, null)
                }
            }

            AmeAppLifecycle.showLoading()
            Observable.create<Pair<Uri, String>> {
                if (contentType == null || !contentType.startsWith("text/")) {
                    val masterSecret = BCMEncryptUtils.getMasterSecret(context)
                    val outFile = File(AmeFileUploader.DOCUMENT_DIRECTORY, name
                            ?: System.currentTimeMillis().toString())
                    if (!BcmFileUtils.isExist(outFile.absolutePath)) {
                        ALog.d(TAG, "doForOtherFile uri: $uri, outFile: $outFile is not exist, create")
                        val input = PartAuthority.getAttachmentStream(context, masterSecret, uri)
                        val output: OutputStream = FileOutputStream(outFile)
                        Util.copy(input, output)
                        outFile.deleteOnExit()
                    }
                    val targetUri = FileProvider.getUriForFile(context, BuildConfig.BCM_APPLICATION_ID + ".fileprovider", outFile)
                    it.onNext(Pair(targetUri, outFile.absolutePath))

                } else {
                    throw Exception("no need transfer to local path")
                }
                it.onComplete()

            }.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ result ->
                        handleWithPath(result.first ?: return@subscribe, result.second)
                    }, {
                        ALog.e(TAG, "doForOtherFile error", it)
                        handleWithPath(uri, null)
                    })

        }

        private fun doForPrivate(v: View, messageRecord: MessageRecord) {

            fun hasThumbnail(messageRecord: MessageRecord): Boolean {
                return messageRecord.isMediaMessage() && messageRecord.getMediaAttachment() != null
            }

            fun hasDocument(messageRecord: MessageRecord): Boolean {
                return messageRecord.isMediaMessage() && messageRecord.getDocumentAttachment() != null
            }

            val slide = when {
                hasThumbnail(messageRecord) -> {
                    messageRecord.getMediaAttachment()
                }
                hasDocument(messageRecord) -> {
                    messageRecord.getDocumentAttachment()
                }
                else -> null
            } ?: return

            if (MediaViewActivity.isContentTypeSupported(slide.contentType)) {
                val intent = Intent(v.context, MediaViewActivity::class.java)
                intent.putExtra(MediaViewActivity.DATA_TYPE, MediaViewActivity.TYPE_PRIVATE)
                intent.putExtra(MediaViewActivity.THREAD_ID, messageRecord.threadId)
                intent.putExtra(MediaViewActivity.INDEX_ID, messageRecord.id)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.setDataAndType(slide.getPartUri(), slide.contentType)
                val bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(v.context as Activity, v, ShareElements.Activity.MEDIA_PREIVEW + messageRecord.id).toBundle()
                gotoActivity(v.context, intent, bundle)

            } else if (slide.dataUri != null) {
                val publicUri = PartAuthority.getAttachmentPublicUri(slide.getPartUri())
                doForOtherFile(v.context, publicUri, slide.contentType, slide.fileName)
            }
        }

        private fun doForGroup(v: View, messageRecord: AmeGroupMessageDetail) {

            val attachmentContent = messageRecord.message.content as AmeGroupMessage.AttachmentContent
            var contentType: String? = attachmentContent.mimeType
            val messageType = messageRecord.message.type

            // retry download thumbnail
            if (messageRecord.isThumbnailDownloadFail) {
                (v as? ChatThumbnailView)?.downloadGroupThumbnail(messageRecord)
                return
            } else if (messageRecord.isFileDeleted && messageRecord.thumbnailPartUri == null) {
                if (messageType == AmeGroupMessage.VIDEO) {
                    AmeAppLifecycle.failure(getString(R.string.chats_media_view_video_expire), true)
                } else {
                    AmeAppLifecycle.failure(getString(R.string.chats_media_view_image_expire), true)
                }
                return
            }

            if (messageType == AmeGroupMessage.IMAGE || messageType == AmeGroupMessage.VIDEO) {
                val intent = Intent(v.context, MediaViewActivity::class.java)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.putExtra(MediaViewActivity.THREAD_ID, messageRecord.gid)
                intent.putExtra(MediaViewActivity.INDEX_ID, messageRecord.indexId)
                if (messageRecord is AmeHistoryMessageDetail) {
                    intent.putExtra(MediaViewActivity.DATA_TYPE, MediaViewActivity.TYPE_HISTORY)
                    intent.putExtra(MediaViewActivity.HISTORY_INDEX, messageRecord.mediaIndex)
                } else {
                    intent.putExtra(MediaViewActivity.DATA_TYPE, MediaViewActivity.TYPE_GROUP)
                }
                val bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(v.context as Activity, Pair(v, "${ShareElements.Activity.MEDIA_PREIVEW}${messageRecord.indexId}")).toBundle()

                gotoActivity(v.context, intent, bundle)
                return
            }

            var isComplete = messageRecord.isAttachmentComplete
            if (!isComplete && messageRecord is AmeHistoryMessageDetail) {
                val content = messageRecord.message.content
                if (content is AmeGroupMessage.AttachmentContent && content.isExist()) {
                    isComplete = true
                }
            }

            if (isComplete) {
                if (MediaViewActivity.isContentTypeSupported(contentType)) {
                    val intent = Intent(v.context, MediaViewActivity::class.java)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.putExtra(MediaViewActivity.THREAD_ID, messageRecord.gid)
                    intent.putExtra(MediaViewActivity.INDEX_ID, messageRecord.indexId)
                    intent.putExtra(MediaViewActivity.DATA_TYPE, MediaViewActivity.TYPE_SINGLE)
                    intent.putExtra(MediaViewActivity.MEDIA_URI, messageRecord.attachmentUri)
                    val bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(v.context as Activity, Pair(v, "${ShareElements.Activity.MEDIA_PREIVEW}${messageRecord.indexId}")).toBundle()
                    gotoActivity(v.context, intent, bundle)
                    return
                }

                val publicUri = if (messageRecord.isFileEncrypted) {
                    PartAuthority.getGroupPublicUri(PartAuthority.getGroupAttachmentUri(messageRecord.gid, messageRecord.indexId))
                } else {
                    val uri = messageRecord.filePartUri
                    if (uri != null && uri.path != null) {
                        PartProvider.getUnencryptedUri(uri.path)
                    } else {
                        return
                    }
                }

                if (contentType.isNullOrEmpty()) {
                    contentType = MediaUtil.getMimeType(v.context, messageRecord.toAttachmentUri())
                    if (contentType.isNullOrEmpty()) {
                        val name = if (attachmentContent is AmeGroupMessage.FileContent) {
                            attachmentContent.fileName ?: attachmentContent.url
                        } else {
                            attachmentContent.url
                        }
                        contentType = BcmFileUtils.getMimeTypeByNme(name)
                    }
                }

                doForOtherFile(v.context, publicUri, contentType, null)
            }

        }

        private fun gotoActivity(context: Context, intent: Intent, bundle: Bundle?) {
            try {
                if (context is Activity) {
                    context.startActivity(intent, bundle)
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent, bundle)
                }
            } catch (ex: Exception) {
                ALog.e(TAG, "gotoActivity error", ex)
                ToastUtil.show(context, context.getString(R.string.chats_there_is_no_app_available_to_handle_this_link_on_your_device))
            }
        }
    }

    override fun onClick(v: View, data: Any) {
        try {
            if (data is MessageRecord) {
                doForPrivate(v, data)
            } else if (data is AmeGroupMessageDetail) {
                doForGroup(v, data)
            }
        } catch (ex: Exception) {
            ALog.e(TAG, "onClick error", ex)
        }
    }


}