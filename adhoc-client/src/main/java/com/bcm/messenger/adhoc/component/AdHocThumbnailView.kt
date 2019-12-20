package com.bcm.messenger.adhoc.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.bcm.messenger.adhoc.R
import com.bcm.messenger.adhoc.logic.AdHocMessageDetail
import com.bcm.messenger.adhoc.logic.AdHocMessageLogic
import com.bcm.messenger.adhoc.logic.AdHocMessageModel
import com.bcm.messenger.chats.util.ChatComponentListener
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.mms.DecryptableStreamUriLoader.DecryptableUri
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.MediaUtil
import com.bcm.messenger.utility.logger.ALog
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.android.synthetic.main.adhoc_thumbnail_view.view.*


/**
 * adhoc thumbnail view
 */
class AdHocThumbnailView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
        ConstraintLayout(context, attrs, defStyle) {

    companion object {
        private const val TAG = "AdHocThumbnailView"
    }

    private var mAdHocMessage: AdHocMessageDetail? = null
    private var mGlideRequests: GlideRequests? = null
    private var mMaxWidth = 0
    private var mMaxHeight = 0
    private var mPlaceHolderResource = R.drawable.common_image_place_img
    private var mPlaceDrawable: Drawable? = context.getDrawable(R.drawable.common_image_place_img)
    private var mErrorImageResource = R.drawable.common_image_broken_img
    private var mErrorDrawable: Drawable? = context.getDrawable(R.drawable.common_image_broken_img)
    private var mImageRadius = 0

    init {
        View.inflate(context, R.layout.adhoc_thumbnail_view, this)
        mImageRadius = resources.getDimensionPixelSize(R.dimen.chats_conversation_item_radius)
        mMaxWidth = resources.getDimensionPixelSize(R.dimen.chats_conversation_thumbnail_item_width)
        mMaxHeight = resources.getDimensionPixelSize(R.dimen.chats_conversation_thumbnail_item_height)

        val lp = thumbnail_card.layoutParams
        lp.width = mMaxWidth
        lp.height = mMaxHeight
        thumbnail_card.layoutParams = lp
        thumbnail_card.radius = mImageRadius.toFloat()

        AdHocMessageLogic.getModel()?.addOnMessageListener(object : AdHocMessageModel.DefaultOnMessageListener() {
            override fun onProgress(message: AdHocMessageDetail, progress: Float) {
                if (mAdHocMessage == message) {
                    mAdHocMessage?.attachmentProgress = progress
                    updateProgress(progress)
                }
            }
        })
    }

    private fun updateProgress(progress: Float) {
        if (progress > 0 && progress < 1.0f) {
            thumbnail_progress.visibility = View.VISIBLE
            thumbnail_progress.progress = (progress * 100).toInt()
        }else {
            thumbnail_progress.visibility = View.GONE
        }
    }

    fun setThumbnailClickListener(listener: ChatComponentListener?) {
        setOnClickListener {v ->
            if (!v.isClickable) {
                return@setOnClickListener
            }
            mAdHocMessage?.let {
                listener?.onClick(v, it)
            }
        }
    }

    /**
     * set width and height limit
     */
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

    fun setImage(glideRequests: GlideRequests, messageRecord: AdHocMessageDetail) {

        ALog.d(TAG, "setImage messageRecord sessionId: ${messageRecord.sessionId}, indexId: ${messageRecord.indexId}")
        mAdHocMessage = messageRecord
        mGlideRequests = glideRequests
        updateState(messageRecord)
        setEncryptedThumbnail(glideRequests, messageRecord)
    }

    private fun updateState(messageRecord: AdHocMessageDetail) {

        thumbnail_download?.visibility = View.GONE
        val isVideo = messageRecord.getMessageBody()?.isVideo() ?: false
        if(isVideo) {
            thumbnail_play?.visibility = View.VISIBLE
            thumbnail_progress?.visibility = View.GONE
            val m = messageRecord.getMessageBody()?.content as? AmeGroupMessage.VideoContent
            if(m?.duration ?: 0L > 0L) {
                thumbnail_size.visibility = View.VISIBLE
                thumbnail_size.text = BcmFileUtils.stringForTime(m?.duration ?: 0L)
            }else {
                thumbnail_size.visibility = View.GONE
            }

        }else {
            thumbnail_play?.visibility = View.GONE
            thumbnail_progress?.visibility = View.GONE
            thumbnail_size.visibility = View.GONE
        }
    }

    private fun setEncryptedThumbnail(glideRequests: GlideRequests, messageDetailRecord: AdHocMessageDetail) {

        val content = messageDetailRecord.getMessageBody()?.content as? AmeGroupMessage.ThumbnailContent ?: return

        val resultUri = messageDetailRecord.toThumbnailUri()
        buildPlaceHolderThumbnail(resultUri == null)
        if (messageDetailRecord.sendByMe || resultUri != null) {
            buildThumbnailRequest(glideRequests, resultUri, content.mimeType)
        }else {
            ALog.i(TAG, "resultUri is null or not sendByMe, not buildThumbnail")
        }
        isClickable = if (resultUri != null) {
            updateProgress(1.0F)
            true
        }else {
            updateProgress(messageDetailRecord.attachmentProgress)
            false
        }
        ALog.i(TAG, "setEncryptedThumbnail mid: ${messageDetailRecord.mid}, isClickable: $isClickable")
    }

    private fun changeShowSize(image: Any?) {
        var w = mMaxWidth
        var h = mMaxHeight
        var aw = mMaxWidth//current w
        var ah = mMaxHeight//current h
        if(image is Bitmap) {
            aw = image.width
            ah = image.height

        }else if(image is Drawable) {
            aw = image.intrinsicWidth
            ah = image.intrinsicHeight

        }else {
            val message = mAdHocMessage
            if(message != null) {
                if (message.getMessageBody()?.isImage() == true) {
                    val content = message.getMessageBody()?.content as AmeGroupMessage.ImageContent
                    if (content.width > 0 && content.height > 0) {
                        aw = content.width
                        ah = content.height
                    }
                } else if (message.getMessageBody()?.isVideo() == true) {
                    val content = message.getMessageBody()?.content as AmeGroupMessage.VideoContent
                    if (content.thumbnail_width > 0 && content.thumbnail_height > 0) {
                        aw = content.thumbnail_width
                        ah = content.thumbnail_height
                    }
                }
            }
        }

        if (aw == 0 || ah == 0) {//avoid zero
            aw = mMaxWidth
            ah = mMaxHeight
        }

        if (aw == ah) {

        }else {
            val defaultRate = 3.0f / 7.0f
            if(aw > ah) {
                var rate = ah.toFloat() / aw.toFloat()
                if(rate < defaultRate) {
                    rate = defaultRate
                }
                h = (w * rate).toInt()
            }else {
                var rate = aw.toFloat() / ah.toFloat()
                if(rate < defaultRate) {
                    rate = defaultRate
                }
                w = (h * rate).toInt()
            }
        }

        val lp = thumbnail_card.layoutParams
        if (lp.width != w || lp.height != h) {
            lp.width = w
            lp.height = h
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
        if (loadObj == null) return

        if(loadObj is DecryptableUri) {
            ALog.d(TAG, "buildThumbnailRequest loadObj: ${loadObj.uri}")
        }else {
            ALog.d(TAG, "buildThumbnailRequest loadObj: $loadObj")
        }

        when {
            MediaUtil.isGif(contentType) -> {
                glideRequests.asGif().load(loadObj)
                        .listener(object : RequestListener<GifDrawable> {
                            override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<GifDrawable>, isFirstResource: Boolean): Boolean {
                                ALog.d(TAG, "onLoadFailed")
                                buildErrorHolderThumbnail()
                                return false
                            }

                            override fun onResourceReady(resource: GifDrawable, model: Any?, target: Target<GifDrawable>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
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
                            override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                                ALog.d(TAG, "onLoadFailed")
                                buildErrorHolderThumbnail()
                                return false
                            }

                            override fun onResourceReady(resource: Drawable, model: Any?, target: Target<Drawable>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                                ALog.i(TAG, "onResourceReady")
                                changeShowSize(resource)
                                return false
                            }
                        })
                        .override(mMaxWidth, mMaxHeight) //have to set target width and height, or image will be fuzzy
                        .into(thumbnail_image)
            }
        }

    }

}
