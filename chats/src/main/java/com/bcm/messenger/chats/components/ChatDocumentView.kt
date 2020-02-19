package com.bcm.messenger.chats.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.AttrRes
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.util.ChatComponentListener
import com.bcm.messenger.chats.util.GroupAttachmentProgressEvent
import com.bcm.messenger.chats.util.HistoryGroupAttachmentProgressEvent
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.database.model.AttachmentDbModel
import com.bcm.messenger.common.database.records.AttachmentRecord
import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.event.PartProgressEvent
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.grouprepository.model.AmeHistoryMessageDetail
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.getAttrColor
import com.bcm.messenger.utility.ViewUtils
import kotlinx.android.synthetic.main.chats_document_view_new.view.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * chat document view
 */
class ChatDocumentView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, @AttrRes defStyleAttr: Int = 0) :
        androidx.constraintlayout.widget.ConstraintLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "ChatDocumentView"
        private const val TRANSITION_MS = 300
        private const val MIN_PROGRESS = 5

        private const val STATE_COMPLETE = 0
        private const val STATE_PROGRESS = 1
        private const val STATE_DOWNLOAD = 2

    }

    private var downloadListener: ChatComponentListener? = null
    private var viewListener: ChatComponentListener? = null

    private var documentSlide: AttachmentRecord? = null

    private var mPrivateMessage: MessageRecord? = null
    private var mGroupMessage: AmeGroupMessageDetail? = null

    private var mShowPending = false

    init {
        View.inflate(context, R.layout.chats_document_view_new, this)
        val v = resources.getDimensionPixelSize(R.dimen.common_vertical_gap)
        val h = resources.getDimensionPixelSize(R.dimen.common_horizontal_gap)
        setPadding(h, v, h, v)

        super.setOnClickListener(OpenClickedListener())
        document_download.setOnClickListener(DownloadClickedListener())
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

    fun setDownloadClickListener(listener: ChatComponentListener?) {
        this.downloadListener = listener
    }

    fun setDocumentClickListener(listener: ChatComponentListener?) {
        this.viewListener = listener
    }


    fun setDocument(messageRecord: MessageRecord) {
        val showControls = !messageRecord.isFailed() && (!messageRecord.isOutgoing() || messageRecord.isPending())
        val slide = messageRecord.getDocumentAttachment() ?: return
        if (showControls && slide.isPendingDownload()) {
            displayControl(STATE_DOWNLOAD)

        } else if (showControls && slide.transferState == AttachmentDbModel.TransferState.STARTED.state) {
            displayControl(STATE_PROGRESS)
        } else {
            displayControl(STATE_COMPLETE)
        }

        this.mPrivateMessage = messageRecord
        this.documentSlide = slide
        this.mGroupMessage = null

        document_name.text = slide.fileName ?: context.getString(R.string.chats_unknown_file_name)
        if (slide.dataSize <= 0) {
            document_size.visibility = View.GONE
        } else {
            document_size.visibility = View.VISIBLE
            document_size.text = BcmFileUtils.formatSize(context, slide.dataSize)
        }

        document_icon.text = AmeGroupMessage.FileContent.getTypeName(slide.fileName.orEmpty(), slide.contentType)
        document_icon.setTextColor(AmeGroupMessage.FileContent.getTypeColor(document_icon.text.toString()))


        setDocumentAppearance(messageRecord.isOutgoing())
    }


    fun setDocument(accountContext: AccountContext, messageRecord: AmeGroupMessageDetail) {
        mGroupMessage = messageRecord
        mPrivateMessage = null
        documentSlide = null


        if (messageRecord.isAttachmentDownloading || messageRecord.isSending) {

            displayControl(STATE_PROGRESS)

        } else {
            var isComplete = messageRecord.isAttachmentComplete
            if (!isComplete && messageRecord is AmeHistoryMessageDetail) {
                val content = messageRecord.message.content
                if (content is AmeGroupMessage.AttachmentContent && content.isExist(accountContext)) {
                    isComplete = true
                }
            }
            if (isComplete) {
                displayControl(STATE_COMPLETE)
            } else {
                displayControl(STATE_DOWNLOAD)
            }
        }

        val content = messageRecord.message.content as AmeGroupMessage.FileContent
        document_name.text = content.fileName ?: context.getString(R.string.chats_unknown_file_name)
        if (content.size <= 0) {
            document_size.visibility = View.GONE
        } else {
            document_size.visibility = View.VISIBLE
            document_size.text = BcmFileUtils.formatSize(context, content.size)
        }

        document_icon.text = AmeGroupMessage.FileContent.getTypeName(document_name.text.toString(), content.mimeType)
        document_icon.setTextColor(AmeGroupMessage.FileContent.getTypeColor(document_icon.text.toString()))

        setDocumentAppearance(messageRecord.isSendByMe)
    }


    private fun setDocumentAppearance(isOutGoing: Boolean) {
        if (isOutGoing) {
            document_icon.setBackgroundResource(R.drawable.chats_message_file_icon)
            document_size.setTextColor(context.getAttrColor(R.attr.common_text_white_color))
            document_name.setTextColor(context.getAttrColor(R.attr.common_text_white_color))
        } else {
            document_icon.setBackgroundResource(R.drawable.chats_message_file_icon_grey)
            document_size.setTextColor(context.getAttrColor(R.attr.common_text_main_color))
            document_name.setTextColor(context.getAttrColor(R.attr.common_text_main_color))
        }
    }


    private fun displayControl(state: Int) {
        when (state) {
            STATE_COMPLETE -> {
                mShowPending = false
                ViewUtils.fadeOut(document_progress, TRANSITION_MS)
                ViewUtils.fadeOut(document_download, TRANSITION_MS)
                ViewUtils.fadeIn(document_icon, TRANSITION_MS)
            }
            STATE_PROGRESS -> {
                mShowPending = true
                ViewUtils.fadeIn(document_progress, TRANSITION_MS)
                ViewUtils.fadeIn(document_icon, TRANSITION_MS)
                ViewUtils.fadeOut(document_download, TRANSITION_MS)
            }
            STATE_DOWNLOAD -> {
                mShowPending = false
                ViewUtils.fadeIn(document_download, TRANSITION_MS)
                ViewUtils.fadeOut(document_progress, TRANSITION_MS)
                document_icon.visibility = View.INVISIBLE
            }
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventAsync(event: PartProgressEvent) {
        if (documentSlide != null && event.attachment == this.documentSlide) {

            if (event.progress >= event.total) {
                displayControl(STATE_COMPLETE)
            } else {
                displayControl(STATE_PROGRESS)
                var p = (event.progress * 100.0f / event.total).toInt()
                if (p < MIN_PROGRESS) {
                    p = MIN_PROGRESS
                }
                document_progress.progress = p
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventAsync(event: GroupAttachmentProgressEvent) {

        fun updateProgress(groupMessage: AmeGroupMessageDetail, progress: Float) {
            if (progress >= 1.0f) {
                displayControl(STATE_COMPLETE)

            } else {
                displayControl(STATE_PROGRESS)
                var p = (progress * 100.0f).toInt()
                if (p < MIN_PROGRESS) {
                    p = MIN_PROGRESS
                }
                document_progress.progress = p
            }
        }

        mGroupMessage?.let {

            if (it is AmeHistoryMessageDetail) {

                val ac = it.message.content as AmeGroupMessage.AttachmentContent
                if (event is HistoryGroupAttachmentProgressEvent && event.action == GroupAttachmentProgressEvent.ACTION_ATTACHMENT_DOWNLOADING &&
                        (ac.url == event.url)) {
                    updateProgress(it, event.progress)
                }

            } else {

                if (event.gid == it.gid && event.indexId == it.indexId &&
                        (event.action == GroupAttachmentProgressEvent.ACTION_ATTACHMENT_DOWNLOADING || event.action == GroupAttachmentProgressEvent.ACTION_ATTACHMENT_UPLOADING)) {

                    updateProgress(it, event.progress)
                }
            }
        }

    }

    private inner class DownloadClickedListener : OnClickListener {

        override fun onClick(v: View) {
            if (downloadListener != null) {
                val data = mPrivateMessage ?: mGroupMessage
                if (data != null && !mShowPending) {
                    downloadListener?.onClick(v, data)
                }

            }
        }
    }

    private inner class OpenClickedListener : OnClickListener {

        override fun onClick(v: View) {

            if (viewListener != null) {
                val data = mPrivateMessage ?: mGroupMessage
                if (data != null && !mShowPending) {
                    viewListener?.onClick(v, data)
                }
            }
        }
    }

}
