package com.bcm.messenger.chats.group.viewholder

import android.view.View
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.components.ChatThumbnailView
import com.bcm.messenger.chats.group.logic.GroupMessageLogic
import com.bcm.messenger.chats.group.logic.MessageFileHandler
import com.bcm.messenger.chats.util.ChatComponentListener
import com.bcm.messenger.chats.util.ChatPreviewClickListener
import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.mms.GlideRequests
import com.bcm.messenger.utility.logger.ALog

/**
 *
 * Created by wjh on 2018/10/23
 */
class ChatVideoHolderAction(accountContext: AccountContext) : BaseChatHolderAction<ChatThumbnailView>(accountContext) {

    private var mPreviewClickListener = ChatPreviewClickListener(accountContext)
    private var mDownloadClickListener = AttachmentDownloadClickListener()

    override fun bindData(message: AmeGroupMessageDetail, body: ChatThumbnailView, glideRequests: GlideRequests, batchSelected: Set<AmeGroupMessageDetail>?) {
        ALog.d("ChatVideoHolderAction", "bind")
        body.setThumbnailClickListener(mPreviewClickListener)
        body.setDownloadClickListener(mDownloadClickListener)
        body.setThumbnailAppearance(R.drawable.common_video_place_img, R.drawable.common_video_broken_img, body.resources.getDimensionPixelSize(R.dimen.chats_conversation_item_radius))
        // MARK: Temporary set mastersecret
        body.setImage(accountContext.masterSecret ?: return, glideRequests, message)
    }

    override fun resend(message: AmeGroupMessageDetail) {
        if (!message.attachmentUri.isNullOrEmpty()) {
            GroupMessageLogic.get(accountContext).messageSender.resendMediaMessage(message)
        }
    }

    private inner class AttachmentDownloadClickListener : ChatComponentListener {

        override fun onClick(v: View, data: Any) {
            if (data is AmeGroupMessageDetail) {
                val content = data.message.content as AmeGroupMessage.AttachmentContent
                if (!content.isExist()) {
                    MessageFileHandler.downloadAttachment(accountContext, data, null)
                }
            }
        }
    }

}