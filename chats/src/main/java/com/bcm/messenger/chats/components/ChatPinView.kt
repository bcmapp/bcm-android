package com.bcm.messenger.chats.components

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.group.logic.MessageFileHandler
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.imagepicker.widget.CropRoundCornerTransform
import com.bcm.messenger.common.mms.DecryptableStreamUriLoader
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.common.provider.AMELogin
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.MediaUtil
import com.bcm.messenger.utility.logger.ALog
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import kotlinx.android.synthetic.main.chats_pin_view.view.*
import java.util.*

/**
 */
class ChatPinView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
        androidx.constraintlayout.widget.ConstraintLayout(context, attrs, defStyle), RecipientModifiedListener {

    interface OnChatPinActionListener {
        fun onContentClick()
        fun onClose()
    }

    private val TAG = "ChatPinView"
    private var mMaxWidth = 0
    private var mMaxHeight = 0
    private var mImageRadius = 0

    private var mPlaceHolderResource = R.drawable.common_image_place_img
    private var mErrorImageResource = R.drawable.common_image_broken_img
    private var glideRequests: GlideRequests? = null
    private var messageDetailRecord: AmeGroupMessageDetail? = null

    private var mListener: OnChatPinActionListener? = null

    init {
        View.inflate(context, R.layout.chats_pin_view, this)
        mMaxWidth = AppUtil.dp2Px(resources, 40)
        mMaxHeight = AppUtil.dp2Px(resources, 40)
        mImageRadius = AppUtil.dp2Px(resources, 2)

        pin_close?.setOnClickListener {
            mListener?.onClose()
        }
    }

    fun setGroupMessage(messageRecord: AmeGroupMessageDetail, glideRequests: GlideRequests, listener: OnChatPinActionListener) {
        this.messageDetailRecord = messageRecord
        this.glideRequests = glideRequests
        this.mListener = listener
        clearShow()
        when (messageRecord.message.type) {
            AmeGroupMessage.TEXT -> setText()
            AmeGroupMessage.AUDIO -> setAudio()
            AmeGroupMessage.IMAGE -> setImage()
            AmeGroupMessage.VIDEO -> setVideo()
            AmeGroupMessage.LINK -> setLink()
            AmeGroupMessage.FILE -> setFile()
            AmeGroupMessage.NEWSHARE_CHANNEL -> setNewShareChannel()
            AmeGroupMessage.LOCATION -> setLocation()
            AmeGroupMessage.CONTACT -> setContact()
            AmeGroupMessage.CHAT_HISTORY -> setHistory()
            AmeGroupMessage.CHAT_REPLY -> setReply()
            AmeGroupMessage.GROUP_SHARE_CARD -> setGroupShareCard()
        }
        this.setOnClickListener {
            mListener?.onContentClick()
        }
    }

    private fun clearShow() {
        chat_pin_recipient_photo.visibility = View.GONE
        chats_pin_image.visibility = View.GONE
        chats_pin_image.background = null
        chats_pin_video_icon.visibility = View.GONE
    }

    private fun setText() {
        val content = messageDetailRecord?.message?.content as? AmeGroupMessage.TextContent
        chat_pin_content.text = content?.text
    }

    private fun setAudio() {
        chat_pin_recipient_photo.visibility = View.INVISIBLE
        chats_pin_image.visibility = View.VISIBLE
        chats_pin_image.setImageResource(R.drawable.chats_40_voice_msg)
        chat_pin_content.text = resources.getString(R.string.chats_pin_audio)
    }

    private fun setImage() {
        chat_pin_recipient_photo.visibility = View.INVISIBLE
        chats_pin_image.visibility = View.VISIBLE
        chat_pin_content.text = resources.getString(R.string.chats_pin_image)

        messageDetailRecord?.let {
            val content = it.message.content as? AmeGroupMessage.ThumbnailContent
            MessageFileHandler.downloadThumbnail(it, object : MessageFileHandler.MessageFileCallback {
                override fun onResult(success: Boolean, uri: Uri?) {
                    it.isThumbnailDownloading = false
                    if (success && glideRequests != null && content != null)
                        buildThumbnailRequest(glideRequests!!, uri, content.mimeType)
                }
            })
        }
    }

    private fun setVideo() {
        chat_pin_recipient_photo.visibility = View.INVISIBLE
        chats_pin_image.visibility = View.VISIBLE
        chats_pin_video_icon.visibility = View.VISIBLE
        chat_pin_content.text = resources.getString(R.string.chats_pin_video)
        messageDetailRecord?.let {
            val content = it.message.content as? AmeGroupMessage.VideoContent
            MessageFileHandler.downloadThumbnail(it, object : MessageFileHandler.MessageFileCallback {
                override fun onResult(success: Boolean, uri: Uri?) {
                    it.isThumbnailDownloading = false
                    if (success && glideRequests != null && content != null)
                        buildThumbnailRequest(glideRequests!!, uri, content.mimeType)
                }
            })
        }
    }

    private fun setFile() {
        chat_pin_recipient_photo.visibility = View.INVISIBLE
        chats_pin_image.visibility = View.VISIBLE
        chat_pin_content.text = resources.getString(R.string.chats_pin_file)
        messageDetailRecord?.let {
            val content = it.message.content as? AmeGroupMessage.FileContent
            chats_pin_image.setBackgroundResource(R.drawable.chats_message_file_icon_grey)
            chats_pin_image.setImageDrawable(AmeGroupMessage.FileContent.getTypeDrawable(content?.fileName
                    ?: content?.url
                    ?: "", content?.mimeType, AppUtil.dp2Px(resources, 20), AppUtil.sp2Px(resources, 12),
                    AppUtil.getColor(resources, R.color.common_content_warning_color)))
            chat_pin_content.text = content?.fileName ?: ""
        }
    }

    private fun setLink() {
        val content = messageDetailRecord?.message?.content as? AmeGroupMessage.LinkContent
        chat_pin_content.text = content?.url
    }

    private fun setLocation() {
        chat_pin_recipient_photo.visibility = View.INVISIBLE
        chats_pin_image.visibility = View.VISIBLE
        chats_pin_image.setImageResource(R.drawable.chats_40_pinned_location)
        chat_pin_content.text = resources.getString(R.string.chats_pin_location)
    }

    private fun setContact() {
        chat_pin_recipient_photo.visibility = View.VISIBLE
        chat_pin_content.text = resources.getString(R.string.chats_pin_contact)
        messageDetailRecord?.let {
            val content = it.message.content as? AmeGroupMessage.ContactContent
            content?.uid?.let {
                val recipient = Recipient.from(AMELogin.majorContext, it, true)
                recipient.addListener(this)
                chat_pin_recipient_photo.setPhoto(recipient)
            }

        }
    }

    private fun setNewShareChannel() {
        chat_pin_content.text = resources.getString(R.string.chats_pin_channel)
    }

    private fun setHistory() {
        chat_pin_content.text = resources.getString(R.string.chats_pin_history)
    }

    private fun setReply() {
        val message = messageDetailRecord?.message?.content as? AmeGroupMessage.ReplyContent
        chat_pin_content.text = message?.text
    }

    private fun setGroupShareCard() {
        chat_pin_content.text = resources.getString(R.string.common_group_share_message_description)
    }

    override fun onModified(recipient: Recipient) {
        if (recipient == messageDetailRecord?.getSender(recipient.address.context())) {
            chat_pin_recipient_photo.setPhoto(recipient)
        }
    }



    private fun buildThumbnailRequest(glideRequests: GlideRequests, loadObj: Any?, contentType: String) {
        if (!isLocalFile(loadObj)) {
            buildThumbnail(mPlaceHolderResource)
        }

        val placeholder = chats_pin_image.drawable
                ?: chats_pin_image.context.getDrawable(mPlaceHolderResource)

        ALog.d(TAG, "buildThumbnailRequest loadObj: $loadObj")
        when {
            MediaUtil.isGif(contentType) -> glideRequests.asGif().load(loadObj)
                    .override(mMaxWidth, mMaxHeight)
                    .centerCrop()
                    .apply(RequestOptions.bitmapTransform(CropRoundCornerTransform(mImageRadius, 0, CropRoundCornerTransform.CornerType.ALL)))
                    .placeholder(placeholder)
                    .error(mErrorImageResource)
                    .listener(object : RequestListener<GifDrawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any, target: Target<GifDrawable>, isFirstResource: Boolean): Boolean {
                            ALog.d(TAG, "onLoadFailed")
                            return false
                        }

                        override fun onResourceReady(resource: GifDrawable, model: Any, target: Target<GifDrawable>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                            //ALog.d(TAG, "onResourceReady")
                            return false
                        }
                    })
                    .into(chats_pin_image)
            else -> glideRequests.load(loadObj)
                    .override(mMaxWidth, mMaxHeight)
                    .centerCrop()
                    .apply(RequestOptions.bitmapTransform(CropRoundCornerTransform(mImageRadius, 0, CropRoundCornerTransform.CornerType.ALL)))
                    .placeholder(placeholder)
                    .error(mErrorImageResource)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                            ALog.d(TAG, "onLoadFailed")
                            return false
                        }

                        override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                            //ALog.d(TAG, "onResourceReady")
                            return false
                        }
                    })
                    .into(chats_pin_image)
        }
    }


    private fun setImageResourceInner(glideRequests: GlideRequests,
                                      messageDetailRecord: AmeGroupMessageDetail) {

        val content = messageDetailRecord.message.content as AmeGroupMessage.ThumbnailContent
        val uri = messageDetailRecord.toAttachmentUri()
        if (uri != null) {
            buildThumbnailRequest(glideRequests, uri, content.mimeType)
        } else {
            if (content.thumbnail_url.isEmpty()) {
                glideRequests.clear(chats_pin_image)
                setImageTag(chats_pin_image, null)
            } else {
                buildThumbnailRequest(glideRequests, "${content.thumbnail_url}?ips_thumbnail/3/w/$mMaxWidth/h/$mMaxHeight", content.mimeType)
            }
        }
    }

    private fun isLocalFile(uri: Any?): Boolean {
        if (null != uri) {
            when (uri) {
                is Uri -> return !uri.scheme.startsWith("http")
                is DecryptableStreamUriLoader.DecryptableUri -> return !(uri.uri.scheme.startsWith("http"))
                is String -> return !uri.startsWith("http")
            }
        }
        return false
    }


    private fun buildThumbnail(loadRes: Int) {
        ALog.d(TAG, "buildThumbnail")
        chats_pin_image.setImageResource(loadRes)
    }

    private fun setImageTag(view: ImageView, newTag: Any?): Boolean {
        val tag = view.getTag(R.id.chats_image_tag)
        if (null != tag) {
            if (Objects.equals(tag, newTag)) {
                return false
            }
        }

        view.setTag(R.id.chats_image_tag, newTag)
        return true
    }

}