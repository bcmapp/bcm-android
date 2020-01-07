package com.bcm.messenger.chats.group.viewholder

import android.net.Uri
import android.view.View
import com.bcm.messenger.chats.components.ChatDocumentView
import com.bcm.messenger.chats.group.logic.GroupMessageLogic
import com.bcm.messenger.chats.group.logic.MessageFileHandler
import com.bcm.messenger.chats.util.ChatComponentListener
import com.bcm.messenger.chats.util.ChatPreviewClickListener
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.mms.GlideRequests

/**
 *
 * Created by wjh on 2018/10/23
 */
class ChatDocumentHolderAction() : BaseChatHolderAction<ChatDocumentView>() {


    private var mPreviewClickListener = ChatPreviewClickListener()
    private var mDownloadClickListener = AttachmentDownloadClickListener()

    override fun bindData(accountContext: AccountContext, message: AmeGroupMessageDetail, body: ChatDocumentView, glideRequests: GlideRequests, batchSelected: Set<AmeGroupMessageDetail>?) {
        body.setDocumentClickListener(mPreviewClickListener)
        body.setDownloadClickListener(mDownloadClickListener)
        body.setDocument(message)
    }

    override fun resend(accountContext: AccountContext, messageRecord: AmeGroupMessageDetail) {
        if (!messageRecord.attachmentUri.isNullOrEmpty()) {
            GroupMessageLogic.get(accountContext).messageSender.resendMediaMessage(messageRecord)
        }
    }

    private inner class AttachmentDownloadClickListener() : ChatComponentListener {

        override fun onClick(v: View, data: Any) {
            if (data is AmeGroupMessageDetail) {
                val content = data.message.content as AmeGroupMessage.AttachmentContent
                if (content.isExist()) {
                    updateDocumentShow(v, data)
                } else {
                    MessageFileHandler.downloadAttachment(data, object : MessageFileHandler.MessageFileCallback {
                        override fun onResult(success: Boolean, uri: Uri?) {
                            updateDocumentShow(v, data)

                        }
                    })
                    updateDocumentShow(v, data)
                }
            }
        }

        private fun updateDocumentShow(view: View, messageRecord: AmeGroupMessageDetail) {
            if (mBaseView == view) {
                mBaseView?.setDocument(messageRecord)
            }
        }
    }

}