package com.bcm.messenger.chats.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.group.logic.MessageFileHandler
import com.bcm.messenger.chats.util.ChatComponentListener
import com.bcm.messenger.chats.util.GroupAttachmentProgressEvent
import com.bcm.messenger.chats.util.HistoryGroupAttachmentProgressEvent
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.database.model.AttachmentDbModel
import com.bcm.messenger.common.database.records.AttachmentRecord
import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.event.PartProgressEvent
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.grouprepository.model.AmeHistoryMessageDetail
import com.bcm.messenger.common.mms.DecryptableStreamUriLoader.DecryptableUri
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.MediaUtil
import com.bcm.messenger.utility.ViewUtils
import com.bcm.messenger.utility.logger.ALog
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.android.synthetic.main.chats_thumbnail_view_new.view.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 *
 */
class ChatThumbnailView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
        ConstraintLayout(context, attrs, defStyle) {

    companion object {

        private const val TAG = "ChatThumbnailView"
        private const val TRANSITION_MS = 300
        private const val MIN_PROGRESS = 5

        private const val STATE_IMG_COMPLETE = 0
        private const val STATE_IMG_PROGRESS = 1
        private const val STATE_DOWNLOAD = 2
        private const val STATE_VIDEO_COMPLETE = 3
        private const val STATE_VIDEO_PROGRESS = 4
    }

    private var mPrivateMessage: MessageRecord? = null
    private var mGroupMessage: AmeGroupMessageDetail? = null
    private var mAttachmentRecord: AttachmentRecord? = null

    private var parentClickListener: OnClickListener? = null
    private var thumbnailClickListener: ChatComponentListener? = null
    private var downloadClickListener: ChatComponentListener? = null

    private var mMasterSecret: MasterSecret? = null
    private var mGlideRequests: GlideRequests? = null
    private var mShowPending = false

    private var mMaxWidth = 0
    private var mMaxHeight = 0

    private var mPlaceHolderResource = R.drawable.common_image_place_img
    private var mPlaceDrawable: Drawable? = context.getDrawable(R.drawable.common_image_place_img)
    private var mErrorImageResource = R.drawable.common_image_broken_img
    private var mErrorDrawable: Drawable? = context.getDrawable(R.drawable.common_image_broken_img)

    private var mImageRadius = 0

    init {
        View.inflate(context, R.layout.chats_thumbnail_view_new, this)
        mImageRadius = resources.getDimensionPixelSize(R.dimen.chats_conversation_item_radius)
        mMaxWidth = resources.getDimensionPixelSize(R.dimen.chats_conversation_thumbnail_item_width)
        mMaxHeight = resources.getDimensionPixelSize(R.dimen.chats_conversation_thumbnail_item_height)

        val lp = thumbnail_card.layoutParams
        lp.width = mMaxWidth
        lp.height = mMaxHeight
        thumbnail_card.layoutParams = lp
        thumbnail_card.radius = mImageRadius.toFloat()

        thumbnail_download.setOnClickListener(DownloadClickDispatcher())
        super.setOnClickListener(ThumbnailClickDispatcher())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        EventBus.getDefault().unregister(this)
    }

    override fun setOnClickListener(l: OnClickListener?) {
        parentClickListener = l
    }


    fun setThumbnailLimit(maxWidth: Int, maxHeight: Int) {
        mMaxWidth = maxWidth
        mMaxHeight = maxHeight
    }

    fun setThumbnailAppearance(defaultBackgroundRes: Int, radius: Int) {
        if (mPlaceHolderResource != defaultBackgroundRes) {
            mPlaceHolderResource = defaultBackgroundRes
            mPlaceDrawable = context.getDrawable(defaultBackgroundRes)
        }
        if (mImageRadius != radius) {
            mImageRadius = radius
            thumbnail_card?.radius = radius.toFloat()
        }
    }

    fun setThumbnailAppearance(defaultBackgroundRes: Int, errorBackgroundRes: Int, radius: Int) {
        if (mPlaceHolderResource != defaultBackgroundRes) {
            mPlaceHolderResource = defaultBackgroundRes
            mPlaceDrawable = context.getDrawable(defaultBackgroundRes)
        }
        if (mErrorImageResource != errorBackgroundRes) {
            mErrorImageResource = errorBackgroundRes
            mErrorDrawable = context.getDrawable(errorBackgroundRes)
        }
        if (mImageRadius != radius) {
            mImageRadius = radius
            thumbnail_card?.radius = radius.toFloat()
        }
    }

    fun setThumbnailClickListener(listener: ChatComponentListener?) {
        this.thumbnailClickListener = listener
    }

    fun setDownloadClickListener(listener: ChatComponentListener?) {
        this.downloadClickListener = listener
    }

    fun setImage(masterSecret: MasterSecret, glideRequests: GlideRequests, messageRecord: MessageRecord) {
        val showControls = !messageRecord.isFailed() && (!messageRecord.isOutgoing() || messageRecord.isPending())
        val attachmentRecord = messageRecord.getImageAttachment() ?: messageRecord.getVideoAttachment() ?: return
        val isVideo = MediaUtil.isVideo(attachmentRecord.contentType)
        if (showControls && attachmentRecord.transferState == AttachmentDbModel.TransferState.STARTED.state) {
            if (isVideo) {
                displayControl(STATE_VIDEO_PROGRESS)
            } else {
                displayControl(STATE_IMG_PROGRESS)
            }
            thumbnail_size.visibility = View.GONE
        } else if (showControls && attachmentRecord.isPendingDownload()) {
            displayControl(STATE_DOWNLOAD)

            thumbnail_size.visibility = View.GONE
        } else {
            if (isVideo) {
                displayControl(STATE_VIDEO_COMPLETE)
                val duration = attachmentRecord.duration
                if (duration > 0L) {
                    thumbnail_size.visibility = View.VISIBLE
                    thumbnail_size.text = BcmFileUtils.stringForTime(duration)
                } else {
                    thumbnail_size.visibility = View.GONE
                }
            } else {
                displayControl(STATE_IMG_COMPLETE)
                thumbnail_size.visibility = View.GONE
            }
        }

        ALog.d(TAG, "contentType: ${attachmentRecord.contentType}, thumbnail: ${attachmentRecord.thumbnailUri}, uri: ${attachmentRecord.dataUri}, id: ${messageRecord.id}, fastID: ${attachmentRecord.fastPreflightId}")

        val uri = if (isVideo) {
            attachmentRecord.getThumbnailPartUri()
        } else {
            attachmentRecord.getThumbnailPartUri() ?: attachmentRecord.getPartUri()
        }

        val useReplace: Boolean = if (mPrivateMessage?.id == messageRecord.id) {
            if (mAttachmentRecord?.thumbnailUri == attachmentRecord.thumbnailUri && mAttachmentRecord?.dataUri == attachmentRecord.dataUri) {
                false
            } else mAttachmentRecord?.dataUri != attachmentRecord.dataUri || isVideo
        } else {
            true
        }

        mPrivateMessage = messageRecord
        mGroupMessage = null
        mAttachmentRecord = attachmentRecord
        mGlideRequests = glideRequests
        mMasterSecret = masterSecret
        buildPlaceHolderThumbnail(uri == null)
        if (useReplace) {
            when {
                uri != null -> buildThumbnailRequest(glideRequests,
                        DecryptableUri(masterSecret, uri),
                        attachmentRecord.contentType)
                else -> {
                    buildPlaceHolderThumbnail()
                }
            }
        }
    }


    fun setImage(masterSecret: MasterSecret, glideRequests: GlideRequests, messageRecord: AmeGroupMessageDetail) {

        ALog.d(TAG, "setImage messageRecord gid: ${messageRecord.gid}, indexId: ${messageRecord.indexId}")
        updateState(messageRecord)
        mGroupMessage = messageRecord
        mGlideRequests = glideRequests
        mMasterSecret = masterSecret
        mPrivateMessage = null
        mAttachmentRecord = null

        setEncryptedThumbnail(masterSecret, glideRequests, messageRecord)
    }


    private fun updateState(messageRecord: AmeGroupMessageDetail) {

        val isVideo = messageRecord.message.isVideo()
        if (messageRecord.isSending || messageRecord.isThumbnailDownloading) {
            if (isVideo) {
                displayControl(STATE_VIDEO_PROGRESS)
            } else {
                displayControl(STATE_IMG_PROGRESS)
            }

        } else {
            if (isVideo) {
                displayControl(STATE_VIDEO_COMPLETE)

                val m = messageRecord.message.content as AmeGroupMessage.VideoContent
                if (m.duration > 0L) {
                    thumbnail_size.visibility = View.VISIBLE
                    thumbnail_size.text = BcmFileUtils.stringForTime(m.duration)
                } else {
                    thumbnail_size.visibility = View.GONE
                }

            } else {
                displayControl(STATE_IMG_COMPLETE)
                thumbnail_size.visibility = View.GONE
            }

        }
    }


    private fun setEncryptedThumbnail(masterSecret: MasterSecret, glideRequests: GlideRequests, messageDetailRecord: AmeGroupMessageDetail) {

        val content = messageDetailRecord.message.content as AmeGroupMessage.ThumbnailContent
        val resultUri = if (messageDetailRecord.message.isVideo()) {
            messageDetailRecord.thumbnailPartUri
        } else {
            messageDetailRecord.thumbnailPartUri ?: messageDetailRecord.filePartUri
        }
        buildPlaceHolderThumbnail(resultUri == null)
        if (resultUri == null) {
            MessageFileHandler.downloadThumbnail(messageDetailRecord, object : MessageFileHandler.MessageFileCallback {
                override fun onResult(success: Boolean, uri: Uri?) {
                    if (mGroupMessage == messageDetailRecord) {
                        updateState(messageDetailRecord)
                        if (success && uri != null) {
                            buildThumbnailRequest(glideRequests, DecryptableUri(masterSecret, uri), content.mimeType)
                        } else {
                            buildErrorHolderThumbnail()
                        }
                    }
                }

            })
        } else {
            buildThumbnailRequest(glideRequests, DecryptableUri(masterSecret, resultUri), content.mimeType)
        }

    }

    private fun changeShowSize(image: Any?) {
        var w = mMaxWidth
        var h = mMaxHeight
        var aw = mMaxWidth
        var ah = mMaxHeight
        if (image is Bitmap) {
            aw = image.width
            ah = image.height

        } else if (image is Drawable) {
            aw = image.intrinsicWidth
            ah = image.intrinsicHeight

        } else {
            val groupMessage = mGroupMessage
            if (groupMessage != null) {
                if (groupMessage.message.isImage()) {
                    val content = groupMessage.message.content as AmeGroupMessage.ImageContent
                    if (content.width > 0 && content.height > 0) {
                        aw = content.width
                        ah = content.height
                    }
                } else if (groupMessage.message.isVideo()) {
                    val content = groupMessage.message.content as AmeGroupMessage.VideoContent
                    if (content.thumbnail_width > 0 && content.thumbnail_height > 0) {
                        aw = content.thumbnail_width
                        ah = content.thumbnail_height
                    }
                }
            }
        }

        if (aw == 0 || ah == 0) {
            aw = mMaxWidth
            ah = mMaxHeight
        }

        if (aw != ah) {
            val defaultRate = 1.0f / 7.0f
            if (aw > ah) {
                var rate = ah.toFloat() / aw.toFloat()
                if (rate < defaultRate) {
                    rate = defaultRate
                }
                h = (w * rate).toInt()
            } else {
                var rate = aw.toFloat() / ah.toFloat()
                if (rate < defaultRate) {
                    rate = defaultRate
                }
                w = (h * rate).toInt()
            }
        }

        val lp = thumbnail_card.layoutParams
        if (lp.width != w || lp.height != h) {
            lp.width = w
            lp.height = h
            ALog.i(TAG, "changeShowSize after w: $w, h: $h")
            thumbnail_card.layoutParams = lp
        }
    }

    private fun buildPlaceHolderThumbnail(force: Boolean = false) {
        ALog.d(TAG, "buildPlaceHolderThumbnail force: $force")
        if (thumbnail_holder.drawable != mPlaceDrawable) {
            thumbnail_holder.setImageDrawable(mPlaceDrawable)
        }
        if (force) {
            changeShowSize(null)
            thumbnail_image.setImageDrawable(null)
        }
    }

    private fun buildErrorHolderThumbnail(force: Boolean = false) {
        ALog.d(TAG, "buildPlaceHolderThumbnail force: $force")
        if (thumbnail_holder.drawable != mErrorDrawable) {
            thumbnail_holder.setImageDrawable(mErrorDrawable)
        }
        if (force) {
            changeShowSize(null)
            thumbnail_image.setImageDrawable(null)
        }
    }

    private fun buildThumbnailRequest(glideRequests: GlideRequests, loadObj: Any?, contentType: String) {

        if (loadObj is DecryptableUri) {
            ALog.d(TAG, "buildThumbnailRequest loadObj: ${loadObj.uri}")
        } else {
            ALog.d(TAG, "buildThumbnailRequest loadObj: $loadObj")
        }

        when {
            MediaUtil.isGif(contentType) -> {
                glideRequests.asGif().load(loadObj)
                        .listener(object : RequestListener<GifDrawable> {
                            override fun onLoadFailed(e: GlideException?, model: Any, target: Target<GifDrawable>, isFirstResource: Boolean): Boolean {
                                ALog.d(TAG, "onLoadFailed")
                                buildErrorHolderThumbnail()
                                return false
                            }

                            override fun onResourceReady(resource: GifDrawable, model: Any, target: Target<GifDrawable>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                                changeShowSize(resource)
                                return false
                            }
                        })
                        .override(mMaxWidth, mMaxHeight)
                        .into(thumbnail_image)
            }
            else -> {
                glideRequests.load(loadObj)
                        .listener(object : RequestListener<Drawable> {
                            override fun onLoadFailed(e: GlideException?, model: Any, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                                ALog.d(TAG, "onLoadFailed")
                                buildErrorHolderThumbnail()
                                return false
                            }

                            override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                                ALog.i(TAG, "onResourceReady")
                                changeShowSize(resource)
                                return false
                            }
                        })
                        .override(mMaxWidth, mMaxHeight)
                        .into(thumbnail_image)
            }
        }

    }

    private fun displayControl(state: Int) {

        when (state) {
            STATE_IMG_COMPLETE -> {
                mShowPending = false
                ViewUtils.fadeOut(thumbnail_progress, TRANSITION_MS)
                ViewUtils.fadeOut(thumbnail_download, TRANSITION_MS)
                ViewUtils.fadeOut(thumbnail_play, TRANSITION_MS)
            }
            STATE_IMG_PROGRESS -> {
                mShowPending = true
                ViewUtils.fadeIn(thumbnail_progress, TRANSITION_MS)
                ViewUtils.fadeOut(thumbnail_download, TRANSITION_MS)
                ViewUtils.fadeOut(thumbnail_play, TRANSITION_MS)
            }
            STATE_VIDEO_COMPLETE -> {
                mShowPending = false
                ViewUtils.fadeIn(thumbnail_play, TRANSITION_MS)
                ViewUtils.fadeOut(thumbnail_progress, TRANSITION_MS)
                ViewUtils.fadeOut(thumbnail_download, TRANSITION_MS)
            }
            STATE_VIDEO_PROGRESS -> {
                mShowPending = true
                ViewUtils.fadeIn(thumbnail_progress, TRANSITION_MS)
                ViewUtils.fadeOut(thumbnail_download, TRANSITION_MS)
                ViewUtils.fadeIn(thumbnail_play, TRANSITION_MS)
            }
            STATE_DOWNLOAD -> {
                mShowPending = false
                ViewUtils.fadeIn(thumbnail_download, TRANSITION_MS)
                ViewUtils.fadeOut(thumbnail_progress, TRANSITION_MS)
                ViewUtils.fadeOut(thumbnail_play, TRANSITION_MS)
            }
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventAsync(event: PartProgressEvent) {
        if (event.attachment == this.mAttachmentRecord) {
            val isVideo = MediaUtil.isVideo(this.mAttachmentRecord?.contentType)

            val progress = event.progress.toFloat() / event.total
            if (progress >= 1.0f) {
                ALog.d(TAG, "download complete")
                if (isVideo) {
                    displayControl(STATE_VIDEO_COMPLETE)
                } else {
                    displayControl(STATE_IMG_COMPLETE)
                }
            } else {
                if (isVideo) {
                    displayControl(STATE_VIDEO_PROGRESS)
                } else {
                    displayControl(STATE_IMG_PROGRESS)
                }

                var p = (event.progress.toFloat() * 100 / event.total).toInt()
                if (p < MIN_PROGRESS) {
                    p = MIN_PROGRESS
                }
                thumbnail_progress.progress = p
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventAsync(event: GroupAttachmentProgressEvent) {

        fun updateProgress(groupMessage: AmeGroupMessageDetail, progress: Float) {
            if (progress >= 1.0f) {
                updateState(groupMessage)
                setImage(mMasterSecret ?: return, mGlideRequests ?: return, groupMessage)
            } else {
                updateState(groupMessage)
                var p = (progress * 100).toInt()
                if (p < MIN_PROGRESS) {
                    p = MIN_PROGRESS
                }
                thumbnail_progress.progress = p
            }
        }

        mGroupMessage?.let {
            if (it is AmeHistoryMessageDetail) {
                val tc = it.message.content as AmeGroupMessage.ThumbnailContent
                if (event is HistoryGroupAttachmentProgressEvent && event.action == GroupAttachmentProgressEvent.ACTION_THUMBNAIL_DOWNLOADING &&
                        (event.url == tc.url || event.url == tc.thumbnail_url)) {
                    updateProgress(it, event.progress)
                }
            } else {
                if (it.gid == event.gid && it.indexId == event.indexId && event.action == GroupAttachmentProgressEvent.ACTION_THUMBNAIL_DOWNLOADING) {
                    updateProgress(it, event.progress)
                }
            }
        }
    }

    private inner class ThumbnailClickDispatcher : OnClickListener {
        override fun onClick(view: View) {
            if (thumbnailClickListener != null) {
                val data = mPrivateMessage ?: mGroupMessage
                if (data != null) {
                    thumbnailClickListener?.onClick(view, data)
                }
            } else if (parentClickListener != null) {
                parentClickListener?.onClick(view)
            }
        }
    }

    private inner class DownloadClickDispatcher : OnClickListener {
        override fun onClick(view: View) {
            if (downloadClickListener != null) {
                val data = mPrivateMessage ?: mGroupMessage
                if (data != null && !mShowPending) {
                    downloadClickListener?.onClick(view, data)
                }
            }
        }
    }

}
