package com.bcm.messenger.chats.mediabrowser

import android.net.Uri
import android.view.View
import android.widget.ImageView
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.group.logic.MessageFileHandler
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.mms.DecryptableStreamUriLoader
import com.bcm.messenger.common.mms.GlideApp
import com.bcm.messenger.common.provider.bean.ConversationStorage
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.GroupUtil
import com.bcm.messenger.common.utils.MediaUtil
import com.bcm.messenger.utility.AmeURLUtil
import com.bcm.messenger.utility.logger.ALog
import com.bumptech.glide.load.engine.DiskCacheStrategy
import java.lang.ref.WeakReference

/**
 * media browse data
 * Created by wjh on 2018/10/15
 */
data class MediaBrowseData(val name: String,
                      val mediaType: String,
                      val time: Long,//create time
                      val msgSource: Any,//body
                      val fromGroup: Boolean,
                      var selected: Boolean = false
                    ) {

    private var viewRef: WeakReference<ImageView>? = null
    private var backgroundRef: WeakReference<View?>? = null
    private var mOverrideWidth: Int = -1
    private var mOverrideHeight: Int = -1
    private var mPlaceHolderResource: Int = 0

    private var mCallback: (() -> Unit)? = null

    internal fun notifyChanged() {
        mCallback?.invoke()
    }

    fun observe(callback: (() -> Unit)? = null) {
        mCallback = callback
    }

    fun clearThumbnail(view: ImageView) {
        viewRef = null
        view.setImageResource(0)
    }

    fun setThumbnail(accountContext: AccountContext?, background: View?, view: ImageView, overrideWidth: Int, overrideHeight: Int, placeHolderResource: Int) {
        if (viewRef?.get() == view && backgroundRef?.get() == background) {
            return
        }

        accountContext?:return

        ALog.d("MediaBrowseData", "setThumbnail")
        viewRef = WeakReference(view)
        backgroundRef = WeakReference(background)
        mOverrideWidth = overrideWidth
        mOverrideHeight = overrideHeight
        mPlaceHolderResource = placeHolderResource

        if (MediaUtil.isImageType(mediaType) || MediaUtil.isVideoType(mediaType)) {
            background?.visibility = View.GONE
            if (fromGroup) {
                val msg = msgSource as? AmeGroupMessageDetail
                view.setImageResource(placeHolderResource)
                if (msg != null) {
                    if (msg.getThumbnailPartUri(accountContext) != null) {
                        showThumbnail(accountContext, view, msg.getThumbnailPartUri(accountContext)!!)
                    } else {
                        MessageFileHandler.downloadThumbnail(accountContext, msg, object : MessageFileHandler.MessageFileCallback {
                            override fun onResult(success: Boolean, uri: Uri?) {
                                val current = viewRef?.get()
                                if (current == view) {
                                    if (success && uri != null) {
                                        showThumbnail(accountContext, current, uri)
                                    }
                                }
                            }
                        })
                    }
                }
            } else {
                val record = msgSource as? MessageRecord
                val image = record?.getMediaAttachment()
                val uri = image?.getThumbnailPartUri()
                if (uri == null) {
                    view.setImageResource(placeHolderResource)
                } else {
                    showThumbnail(accountContext, view, uri)
                }
            }

        } else if (MediaUtil.isTextType(mediaType)) {
            background?.visibility = View.VISIBLE
            background?.setBackgroundResource(R.drawable.chats_browser_link_background)
            showIcon(view, AmeURLUtil.getUrlLogo(name), R.drawable.chats_message_link_default_icon)

        } else {
            background?.visibility = View.VISIBLE
            background?.setBackgroundResource(mPlaceHolderResource)
            view.setImageDrawable(AmeGroupMessage.FileContent.getTypeDrawable(name, mediaType, overrideWidth, AppUtil.sp2Px(view.resources, 12)))
        }
    }

    fun refresh(accountContext: AccountContext) {
        ALog.d("MediaBrowseData", "refresh")
        val view = viewRef?.get() ?: return
        setThumbnail(accountContext, backgroundRef?.get(), view, mOverrideWidth, mOverrideHeight, mPlaceHolderResource)
    }


    fun getVideoDuration(): Long {
        return if (fromGroup) {
            val msg = msgSource as AmeGroupMessageDetail
            val content = msg.message.content as AmeGroupMessage.VideoContent
            content.duration
        } else {
            val msg = msgSource as? MessageRecord ?: return 0L
            val duration = msg.getVideoAttachment()?.duration
            duration ?: 0L
        }
    }

    private fun showIcon(view: ImageView?, iconUrl: String, placeHolder: Int) {
        if (null == view || AppUtil.activityFinished(view)) {
            return
        }

        GlideApp.with(view).load(iconUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(mOverrideWidth, mOverrideHeight)
                .placeholder(placeHolder)
                .error(placeHolder)
                .into(view)
    }

    private fun showThumbnail(accountContext: AccountContext?, view: ImageView?, uri: Uri) {
        if (null == view || AppUtil.activityFinished(view) || accountContext == null) {
            return
        }

        GlideApp.with(view)
                .load(DecryptableStreamUriLoader.DecryptableUri(BCMEncryptUtils.getMasterSecret(accountContext)
                        ?: throw Exception("getMasterSecret fail"), uri))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(mOverrideWidth, mOverrideHeight)
                .placeholder(mPlaceHolderResource)
                .error(mPlaceHolderResource)
                .into(view)
    }

    private fun getMessageFilePath(): String? {
        if (fromGroup) {
            val msg = msgSource as? AmeGroupMessageDetail
            return msg?.attachmentUri
        } else {
            val record = msgSource as? MessageRecord
            val uri = if (record?.hasDocuments() == true) {
                record.getDocumentAttachment()?.dataUri
            } else {
                record?.getMediaAttachment()?.dataUri
            }
            return uri?.path
        }
    }

    fun isDownloaded(): Boolean {
        return getMessageFilePath()?.isNotEmpty() == true
    }

    fun fileSize(): Long {
        if (fromGroup) {
            val msg = msgSource as? AmeGroupMessageDetail
            if (null != msg) {
                return when (msg.message.content) {
                    is AmeGroupMessage.VideoContent -> (msg.message.content as AmeGroupMessage.VideoContent).size
                    is AmeGroupMessage.ImageContent -> (msg.message.content as AmeGroupMessage.ImageContent).size
                    is AmeGroupMessage.FileContent -> (msg.message.content as AmeGroupMessage.FileContent).size
                    else -> 0
                }
            }
        } else {
            val record = msgSource as? MessageRecord
            return record?.getMediaAttachment()?.dataSize ?: 0L
        }
        return 0L
    }

    fun getStorageType(): Int {
        if (fromGroup) {
            val msg = msgSource as? AmeGroupMessageDetail
            if (null != msg) {
                return when (msg.message.content) {
                    is AmeGroupMessage.VideoContent -> ConversationStorage.TYPE_VIDEO
                    is AmeGroupMessage.ImageContent -> ConversationStorage.TYPE_IMAGE
                    is AmeGroupMessage.FileContent -> ConversationStorage.TYPE_FILE
                    else -> ConversationStorage.TYPE_UN_SUPPORT
                }
            }
        } else {
            val record = msgSource as? MessageRecord
            if (record?.hasAttachments() == true) {
                val attachment = record.getMediaAttachment() ?: return ConversationStorage.TYPE_UN_SUPPORT
                if (attachment.isImage()) {
                    return ConversationStorage.TYPE_IMAGE
                } else if (attachment.isVideo()) {
                    return ConversationStorage.TYPE_VIDEO
                } else if (attachment.isDocument()) {
                    return ConversationStorage.TYPE_FILE
                }
            }
        }
        return ConversationStorage.TYPE_UN_SUPPORT
    }

    fun getUserAddress(accountContext: AccountContext): Address? {
        if (fromGroup) {
            val msg = msgSource as? AmeGroupMessageDetail ?: return null
            return GroupUtil.addressFromGid(accountContext, msg.gid)
        } else {
            val record = msgSource as? MessageRecord ?: return null
            return record.getRecipient(accountContext).address
        }
    }
}
