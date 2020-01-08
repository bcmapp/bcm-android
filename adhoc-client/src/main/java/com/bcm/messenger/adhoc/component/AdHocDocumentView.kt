package com.bcm.messenger.adhoc.component

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.AttrRes
import com.bcm.messenger.adhoc.R
import com.bcm.messenger.adhoc.logic.AdHocMessageDetail
import com.bcm.messenger.adhoc.logic.AdHocMessageLogic
import com.bcm.messenger.adhoc.logic.AdHocMessageModel
import com.bcm.messenger.chats.util.ChatComponentListener
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.utils.AppUtil
import com.bcm.messenger.common.utils.BcmFileUtils
import kotlinx.android.synthetic.main.adhoc_document_view.view.*

/**
 * adhoc document component
 */
class AdHocDocumentView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, @AttrRes defStyleAttr: Int = 0) :
        androidx.constraintlayout.widget.ConstraintLayout(context, attrs, defStyleAttr) {

    companion object {
        private val TAG = "AdHocDocumentView"
    }

    private var mAdHocMessage: AdHocMessageDetail? = null

    init {
        View.inflate(context, R.layout.adhoc_document_view, this)
        val v = resources.getDimensionPixelSize(R.dimen.common_vertical_gap)
        val h = resources.getDimensionPixelSize(R.dimen.common_horizontal_gap)
        setPadding(h, v, h, v)
    }

    private fun updateProgress(progress: Float) {
        if (progress > 0 && progress < 1.0f) {
            document_progress.visibility = View.VISIBLE
            document_progress.progress = (progress * 100).toInt()
        }else {
            document_progress.visibility = View.GONE
        }
    }

    fun setDocumentClickListener(listener: ChatComponentListener?) {
        setOnClickListener {v ->
            if (!v.isClickable) {
                return@setOnClickListener
            }
            mAdHocMessage?.let {
                listener?.onClick(v, it)
            }
        }
    }

    fun setDocument(accountContext: AccountContext, messageRecord: AdHocMessageDetail) {
        mAdHocMessage = messageRecord

        AdHocMessageLogic.get(accountContext).getModel()?.addOnMessageListener(object : AdHocMessageModel.DefaultOnMessageListener() {
            override fun onProgress(message: AdHocMessageDetail, progress: Float) {
                if (mAdHocMessage == message) {
                    mAdHocMessage?.attachmentProgress = progress
                    updateProgress(progress)
                }
            }
        })

        if (messageRecord.toAttachmentUri() != null) {
            document_download?.visibility = View.GONE
            document_progress?.visibility = View.GONE
            this.isClickable = true
        } else {
            document_download?.visibility = View.GONE
            document_progress?.visibility = View.GONE
            this.isClickable = false
        }

        val content = messageRecord.getMessageBody()?.content as AmeGroupMessage.FileContent
        document_name.text = content.fileName ?: context.getString(R.string.chats_unknown_file_name)
        if (content.size <= 0) {
            document_size.visibility = View.GONE
        } else {
            document_size.visibility = View.VISIBLE
            document_size.text = BcmFileUtils.formatSize(context, content.size)
        }

        document_icon.text = AmeGroupMessage.FileContent.getTypeName(document_name.text.toString(), content.mimeType)
        document_icon.setTextColor(AmeGroupMessage.FileContent.getTypeColor(document_icon.text.toString()))

        setDocumentAppearance(messageRecord.sendByMe)
        updateProgress(messageRecord.attachmentProgress)

    }

    private fun setDocumentAppearance(isOutGoing: Boolean) {
        if (isOutGoing) {
            document_icon.setBackgroundResource(R.drawable.chats_message_file_icon)
            document_size.setTextColor(AppUtil.getColor(resources, R.color.common_color_white))
            document_name.setTextColor(AppUtil.getColor(resources, R.color.common_color_white))
            setBackgroundResource(R.drawable.chats_conversation_send_bubble_bg)
        } else {
            document_icon.setBackgroundResource(R.drawable.chats_message_file_icon_grey)
            document_size.setTextColor(AppUtil.getColor(resources, R.color.common_color_black))
            document_name.setTextColor(AppUtil.getColor(resources, R.color.common_color_black))
            setBackgroundResource(R.drawable.chats_conversation_received_bubble_bg)

        }
    }

}
