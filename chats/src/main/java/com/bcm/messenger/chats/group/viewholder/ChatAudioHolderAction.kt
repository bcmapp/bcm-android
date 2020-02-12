package com.bcm.messenger.chats.group.viewholder

import android.net.Uri
import android.view.View
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.components.ChatAudioView
import com.bcm.messenger.chats.group.logic.GroupMessageLogic
import com.bcm.messenger.chats.group.logic.MessageFileHandler
import com.bcm.messenger.chats.util.ChatComponentListener
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.common.utils.getAttrColor

/**
 * Created by wjh on 2018/10/23
 */
class ChatAudioHolderAction(accountContext: AccountContext) : BaseChatHolderAction<ChatAudioView>(accountContext) {

    private var mDownloadClickListener = AttachmentDownloadClickListener()

    override fun bindData(messageRecord: AmeGroupMessageDetail, bodyView: ChatAudioView, glideRequests: GlideRequests, batchSelected: Set<AmeGroupMessageDetail>?) {

        bodyView.setDownloadClickListener(mDownloadClickListener)

        val content = messageRecord.message?.content as? AmeGroupMessage.AudioContent ?: return
        val context = bodyView.context
        bodyView.setAudio(accountContext.masterSecret ?: return, messageRecord)
        if (messageRecord.isSendByMe) {
            bodyView.setProgressDrawableResource(R.drawable.chats_audio_send_top_progress_bg)

            bodyView.setAudioAppearance(R.drawable.chats_conversation_item_play_icon, R.drawable.chats_conversation_item_pause_icon,
                    context.getAttrColor(R.attr.common_white_color),
                    context.getAttrColor(R.attr.common_white_color),
                    context.getAttrColor(R.attr.common_text_white_color))
        } else {
            bodyView.setProgressDrawableResource(R.drawable.chats_audio_receive_top_progress_bg)
            bodyView.setAudioAppearance(R.drawable.chats_conversation_item_play_icon, R.drawable.chats_conversation_item_pause_icon,
                    context.getAttrColor(R.attr.chats_conversation_income_icon_color),
                    context.getAttrColor(R.attr.chats_conversation_income_text_color),
                    context.getAttrColor(R.attr.chats_conversation_income_text_color))
        }

        if (messageRecord.attachmentUri.isNullOrEmpty() || !content.isExist(accountContext)) {
            bodyView.doDownloadAction()
        }
    }

    override fun unBind() {
        mBaseView?.cleanup()
    }

    override fun resend(messageRecord: AmeGroupMessageDetail) {
        if (!messageRecord.attachmentUri.isNullOrEmpty()) {
            GroupMessageLogic.get(accountContext).messageSender.resendMediaMessage(messageRecord)
        }
    }

    private inner class AttachmentDownloadClickListener : ChatComponentListener {

        override fun onClick(v: View, data: Any) {

            if (data is AmeGroupMessageDetail) {
                val content = data.message.content as AmeGroupMessage.AudioContent
                if (content.isExist(accountContext)) {
                    updateAudioMessage(v, data)
                } else {

                    MessageFileHandler.downloadAttachment(accountContext, data, object : MessageFileHandler.MessageFileCallback {
                        override fun onResult(success: Boolean, uri: Uri?) {
                            if (mMessageDetail == data) {
                                updateAudioMessage(v, data)
                            }
                        }
                    })
                    updateAudioMessage(v, data)
                }
            }
        }

        private fun updateAudioMessage(v: View, messageRecord: AmeGroupMessageDetail) {

            if (mBaseView == v) {
                mBaseView?.setAudio(accountContext.masterSecret ?: return, messageRecord)
            }
        }
    }
}